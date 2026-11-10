package dev.vantage.common.util;

import java.io.IOException;

/** Thrown when the user cancelled a long-running operation such as a download. Not an error to report. */
public final class CancelledException extends IOException {
	private static final long serialVersionUID = 1L;

	public CancelledException(String message) {
		super(message);
	}
}
