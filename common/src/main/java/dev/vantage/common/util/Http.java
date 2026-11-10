package dev.vantage.common.util;

import com.google.gson.Gson;
import dev.vantage.common.VantageInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * HTTP helpers shared by the launcher and the in-game client.
 * <ul>
 *   <li>API requests ({@link #getString}, {@link #postForm}, ...) use {@link HttpClient} with bounded retries.
 *       A missing network fails immediately instead of being retried.</li>
 *   <li>File downloads ({@link #download}) use {@link HttpURLConnection} because it supports a per-read timeout:
 *       a connection that stops delivering data fails after {@link #READ_TIMEOUT} instead of hanging forever.
 *       Downloads continue from a {@code .part} file after an interruption, verify a hash and can be cancelled.</li>
 * </ul>
 */
public final class Http {
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
	public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	/** Longest time a download may go without receiving a single byte. */
	public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
	public static final int MAX_ATTEMPTS = 3;
	private static final long RETRY_BASE_DELAY_MS = 750;
	private static final long MAX_RETRY_AFTER_MS = 10_000;
	private static final int BUFFER_SIZE = 64 * 1024;
	/** Read timeout used for downloads; only tests change it. */
	static volatile Duration readTimeout = READ_TIMEOUT;

	static {
		// Keep enough idle connections for parallel downloads (the JDK default of 5 forces extra TLS handshakes).
		if (System.getProperty("http.maxConnections") == null) {
			System.setProperty("http.maxConnections", "16");
		}
	}

	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(CONNECT_TIMEOUT)
		.build();

	/** Targets currently being downloaded; two downloads of the same file wait for each other. */
	private static final Map<Path, TargetLock> ACTIVE_TARGETS = new ConcurrentHashMap<>();

	private static final class TargetLock {
		final ReentrantLock lock = new ReentrantLock();
		int users;
	}

	private Http() {
	}

	public static HttpClient client() {
		return CLIENT;
	}

	public static HttpRequest.Builder request(URI uri) {
		return HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("User-Agent", VantageInfo.USER_AGENT);
	}

	public static String getString(URI uri) throws IOException {
		return send(request(uri).GET().build());
	}

	public static <T> T getJson(URI uri, Type type, Gson gson) throws IOException {
		return parse(getString(uri), type, gson, uri);
	}

	public static <T> T postJson(URI uri, Object body, Type type, Gson gson) throws IOException {
		HttpRequest request = request(uri)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
			.build();
		return parse(send(request), type, gson, uri);
	}

	public static String postForm(URI uri, Map<String, String> form) throws IOException {
		HttpRequest request = request(uri)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
			.build();
		return send(request);
	}

	public static String formEncode(Map<String, String> form) {
		StringJoiner joiner = new StringJoiner("&");
		form.forEach((key, value) -> joiner.add(encode(key) + "=" + encode(value)));
		return joiner.toString();
	}

	public static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/**
	 * Sends a request and returns the body of a 2xx response. Retries on dropped connections, timeouts, 429
	 * (honouring Retry-After / X-Ratelimit-Reset up to 10 s) and 5xx responses. If the network or host is not
	 * reachable at all the error is thrown right away, so offline start-ups do not wait for pointless retries.
	 */
	public static String send(HttpRequest request) throws IOException {
		IOException last = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				int status = response.statusCode();
				if (status >= 200 && status < 300) {
					return response.body();
				}
				HttpException error = new HttpException(request.uri().toString(), status, response.body());
				if (isRetryable(status)) {
					last = error;
					sleepBeforeRetry(response, attempt);
					continue;
				}
				throw error;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while requesting " + request.uri(), e);
			} catch (HttpException e) {
				throw e;
			} catch (IOException e) {
				if (isNetworkUnavailable(e)) {
					throw e;
				}
				last = e;
				sleepQuietly(RETRY_BASE_DELAY_MS * attempt);
			}
		}
		throw last != null ? last : new IOException("Request failed: " + request.uri());
	}

	/**
	 * True if the error means that the network or the host cannot be reached at all (no connection, DNS
	 * failure, connection refused, connect timeout) as opposed to a transfer that broke off.
	 */
	public static boolean isNetworkUnavailable(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof NoRouteToHostException
				|| t instanceof HttpConnectTimeoutException) {
				return true;
			}
			if (t instanceof SocketTimeoutException && t.getMessage() != null
				&& t.getMessage().toLowerCase(Locale.ROOT).contains("connect")) {
				return true;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return false;
	}

	static boolean isRetryable(int status) {
		return status == 408 || status == 429 || status >= 500;
	}

	/** Same as {@link #download(URI, Path, String, String, LongConsumer, BooleanSupplier)}; cancelled by interrupting the thread. */
	public static void download(URI uri, Path target, String hashAlgorithm, String expectedHash, LongConsumer bytesRead) throws IOException {
		download(uri, target, hashAlgorithm, expectedHash, bytesRead, () -> Thread.currentThread().isInterrupted());
	}

	/**
	 * Downloads {@code uri} to {@code target}, verifying the given hash when present.
	 * <p>
	 * Data is written to {@code <target>.part} and only moved to {@code target} once it is complete and verified,
	 * so an interrupted download never leaves a corrupt file behind. A later attempt (also after a restart of the
	 * program) continues from the {@code .part} file if the server supports range requests. Up to
	 * {@link #MAX_ATTEMPTS} attempts are made for broken transfers, timeouts and server errors.
	 *
	 * @param hashAlgorithm e.g. "SHA-1" or "SHA-512"; may be null when no hash is known
	 * @param bytesRead     receives the number of bytes added to the file; negative when a partial file had to be
	 *                      discarded. May be null.
	 * @param cancelled     polled while downloading; when it returns true a {@link CancelledException} is thrown
	 *                      and the {@code .part} file is kept for a later resume
	 */
	public static void download(URI uri, Path target, String hashAlgorithm, String expectedHash, LongConsumer bytesRead,
		BooleanSupplier cancelled) throws IOException {
		LongConsumer progress = bytesRead != null ? bytesRead : bytes -> {
		};
		Path absolute = target.toAbsolutePath().normalize();
		TargetLock targetLock = ACTIVE_TARGETS.compute(absolute, (key, existing) -> {
			TargetLock lock = existing != null ? existing : new TargetLock();
			lock.users++;
			return lock;
		});
		targetLock.lock.lock();
		try {
			long[] reported = {0};
			IOException last = null;
			for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
				if (cancelled.getAsBoolean()) {
					throw new CancelledException("Download cancelled: " + uri);
				}
				try {
					downloadOnce(uri, absolute, hashAlgorithm, expectedHash, progress, reported, cancelled);
					return;
				} catch (CancelledException e) {
					throw e;
				} catch (HttpException e) {
					if (!isRetryable(e.status())) {
						throw e;
					}
					last = e;
				} catch (IOException e) {
					if (isNetworkUnavailable(e)) {
						throw e;
					}
					last = e;
				}
				sleepCancellable(RETRY_BASE_DELAY_MS * attempt, cancelled);
			}
			throw last;
		} finally {
			targetLock.lock.unlock();
			ACTIVE_TARGETS.compute(absolute, (key, lock) -> --lock.users == 0 ? null : lock);
		}
	}

	/** The temporary file a download of {@code target} is written to. */
	public static Path partFile(Path target) {
		return target.resolveSibling(target.getFileName() + ".part");
	}

	private static void downloadOnce(URI uri, Path target, String hashAlgorithm, String expectedHash, LongConsumer progress,
		long[] reported, BooleanSupplier cancelled) throws IOException {
		Files.createDirectories(target.getParent());
		Path part = partFile(target);
		long existing = Files.isRegularFile(part) ? Files.size(part) : 0;

		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		boolean completed = false;
		try {
			connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
			connection.setReadTimeout((int) readTimeout.toMillis());
			connection.setInstanceFollowRedirects(true);
			connection.setRequestProperty("User-Agent", VantageInfo.USER_AGENT);
			connection.setRequestProperty("Accept-Encoding", "identity");
			if (existing > 0) {
				connection.setRequestProperty("Range", "bytes=" + existing + "-");
			}
			int status = connection.getResponseCode();
			if (status == 416) {
				// The partial file does not fit the server's file (e.g. it changed); start over.
				Files.deleteIfExists(part);
				throw new IOException("Could not resume " + uri + "; starting over");
			}
			if (status < 200 || status >= 300) {
				throw new HttpException(uri.toString(), status, null);
			}
			boolean resume = existing > 0 && status == 206 && rangeStart(connection) == existing;
			if (!resume) {
				existing = 0;
			}
			// Count bytes already on disk (from an earlier attempt or run), or roll back what a discarded part reported.
			progress.accept(existing - reported[0]);
			reported[0] = existing;

			MessageDigest digest = hashAlgorithm != null && expectedHash != null ? HashUtil.digest(hashAlgorithm) : null;
			if (digest != null && existing > 0) {
				HashUtil.update(digest, part);
			}
			StandardOpenOption[] mode = resume
				? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
				: new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
			try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(part, mode)) {
				byte[] buffer = new byte[BUFFER_SIZE];
				int read;
				while ((read = in.read(buffer)) != -1) {
					if (cancelled.getAsBoolean()) {
						throw new CancelledException("Download cancelled: " + uri);
					}
					out.write(buffer, 0, read);
					if (digest != null) {
						digest.update(buffer, 0, read);
					}
					reported[0] += read;
					progress.accept(read);
				}
			}
			long announced = connection.getContentLengthLong();
			if (announced >= 0 && Files.size(part) != existing + announced) {
				throw new IOException("Incomplete download of " + uri + " (" + Files.size(part) + " of " + (existing + announced) + " bytes)");
			}
			if (digest != null) {
				String actual = HexFormat.of().formatHex(digest.digest());
				if (!actual.equalsIgnoreCase(expectedHash)) {
					Files.deleteIfExists(part);
					throw new IOException("Hash mismatch for " + uri + " (expected " + expectedHash + ", got " + actual + ")");
				}
			}
			FileUtil.moveReplacing(part, target);
			completed = true;
		} finally {
			if (!completed) {
				// Do not return a half-read connection to the keep-alive pool.
				connection.disconnect();
			}
		}
	}

	/** First byte of a 206 response, from {@code Content-Range: bytes <start>-<end>/<total>}; -1 if unknown. */
	private static long rangeStart(HttpURLConnection connection) {
		String range = connection.getHeaderField("Content-Range");
		if (range == null || !range.startsWith("bytes ")) {
			return -1;
		}
		int dash = range.indexOf('-');
		try {
			return dash > 6 ? Long.parseLong(range.substring(6, dash).trim()) : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static <T> T parse(String body, Type type, Gson gson, URI uri) throws IOException {
		try {
			return gson.fromJson(body, type);
		} catch (com.google.gson.JsonParseException e) {
			throw new IOException("Unexpected response from " + uri + ": " + e.getMessage(), e);
		}
	}

	private static void sleepBeforeRetry(HttpResponse<?> response, int attempt) {
		long waitMs = 1000L * attempt;
		String header = response.headers().firstValue("Retry-After")
			.or(() -> response.headers().firstValue("X-Ratelimit-Reset"))
			.orElse(null);
		if (header != null) {
			try {
				waitMs = Math.min(MAX_RETRY_AFTER_MS, Long.parseLong(header.trim()) * 1000L);
			} catch (NumberFormatException ignored) {
				// fall back to linear backoff
			}
		}
		sleepQuietly(waitMs);
	}

	private static void sleepCancellable(long millis, BooleanSupplier cancelled) throws CancelledException {
		long end = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < end) {
			if (cancelled.getAsBoolean()) {
				throw new CancelledException("Download cancelled");
			}
			sleepQuietly(Math.min(100, Math.max(1, end - System.currentTimeMillis())));
		}
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
