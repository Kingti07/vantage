package dev.vantage.launcher.ui;

import dev.vantage.common.auth.AuthException;
import dev.vantage.common.util.CancelledException;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.HttpException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns exceptions into short messages a player understands, with a hint what to do. Technical details stay in
 * the log and behind the "Copy details" button of {@link Overlay#error}.
 */
public final class ErrorText {
	private static final Pattern URL_IN_MESSAGE = Pattern.compile("from (https?://\\S+)");

	/** A user-facing description; {@code hint} may be null. */
	public record Described(String message, String hint) {
		public String text() {
			return hint == null ? message : message + "\n\n" + hint;
		}
	}

	private ErrorText() {
	}

	public static Described describe(Throwable error) {
		if (error == null) {
			return new Described("Something went wrong.", "See the launcher log for details.");
		}
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof CancelledException) {
				return new Described("Cancelled.", null);
			}
		}
		if (Http.isNetworkUnavailable(error)) {
			return new Described("No internet connection, or the server could not be reached.",
				"Check your connection and try again. Installations you have played before can still be started offline.");
		}
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof AuthException) {
				return new Described(clean(t.getMessage(), "Microsoft sign-in failed."), null);
			}
			if (t instanceof HttpException http) {
				return describeHttp(http);
			}
			if (t instanceof SSLException) {
				return new Described("A secure connection could not be established.",
					"Check that your computer's date and time are correct and that no antivirus or proxy intercepts HTTPS.");
			}
			if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
				return new Described("The server took too long to respond.", "Your connection may be slow or unstable. Try again in a moment.");
			}
			if (t instanceof AccessDeniedException denied) {
				return new Described("Vantage is not allowed to change " + fileName(denied) + ".",
					"Close programs that might use this file (for example a running Minecraft) or check the folder's permissions.");
			}
			if (t instanceof NoSuchFileException missing) {
				return new Described("A required file is missing: " + fileName(missing) + ".",
					"Use \"Repair files\" on the installation, or check that the folder still exists.");
			}
			if (t instanceof IOException) {
				String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
				if (message.contains("not enough space") || message.contains("no space left") || message.contains("disk full")
					|| message.contains("disk is full")) {
					return new Described("The disk is full.", "Free up some space and try again. A full Minecraft installation needs about 1 GB.");
				}
				if (message.contains("hash mismatch")) {
					return new Described("A downloaded file was damaged on the way.",
						"Try again. If it keeps happening, your connection or a proxy may be altering downloads.");
				}
			}
			if (t instanceof OutOfMemoryError) {
				return new Described("The launcher ran out of memory.", "Close other programs and restart the launcher.");
			}
		}
		return new Described(clean(firstMessage(error), "Something went wrong (" + error.getClass().getSimpleName() + ")."),
			"See the launcher log for details.");
	}

	private static Described describeHttp(HttpException http) {
		String host = host(http.getMessage());
		int status = http.status();
		if (status == 404) {
			return new Described("The server does not have the requested file (HTTP 404" + host + ").",
				"It may have been removed. Try again later or choose another version.");
		}
		if (status == 401 || status == 403) {
			return new Described("The server refused access (HTTP " + status + host + ").", null);
		}
		if (status == 429) {
			return new Described("The server is receiving too many requests right now" + host + ".", "Wait a minute and try again.");
		}
		if (status >= 500) {
			return new Described("The server has a problem right now (HTTP " + status + host + ").", "This is not caused by your computer. Try again later.");
		}
		return new Described("The server answered with an error (HTTP " + status + host + ").", "Try again later.");
	}

	private static String host(String message) {
		if (message == null) {
			return "";
		}
		Matcher matcher = URL_IN_MESSAGE.matcher(message);
		if (!matcher.find()) {
			return "";
		}
		try {
			String host = URI.create(matcher.group(1)).getHost();
			return host == null ? "" : ", " + host;
		} catch (IllegalArgumentException e) {
			return "";
		}
	}

	private static String fileName(FileSystemException e) {
		String file = e.getFile();
		if (file == null) {
			return "a file";
		}
		Path path;
		try {
			path = Path.of(file);
		} catch (java.nio.file.InvalidPathException invalid) {
			return "\"" + file + "\"";
		}
		if (path.getFileName() == null) {
			return "\"" + file + "\"";
		}
		return path.getParent() != null ? "\"" + path.getFileName() + "\" in " + path.getParent() : "\"" + path.getFileName() + "\"";
	}

	private static String firstMessage(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t.getMessage() != null && !t.getMessage().isBlank()) {
				return t.getMessage();
			}
		}
		return null;
	}

	/** Removes "…: null" left by wrapped exceptions without a message. */
	static String clean(String message, String fallback) {
		if (message == null || message.isBlank()) {
			return fallback;
		}
		String cleaned = message.replaceAll("(: null)+$", "").trim();
		return cleaned.isEmpty() ? fallback : cleaned;
	}
}
