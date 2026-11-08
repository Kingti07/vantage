package dev.vantage.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HashUtil {
	private HashUtil() {
	}

	public static String sha1(Path file) throws IOException {
		return hash(file, "SHA-1");
	}

	public static String sha512(Path file) throws IOException {
		return hash(file, "SHA-512");
	}

	public static String hash(Path file, String algorithm) throws IOException {
		MessageDigest digest = digest(algorithm);
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	public static MessageDigest digest(String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("JVM is missing required digest " + algorithm, e);
		}
	}
}
