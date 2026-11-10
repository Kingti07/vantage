package dev.vantage.launcher.game;

import dev.vantage.common.util.CancelledException;
import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.ProgressListener;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Downloads many files in parallel with SHA-1 verification and aggregated progress (bytes, speed, remaining
 * time). Files that already exist with the expected size (and, in {@code verifyHashes} mode, hash) are skipped,
 * and every target is downloaded at most once. The first failure or a cancellation stops the remaining downloads;
 * their partial files are kept so the next attempt continues where this one stopped.
 */
public final class DownloadQueue {
	public record Job(URI uri, Path target, String sha1, long size) {
	}

	private final Map<Path, Job> jobs = new LinkedHashMap<>();
	private final boolean verifyHashes;

	public DownloadQueue(boolean verifyHashes) {
		this.verifyHashes = verifyHashes;
	}

	/** Adds a download; a second job for the same target file is ignored. */
	public void add(String url, Path target, String sha1, long size) {
		jobs.putIfAbsent(target.toAbsolutePath().normalize(), new Job(URI.create(url), target, sha1, size));
	}

	public int size() {
		return jobs.size();
	}

	/**
	 * Runs all jobs. Progress is reported in [progressStart, progressEnd] of the overall operation.
	 *
	 * @throws CancelledException if the listener reports {@link ProgressListener#isCancelled()}
	 */
	public void run(ProgressListener listener, String label, double progressStart, double progressEnd, int threads) throws IOException {
		listener.checkCancelled();
		List<Job> pending = new ArrayList<>();
		long totalBytes = 0;
		for (Job job : jobs.values()) {
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
		TransferMeter meter = new TransferMeter(listener, total);
		AtomicBoolean failed = new AtomicBoolean();
		BooleanSupplier stop = () -> failed.get() || listener.isCancelled();
		ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(threads, pending.size())), r -> {
			Thread thread = new Thread(r, "vantage-download");
			thread.setDaemon(true);
			return thread;
		});
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Job job : pending) {
				futures.add(executor.submit(() -> {
					try {
						Http.download(job.uri(), job.target(), job.sha1() != null ? "SHA-1" : null, job.sha1(), bytes -> {
							long now = done.addAndGet(bytes);
							listener.progress(progressStart + (progressEnd - progressStart) * Math.clamp(now / (double) total, 0.0, 1.0));
							meter.update(now);
						}, stop);
					} catch (IOException | RuntimeException e) {
						if (!(e instanceof CancelledException)) {
							failed.set(true);
						}
						throw e;
					}
					return null;
				}));
			}
			Throwable first = null;
			int failures = 0;
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (ExecutionException e) {
					if (e.getCause() instanceof CancelledException) {
						continue;
					}
					failures++;
					if (first == null) {
						first = e.getCause();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new CancelledException("Download interrupted");
				}
			}
			if (first != null) {
				String prefix = failures == 1 ? "A download failed: " : failures + " downloads failed. First error: ";
				throw first instanceof IOException io && failures == 1 ? io : new IOException(prefix + first.getMessage(), first);
			}
			listener.checkCancelled();
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

	/** Turns a growing byte count into a smoothed transfer rate, reported at most four times per second. */
	static final class TransferMeter {
		private static final long INTERVAL_NANOS = 250_000_000L;
		private static final double SMOOTHING = 0.3;

		private final ProgressListener listener;
		private final long total;
		private long lastTime = System.nanoTime();
		private long lastBytes;
		private double rate = -1;

		TransferMeter(ProgressListener listener, long total) {
			this.listener = listener;
			this.total = total;
		}

		synchronized void update(long bytes) {
			long now = System.nanoTime();
			long elapsed = now - lastTime;
			if (elapsed < INTERVAL_NANOS) {
				return;
			}
			double sample = Math.max(0, bytes - lastBytes) * 1e9 / elapsed;
			rate = rate < 0 ? sample : rate + SMOOTHING * (sample - rate);
			lastTime = now;
			lastBytes = bytes;
			listener.transfer(bytes, total, rate);
		}
	}
}
