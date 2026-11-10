package dev.vantage.common.util;

import java.io.IOException;

/** An HTTP request completed but returned a non-success status code. */
public final class HttpException extends IOException {
	private static final long serialVersionUID = 1L;

	private final int status;
	private final String body;

	public HttpException(String url, int status, String body) {
		super("HTTP " + status + " from " + url + (body == null || body.isBlank() ? "" : ": " + abbreviate(body)));
		this.status = status;
		this.body = body;
	}

	public int status() {
		return status;
	}

	public String body() {
		return body;
	}

	private static String abbreviate(String body) {
		String flat = body.replaceAll("\\s+", " ").trim();
		return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
	}
}
