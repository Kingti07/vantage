package dev.vantage.launcher.game;

import com.sun.net.httpserver.HttpServer;
import dev.vantage.common.util.CancelledException;
import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.ProgressListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The download queue against a local HTTP server: skipping, de-duplication, repair, failures and cancel. */
class DownloadQueueTest {
	private static final byte[] DATA = "library content".getBytes(StandardCharsets.UTF_8);
	private static final String SHA1 = HexFormat.of().formatHex(HashUtil.digest("SHA-1").digest(DATA));

	@TempDir
	Path dir;
	private HttpServer server;
	private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			requests.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
			if (path.startsWith("/missing")) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			exchange.sendResponseHeaders(200, DATA.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(DATA);
			}
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private String url(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	private int count(String path) {
		AtomicInteger counter = requests.get(path);
		return counter == null ? 0 : counter.get();
	}

	@Test
	void downloadsEachTargetOnceAndSkipsUpToDateFiles() throws IOException {
		Path target = dir.resolve("libraries/a.jar");
		DownloadQueue queue = new DownloadQueue(false);
		queue.add(url("/a"), target, SHA1, DATA.length);
		queue.add(url("/a-mirror"), target, SHA1, DATA.length);
		assertEquals(1, queue.size(), "the same target is only queued once");
		queue.run(ProgressListener.NONE, "Downloading", 0, 1, 4);
		assertEquals(1, count("/a"));
		assertEquals(0, count("/a-mirror"));

		DownloadQueue again = new DownloadQueue(false);
		again.add(url("/a"), target, SHA1, DATA.length);
		again.run(ProgressListener.NONE, "Downloading", 0, 1, 4);
		assertEquals(1, count("/a"), "an existing file with the right size is not downloaded again");
	}

	@Test
	void repairModeReplacesCorruptFiles() throws IOException {
		Path target = dir.resolve("b.jar");
		Files.write(target, "corrupt content".getBytes(StandardCharsets.UTF_8));
		assertEquals(DATA.length, Files.size(target), "same size, so only a hash check can find it");

		DownloadQueue quick = new DownloadQueue(false);
		quick.add(url("/b"), target, SHA1, DATA.length);
		quick.run(ProgressListener.NONE, "Downloading", 0, 1, 2);
		assertEquals(0, count("/b"));

		DownloadQueue repair = new DownloadQueue(true);
		repair.add(url("/b"), target, SHA1, DATA.length);
		repair.run(ProgressListener.NONE, "Verifying", 0, 1, 2);
		assertEquals(1, count("/b"));
		assertEquals(SHA1, HashUtil.sha1(target));
	}

	@Test
	void reportsFailures() {
		DownloadQueue queue = new DownloadQueue(false);
		queue.add(url("/missing/x.jar"), dir.resolve("x.jar"), null, 10);
		IOException error = assertThrows(IOException.class, () -> queue.run(ProgressListener.NONE, "Downloading", 0, 1, 2));
		assertTrue(error.getMessage().contains("404"), error.getMessage());
	}

	@Test
	void cancelStopsBeforeDownloading() {
		ProgressListener cancelled = new ProgressListener() {
			@Override
			public void status(String message) {
			}

			@Override
			public void progress(double fraction) {
			}

			@Override
			public boolean isCancelled() {
				return true;
			}
		};
		DownloadQueue queue = new DownloadQueue(false);
		queue.add(url("/c"), dir.resolve("c.jar"), SHA1, DATA.length);
		assertThrows(CancelledException.class, () -> queue.run(cancelled, "Downloading", 0, 1, 2));
		assertEquals(0, count("/c"));
	}
}
