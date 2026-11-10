package dev.vantage.common.auth;

import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Where the key of {@link TokenCipher} is kept. The key never leaves the user's machine and is protected by the
 * operating system wherever possible.
 */
public interface KeyStorage {
	/** The stored key, or null if there is none yet. */
	byte[] load() throws IOException;

	void store(byte[] key) throws IOException;

	/** Human-readable name of the protection, e.g. "Windows DPAPI". */
	String name();

	/**
	 * The best protection available on this machine: Windows DPAPI (via JNA, which the launcher bundles and
	 * Minecraft ships), the macOS Keychain ({@code security}) or the Linux Secret Service ({@code secret-tool}).
	 * Falls back to a key file that only the current user can read when none of these is available.
	 */
	static KeyStorage forCurrentOs(Path launcherDir) {
		KeyStorage file = new FileKeyStorage(launcherDir.resolve("token-key.bin"));
		KeyStorage primary = switch (OperatingSystem.current()) {
			case WINDOWS -> DpapiKeyStorage.isAvailable() ? new DpapiKeyStorage(launcherDir.resolve("token-key.dpapi")) : null;
			case MACOS -> Files.isExecutable(Path.of("/usr/bin/security")) ? new CommandKeyStorage.MacKeychain() : null;
			case LINUX -> CommandKeyStorage.SecretTool.isAvailable() ? new CommandKeyStorage.SecretTool() : null;
		};
		return primary == null ? file : new WithFallback(primary, file);
	}

	/** Uses the OS protection, and a key file only if the OS protection fails. */
	final class WithFallback implements KeyStorage {
		private static final Logger LOGGER = LoggerFactory.getLogger(WithFallback.class);
		private final KeyStorage primary;
		private final KeyStorage fallback;
		private boolean usingFallback;

		WithFallback(KeyStorage primary, KeyStorage fallback) {
			this.primary = primary;
			this.fallback = fallback;
		}

		@Override
		public byte[] load() throws IOException {
			try {
				byte[] key = primary.load();
				if (key != null) {
					return key;
				}
			} catch (IOException | RuntimeException | LinkageError e) {
				LOGGER.warn("{} is not usable: {}", primary.name(), e.toString());
			}
			byte[] key = fallback.load();
			usingFallback = key != null;
			return key;
		}

		@Override
		public void store(byte[] key) throws IOException {
			try {
				primary.store(key);
				usingFallback = false;
			} catch (IOException | RuntimeException | LinkageError e) {
				LOGGER.warn("Could not store the key with {} ({}); using a private key file instead", primary.name(), e.toString());
				fallback.store(key);
				usingFallback = true;
			}
		}

		@Override
		public String name() {
			return usingFallback ? fallback.name() : primary.name();
		}
	}

	/** Key file readable only by the current user (POSIX permissions). Weakest option, used as a last resort. */
	final class FileKeyStorage implements KeyStorage {
		private final Path file;

		FileKeyStorage(Path file) {
			this.file = file;
		}

		@Override
		public byte[] load() throws IOException {
			return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
		}

		@Override
		public void store(byte[] key) throws IOException {
			Files.createDirectories(file.toAbsolutePath().getParent());
			FileUtil.writeAtomically(file, key);
			FileUtil.restrictToOwner(file);
		}

		@Override
		public String name() {
			return "a private key file";
		}
	}

	/** Windows Data Protection API: the key file can only be decrypted by the same Windows user. */
	final class DpapiKeyStorage implements KeyStorage {
		private final Path file;

		DpapiKeyStorage(Path file) {
			this.file = file;
		}

		static boolean isAvailable() {
			try {
				Class.forName("com.sun.jna.platform.win32.Crypt32Util");
				return true;
			} catch (ClassNotFoundException | LinkageError e) {
				return false;
			}
		}

		@Override
		public byte[] load() throws IOException {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			try {
				return com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(Files.readAllBytes(file));
			} catch (RuntimeException e) {
				throw new IOException("Windows could not unlock the key (" + e.getMessage() + ")", e);
			}
		}

		@Override
		public void store(byte[] key) throws IOException {
			byte[] protectedKey;
			try {
				protectedKey = com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(key);
			} catch (RuntimeException e) {
				throw new IOException("Windows could not protect the key (" + e.getMessage() + ")", e);
			}
			Files.createDirectories(file.toAbsolutePath().getParent());
			FileUtil.writeAtomically(file, protectedKey);
		}

		@Override
		public String name() {
			return "Windows DPAPI";
		}
	}

	/** Keeps the key in the OS keyring through its command line tool. */
	abstract class CommandKeyStorage implements KeyStorage {
		private static final String SERVICE = "Vantage Launcher";
		private static final String ACCOUNT = "token-key";

		/** Runs a command, optionally feeding {@code input} to it, and returns stdout (null on a non-zero exit). */
		static String run(List<String> command, String input) throws IOException {
			Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			try (OutputStream stdin = process.getOutputStream()) {
				if (input != null) {
					stdin.write(input.getBytes(StandardCharsets.UTF_8));
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream stdout = process.getInputStream()) {
				stdout.transferTo(out);
			}
			try {
				if (!process.waitFor(15, TimeUnit.SECONDS)) {
					process.destroyForcibly();
					throw new IOException(command.getFirst() + " did not respond");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted", e);
			}
			return process.exitValue() == 0 ? out.toString(StandardCharsets.UTF_8).trim() : null;
		}

		static byte[] decode(String value) throws IOException {
			if (value == null || value.isEmpty()) {
				return null;
			}
			try {
				return Base64.getDecoder().decode(value);
			} catch (IllegalArgumentException e) {
				throw new IOException("The stored key is damaged", e);
			}
		}

		/** macOS Keychain via {@code /usr/bin/security}. */
		static final class MacKeychain extends CommandKeyStorage {
			@Override
			public byte[] load() throws IOException {
				return decode(run(List.of("/usr/bin/security", "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w"), null));
			}

			@Override
			public void store(byte[] key) throws IOException {
				// The key is briefly visible in this user's process list; the keychain tool offers no stdin option.
				String result = run(List.of("/usr/bin/security", "add-generic-password", "-U", "-s", SERVICE, "-a", ACCOUNT,
					"-w", Base64.getEncoder().encodeToString(key)), null);
				if (result == null) {
					throw new IOException("The macOS Keychain refused to store the key");
				}
			}

			@Override
			public String name() {
				return "the macOS Keychain";
			}
		}

		/** Linux Secret Service (GNOME Keyring, KWallet) via {@code secret-tool}. */
		static final class SecretTool extends CommandKeyStorage {
			static boolean isAvailable() {
				String path = System.getenv("PATH");
				if (path == null) {
					return false;
				}
				for (String dir : path.split(java.io.File.pathSeparator)) {
					if (!dir.isBlank() && Files.isExecutable(Path.of(dir, "secret-tool"))) {
						return true;
					}
				}
				return false;
			}

			@Override
			public byte[] load() throws IOException {
				return decode(run(List.of("secret-tool", "lookup", "service", SERVICE, "account", ACCOUNT), null));
			}

			@Override
			public void store(byte[] key) throws IOException {
				String result = run(List.of("secret-tool", "store", "--label=" + SERVICE + " sign-in key", "service", SERVICE,
					"account", ACCOUNT), Base64.getEncoder().encodeToString(key));
				if (result == null) {
					throw new IOException("The Secret Service refused to store the key (is a keyring running?)");
				}
			}

			@Override
			public String name() {
				return "the system keyring";
			}
		}
	}
}
