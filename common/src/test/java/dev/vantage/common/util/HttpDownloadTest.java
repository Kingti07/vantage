package dev.vantage.common.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Download behaviour against a local HTTP server: resume, hash checks, stalled connections, cancel, offline. */
class HttpDownloadTest {
	private static final byte[] CONTENT = new byte[300_000];

	static {
		for (int i = 0; i < CONTENT.length; i++) {
			CONTENT[i] = (byte) (i * 31 + 7);
		}
	}

	@TempDir
	Path dir;
	private HttpServer server;
	private final AtomicInteger requests = new AtomicInteger();
	private volatile String lastRange;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/file", this::serveWithRanges);
		server.createContext("/stall", exchange -> {
			requests.incrementAndGet();
			exchange.sendResponseHeaders(200, CONTENT.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(CONTENT, 0, 1000);
				out.flush();
				Thread.sleep(3_000);
			} catch (InterruptedException | IOException ignored) {
				// client gave up
			}
		});
		server.createContext("/missing", exchange -> {
			requests.incrementAndGet();
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
		Http.readTimeout = Http.READ_TIMEOUT;
	}

	private void serveWithRanges(HttpExchange exchange) throws IOException {
		requests.incrementAndGet();
		String range = exchange.getRequestHeaders().getFirst("Range");
		lastRange = range;
		int start = 0;
		if (range != null && range.startsWith("bytes=")) {
			start = Integer.parseInt(range.substring(6, range.indexOf('-')));
			exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + (CONTENT.length - 1) + "/" + CONTENT.length);
			exchange.sendResponseHeaders(206, CONTENT.length - start);
		} else {
			exchange.sendResponseHeaders(200, CONTENT.length);
		}
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(CONTENT, start, CONTENT.length - start);
		}
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	private static String sha1(byte[] data) {
		return java.util.HexFormat.of().formatHex(HashUtil.digest("SHA-1").digest(data));
	}

	@Test
	void downloadsAndVerifies() throws IOException {
		Path target = dir.resolve("a/b/file.bin");
		AtomicLong bytes = new AtomicLong();
		Http.download(uri("/file"), target, "SHA-1", sha1(CONTENT), bytes::addAndGet);
		assertArrayEquals(CONTENT, Files.readAllBytes(target));
		assertEquals(CONTENT.length, bytes.get());
		assertFalse(Files.exists(Http.partFile(target)));
	}

	@Test
	void resumesFromPartialFile() throws IOException {
		Path target = dir.resolve("file.bin");
		Files.write(Http.partFile(target), Arrays.copyOf(CONTENT, 120_000));
		AtomicLong bytes = new AtomicLong();

		Http.download(uri("/file"), target, "SHA-1", sha1(CONTENT), bytes::addAndGet);

		assertEquals("bytes=120000-", lastRange);
		assertArrayEquals(CONTENT, Files.readAllBytes(target));
		assertEquals(CONTENT.length, bytes.get(), "bytes already on disk count as progress");
	}

	@Test
	void hashMismatchLeavesNoFile() {
		Path target = dir.resolve("file.bin");
		IOException error = assertThrows(IOException.class,
			() -> Http.download(uri("/file"), target, "SHA-1", "0000000000000000000000000000000000000000", null));
		assertTrue(error.getMessage().contains("Hash mismatch"));
		assertFalse(Files.exists(target));
		assertFalse(Files.exists(Http.partFile(target)));
		assertEquals(Http.MAX_ATTEMPTS, requests.get());
	}

	@Test
	void stalledConnectionTimesOutAndKeepsPartialData() {
		Http.readTimeout = Duration.ofMillis(300);
		Path target = dir.resolve("file.bin");
		long start = System.nanoTime();
		assertThrows(IOException.class, () -> Http.download(uri("/stall"), target, null, null, null));
		long seconds = (System.nanoTime() - start) / 1_000_000_000L;
		assertTrue(seconds < 15, "a stalled download must not hang (took " + seconds + " s)");
		assertFalse(Files.exists(target));
		assertEquals(Http.MAX_ATTEMPTS, requests.get());
	}

	@Test
	void clientErrorsAreNotRetried() {
		HttpException error = assertThrows(HttpException.class,
			() -> Http.download(uri("/missing"), dir.resolve("x"), null, null, null));
		assertEquals(404, error.status());
		assertEquals(1, requests.get());
	}

	@Test
	void cancelKeepsPartialFileForResume() {
		Path target = dir.resolve("file.bin");
		AtomicLong bytes = new AtomicLong();
		assertThrows(CancelledException.class,
			() -> Http.download(uri("/file"), target, null, null, bytes::addAndGet, () -> bytes.get() > 0));
		assertFalse(Files.exists(target));
		assertTrue(Files.exists(Http.partFile(target)));
	}

	@Test
	void unreachableHostFailsWithoutRetries() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			closedPort = socket.getLocalPort();
		}
		long start = System.nanoTime();
		IOException error = assertThrows(IOException.class,
			() -> Http.download(URI.create("http://127.0.0.1:" + closedPort + "/file"), dir.resolve("x"), null, null, null));
		assertTrue(Http.isNetworkUnavailable(error));
		assertTrue((System.nanoTime() - start) / 1_000_000L < 700, "no retry delay when the host is unreachable");
	}
}
