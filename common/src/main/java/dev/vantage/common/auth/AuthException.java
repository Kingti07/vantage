package dev.vantage.common.auth;

/** Sign-in failed. The message is written for end users. */
public final class AuthException extends Exception {
	public AuthException(String message) {
		super(message);
	}

	public AuthException(String message, Throwable cause) {
		super(message, cause);
	}
}
