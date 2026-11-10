package dev.vantage.common.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts sign-in tokens before they are written to {@code accounts.json}.
 * <p>
 * Tokens are encrypted with AES-256-GCM under a random key. The key itself is protected by the operating system
 * (see {@link KeyStorage#forCurrentOs}): Windows DPAPI, the macOS Keychain or the Linux Secret Service. Only the
 * same user on the same machine can therefore read the tokens; copying {@code accounts.json} elsewhere is useless.
 * Encrypted values look like {@code enc:v1:<base64>}; values without that prefix are plaintext from older launcher
 * versions and are encrypted the next time the file is written.
 */
public final class TokenCipher {
	public static final String PREFIX = "enc:v1:";
	private static final Logger LOGGER = LoggerFactory.getLogger(TokenCipher.class);
	private static final int KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final KeyStorage storage;
	private byte[] key;

	public TokenCipher(KeyStorage storage) {
		this.storage = storage;
	}

	/** Cipher whose key is protected by the best mechanism this operating system offers. */
	public static TokenCipher forLauncherDir(Path launcherDir) {
		return new TokenCipher(KeyStorage.forCurrentOs(launcherDir));
	}

	/** Name of the protection in use, for display (e.g. "Windows DPAPI"). */
	public String protection() {
		return storage.name();
	}

	public static boolean isEncrypted(String value) {
		return value != null && value.startsWith(PREFIX);
	}

	/** Encrypts {@code plain}; null stays null. Creates and stores the key on first use. */
	public synchronized String encrypt(String plain) throws IOException {
		if (plain == null) {
			return null;
		}
		byte[] iv = new byte[IV_BYTES];
		RANDOM.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = Arrays.copyOf(iv, IV_BYTES + encrypted.length);
			System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
			return PREFIX + Base64.getEncoder().encodeToString(out);
		} catch (GeneralSecurityException e) {
			throw new IOException("Could not encrypt sign-in data", e);
		}
	}

	/**
	 * Decrypts a value written by {@link #encrypt}. Plaintext values (older files) are returned unchanged.
	 *
	 * @throws IOException if the value cannot be decrypted, e.g. because the key belongs to another user or machine
	 */
	public synchronized String decrypt(String value) throws IOException {
		if (value == null || !isEncrypted(value)) {
			return value;
		}
		byte[] data;
		try {
			data = Base64.getDecoder().decode(value.substring(PREFIX.length()));
		} catch (IllegalArgumentException e) {
			throw new IOException("Saved sign-in data is damaged", e);
		}
		if (data.length <= IV_BYTES) {
			throw new IOException("Saved sign-in data is damaged");
		}
		byte[] keyBytes = key(false);
		if (keyBytes == null) {
			throw new IOException("The key for the saved sign-in data is missing (" + storage.name() + ")");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
			return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IOException("Saved sign-in data could not be decrypted (" + storage.name() + ")", e);
		}
	}

	private byte[] key(boolean create) throws IOException {
		if (key != null) {
			return key;
		}
		byte[] loaded = storage.load();
		if (loaded != null && loaded.length == KEY_BYTES) {
			key = loaded;
			return key;
		}
		if (!create) {
			return null;
		}
		byte[] fresh = new byte[KEY_BYTES];
		RANDOM.nextBytes(fresh);
		storage.store(fresh);
		LOGGER.info("Created a new key for sign-in data, protected by {}", storage.name());
		key = fresh;
		return key;
	}
}
