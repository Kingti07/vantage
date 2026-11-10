package dev.vantage.common.auth;

import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Json;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists signed-in accounts in {@code accounts.json} inside the launcher data directory. The file contains
 * refresh tokens, so it is written atomically and restricted to the current user where the OS supports it.
 * <p>
 * The launcher and the in-game account switcher (a different process) share the file. Every change therefore
 * re-reads the file under a lock and applies the change to the fresh content, so neither side overwrites what
 * the other one wrote. A file that cannot be parsed is moved aside instead of being overwritten.
 * <p>
 * Tokens are stored encrypted (see {@link TokenCipher}); in memory they are plain. Files written by older launcher
 * versions with plaintext tokens are encrypted the first time they are loaded.
 */
public final class AccountStore {
	/** Serialises access between stores of the same JVM; the file lock covers other processes. */
	private static final Object JVM_LOCK = new Object();
	private static final Logger LOGGER = LoggerFactory.getLogger(AccountStore.class);

	private static final class Data {
		String selected;
		List<MinecraftAccount> accounts = new ArrayList<>();
	}

	private final Path file;
	private final Path lockFile;
	private final TokenCipher cipher;
	private Data data = new Data();
	private boolean plaintextOnDisk;

	public AccountStore(Path launcherDir) {
		this(launcherDir, TokenCipher.forLauncherDir(launcherDir));
	}

	public AccountStore(Path launcherDir, TokenCipher cipher) {
		this.file = launcherDir.resolve("accounts.json");
		this.lockFile = launcherDir.resolve("accounts.json.lock");
		this.cipher = cipher;
	}

	/** How the tokens are protected on disk, e.g. "Windows DPAPI". */
	public String protection() {
		return cipher.protection();
	}

	/**
	 * Reads the accounts file. If it is damaged it is moved aside (e.g. {@code accounts.broken-<time>.json}), the
	 * store starts empty and the location of the backup is returned.
	 *
	 * @throws IOException if the file exists but can be neither read nor moved aside
	 */
	public Optional<Path> load() throws IOException {
		synchronized (JVM_LOCK) {
			try {
				data = read();
				if (plaintextOnDisk) {
					try {
						modify(d -> {
						});
					} catch (IOException e) {
						LOGGER.warn("Could not encrypt the saved sign-in data yet: {}", e.getMessage());
					}
				}
				return Optional.empty();
			} catch (IOException e) {
				if (!isMalformed(e)) {
					throw e;
				}
				Path backup = FileUtil.moveAside(file);
				data = new Data();
				return Optional.of(backup);
			}
		}
	}

	/** Re-reads the file, keeping the current state if that fails. Used before acting on the selected account. */
	public void reload() {
		synchronized (JVM_LOCK) {
			try {
				data = read();
			} catch (IOException ignored) {
				// keep what we have; load() reports problems
			}
		}
	}

	public List<MinecraftAccount> accounts() {
		synchronized (JVM_LOCK) {
			return List.copyOf(data.accounts);
		}
	}

	public Optional<MinecraftAccount> selected() {
		synchronized (JVM_LOCK) {
			return data.accounts.stream().filter(a -> a.uuid().equals(data.selected)).findFirst()
				.or(() -> data.accounts.stream().findFirst());
		}
	}

	public void select(String uuid) throws IOException {
		modify(d -> d.selected = uuid);
	}

	/** Adds or replaces (by uuid) an account and selects it. */
	public void put(MinecraftAccount account) throws IOException {
		modify(d -> {
			d.accounts.removeIf(a -> a.uuid().equals(account.uuid()));
			d.accounts.add(account);
			d.selected = account.uuid();
		});
	}

	/** Updates tokens of an existing account without changing the selection. */
	public void update(MinecraftAccount account) throws IOException {
		modify(d -> d.accounts.replaceAll(a -> a.uuid().equals(account.uuid()) ? account : a));
	}

	public void remove(String uuid) throws IOException {
		modify(d -> {
			d.accounts.removeIf(a -> a.uuid().equals(uuid));
			if (uuid.equals(d.selected)) {
				d.selected = d.accounts.isEmpty() ? null : d.accounts.getFirst().uuid();
			}
		});
	}

	/** Applies {@code change} to the current file content (not to a possibly stale copy) and saves the result. */
	@SuppressWarnings("try") // the lock is held for the duration of the block, never used directly
	private void modify(Consumer<Data> change) throws IOException {
		synchronized (JVM_LOCK) {
			Files.createDirectories(file.toAbsolutePath().getParent());
			try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				 FileLock ignored = channel.lock()) {
				Data fresh;
				try {
					fresh = read();
				} catch (IOException e) {
					if (!isMalformed(e)) {
						throw e;
					}
					FileUtil.moveAside(file);
					fresh = copyOf(data);
				}
				change.accept(fresh);
				Json.write(file, encrypted(fresh));
				FileUtil.restrictToOwner(file);
				data = fresh;
			}
		}
	}

	private Data read() throws IOException {
		if (!Files.isRegularFile(file)) {
			return new Data();
		}
		Data loaded = Json.read(file, Data.class);
		if (loaded == null) {
			return new Data();
		}
		if (loaded.accounts == null) {
			loaded.accounts = new ArrayList<>();
		}
		loaded.accounts.removeIf(a -> a == null || a.uuid() == null);
		plaintextOnDisk = false;
		List<MinecraftAccount> decrypted = new ArrayList<>();
		for (MinecraftAccount stored : loaded.accounts) {
			plaintextOnDisk |= isPlaintext(stored.microsoftRefreshToken()) || isPlaintext(stored.minecraftAccessToken());
			decrypted.add(new MinecraftAccount(stored.uuid(), stored.username(), decrypt(stored, stored.microsoftRefreshToken()),
				decrypt(stored, stored.minecraftAccessToken()), stored.minecraftTokenExpiry(), stored.xuid(), stored.skinUrl()));
		}
		loaded.accounts = decrypted;
		return loaded;
	}

	/** The on-disk form of {@code source}: same data with encrypted tokens. */
	private Data encrypted(Data source) throws IOException {
		Data out = new Data();
		out.selected = source.selected;
		for (MinecraftAccount account : source.accounts) {
			out.accounts.add(new MinecraftAccount(account.uuid(), account.username(), cipher.encrypt(account.microsoftRefreshToken()),
				cipher.encrypt(account.minecraftAccessToken()), account.minecraftTokenExpiry(), account.xuid(), account.skinUrl()));
		}
		return out;
	}

	/** A token that cannot be decrypted is dropped: the account stays listed and asks to sign in again. */
	private String decrypt(MinecraftAccount account, String value) {
		try {
			return cipher.decrypt(value);
		} catch (IOException e) {
			LOGGER.warn("Saved sign-in of {} could not be decrypted ({}); it has to sign in again", account.username(), e.getMessage());
			return null;
		}
	}

	private static boolean isPlaintext(String token) {
		return token != null && !TokenCipher.isEncrypted(token);
	}

	private static Data copyOf(Data source) {
		Data copy = new Data();
		copy.selected = source.selected;
		copy.accounts = new ArrayList<>(source.accounts);
		return copy;
	}

	private static boolean isMalformed(IOException e) {
		return e.getCause() instanceof com.google.gson.JsonParseException;
	}
}
