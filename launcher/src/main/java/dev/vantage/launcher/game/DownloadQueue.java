package dev.vantage.launcher.game;

import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.ProgressListener;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads many files in parallel with SHA-1 verification and aggregated progress.
 * Files that already exist with the expected size (and, in {@code verifyHashes} mode, hash) are skipped.
 */
public final class DownloadQueue {
	public record Job(URI uri, Path target, String sha1, long size) {
	}

	private final List<Job> jobs = new ArrayList<>();
	private final boolean verifyHashes;

	public DownloadQueue(boolean verifyHashes) {
		this.verifyHashes = verifyHashes;
	}

	public void add(String url, Path target, String sha1, long size) {
		jobs.add(new Job(URI.create(url), target, sha1, size));
	}

	public int size() {
		return jobs.size();
	}

	/**
	 * Runs all jobs. Progress is reported in [progressStart, progressEnd] of the overall operation.
	 */
	public void run(ProgressListener listener, String label, double progressStart, double progressEnd, int threads) throws IOException {
		List<Job> pending = new ArrayList<>();
		long totalBytes = 0;
		for (Job job : jobs) {
			if (!isUpToDate(job)) {
				pending.add(job);
				totalBytes += Math.max(job.size(), 1);
			}
		}
		if (pending.isEmpty()) {
			listener.progress(progressEnd);
			return;
		}
		listener.status(label + " (" + pending.size() + " file" + (pending.size() == 1 ? "" : "s") + ")");
		long total = Math.max(totalBytes, 1);
		AtomicLong done = new AtomicLong();
		ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, threads), r -> {
			Thread thread = new Thread(r, "vantage-download");
			thread.setDaemon(true);
			return thread;
		});
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Job job : pending) {
				futures.add(executor.submit(() -> {
					long before = done.get();
					Http.download(job.uri(), job.target(), job.sha1() != null ? "SHA-1" : null, job.sha1(), bytes -> {
						long now = done.addAndGet(bytes);
						listener.progress(progressStart + (progressEnd - progressStart) * Math.min(1.0, now / (double) total));
					});
					if (job.size() <= 0) {
						done.set(Math.max(done.get(), before + 1));
					}
					return null;
				}));
			}
			IOException first = null;
			int failures = 0;
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (ExecutionException e) {
					failures++;
					if (first == null) {
						first = e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Download interrupted", e);
				}
			}
			if (first != null) {
				throw new IOException(failures + " download(s) failed. First error: " + first.getMessage(), first);
			}
		} finally {
			executor.shutdownNow();
		}
		listener.progress(progressEnd);
	}

	private boolean isUpToDate(Job job) {
		try {
			if (!Files.isRegularFile(job.target())) {
				return false;
			}
			if (job.size() > 0 && Files.size(job.target()) != job.size()) {
				return false;
			}
			if (verifyHashes && job.sha1() != null) {
				return HashUtil.sha1(job.target()).equalsIgnoreCase(job.sha1());
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
