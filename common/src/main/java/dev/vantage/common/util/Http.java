package dev.vantage.common.util;

import com.google.gson.Gson;
import dev.vantage.common.VantageInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.LongConsumer;

/**
 * Thin wrapper around {@link HttpClient} with JSON helpers, bounded retries and verified downloads.
 */
public final class Http {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(15))
		.build();
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final int MAX_ATTEMPTS = 3;

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
	 * Sends a request and returns the body of a 2xx response. Retries on connection failures,
	 * 429 (honouring Retry-After / X-Ratelimit-Reset up to 10s) and 5xx responses.
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
				if (status == 429 || status >= 500) {
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
				last = e;
				sleepQuietly(500L * attempt);
			}
		}
		throw last != null ? last : new IOException("Request failed: " + request.uri());
	}

	/**
	 * Downloads {@code uri} to {@code target}, verifying the given hash when present. The file is
	 * written to a temporary sibling first so an interrupted download never leaves a corrupt file.
	 *
	 * @param hashAlgorithm e.g. "SHA-1" or "SHA-512"; may be null when no hash is known
	 * @param bytesRead     receives the number of new bytes read; may be null
	 */
	public static void download(URI uri, Path target, String hashAlgorithm, String expectedHash, LongConsumer bytesRead) throws IOException {
		IOException last = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				downloadOnce(uri, target, hashAlgorithm, expectedHash, bytesRead);
				return;
			} catch (HttpException e) {
				if (e.status() < 500 && e.status() != 429) {
					throw e;
				}
				last = e;
			} catch (IOException e) {
				last = e;
			}
			sleepQuietly(750L * attempt);
		}
		throw last;
	}

	private static void downloadOnce(URI uri, Path target, String hashAlgorithm, String expectedHash, LongConsumer bytesRead) throws IOException {
		Path parent = target.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".part");
		try {
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofMinutes(5))
				.header("User-Agent", VantageInfo.USER_AGENT)
				.GET()
				.build();
			HttpResponse<InputStream> response;
			try {
				response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while downloading " + uri, e);
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				response.body().close();
				throw new HttpException(uri.toString(), response.statusCode(), null);
			}
			MessageDigest digest = hashAlgorithm != null && expectedHash != null ? HashUtil.digest(hashAlgorithm) : null;
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
				byte[] buffer = new byte[64 * 1024];
				int read;
				while ((read = in.read(buffer)) != -1) {
					if (Thread.currentThread().isInterrupted()) {
						throw new IOException("Download cancelled: " + uri);
					}
					out.write(buffer, 0, read);
					if (digest != null) {
						digest.update(buffer, 0, read);
					}
					if (bytesRead != null) {
						bytesRead.accept(read);
					}
				}
			}
			if (digest != null) {
				String actual = HexFormat.of().formatHex(digest.digest());
				if (!actual.equalsIgnoreCase(expectedHash)) {
					throw new IOException("Hash mismatch for " + uri + " (expected " + expectedHash + ", got " + actual + ")");
				}
			}
			FileUtil.moveReplacing(temp, target);
		} finally {
			Files.deleteIfExists(temp);
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
				waitMs = Math.min(10_000L, Long.parseLong(header.trim()) * 1000L);
			} catch (NumberFormatException ignored) {
				// fall back to linear backoff
			}
		}
		sleepQuietly(waitMs);
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
