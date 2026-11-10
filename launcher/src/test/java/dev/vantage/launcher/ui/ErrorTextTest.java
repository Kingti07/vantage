package dev.vantage.launcher.ui;

import dev.vantage.common.auth.AuthException;
import dev.vantage.common.util.CancelledException;
import dev.vantage.common.util.HttpException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorTextTest {
	@Test
	void offlineErrorsBecomeAConnectionMessage() {
		// What HttpClient throws when the network is down: no message, only nested causes.
		ConnectException connect = new ConnectException();
		connect.initCause(new ClosedChannelException());
		IOException wrapped = new IOException("Could not download the Minecraft version list: null", connect);
		ErrorText.Described described = ErrorText.describe(wrapped);
		assertTrue(described.message().startsWith("No internet connection"));
		assertFalse(described.text().contains("null"));
		assertFalse(described.text().contains("Exception"));
	}

	@Test
	void serverErrorsNameTheHost() {
		String text = ErrorText.describe(new HttpException("https://api.modrinth.com/v2/search", 503, null)).text();
		assertTrue(text.contains("HTTP 503, api.modrinth.com"), text);
		assertTrue(text.contains("Try again later"));
	}

	@Test
	void fileProblemsGetAHint() {
		// Built with the platform separator so the test means the same on Windows, macOS and Linux.
		String file = java.nio.file.Path.of("games", "mods", "a.jar").toAbsolutePath().toString();
		assertTrue(ErrorText.describe(new AccessDeniedException(file)).text().contains("not allowed to change \"a.jar\" in "), file);
		assertTrue(ErrorText.describe(new AccessDeniedException("a.jar")).text().contains("not allowed to change \"a.jar\"."));
		assertEquals("The disk is full.",
			ErrorText.describe(new FileSystemException("x: There is not enough space on the disk")).message());
		assertTrue(ErrorText.describe(new IOException("Hash mismatch for https://x (expected a, got b)")).message().contains("damaged"));
	}

	@Test
	void authAndCancelMessagesAreKept() {
		assertEquals("This account must be signed in again.", ErrorText.describe(new AuthException("This account must be signed in again.")).text());
		assertEquals("Cancelled.", ErrorText.describe(new CancelledException("Download cancelled")).text());
	}

	@Test
	void unknownErrorsNeverShowNull() {
		assertEquals("Something went wrong (IllegalStateException).", ErrorText.describe(new IllegalStateException()).message());
		assertEquals("Could not do it", ErrorText.clean("Could not do it: null", "fallback"));
	}
}
