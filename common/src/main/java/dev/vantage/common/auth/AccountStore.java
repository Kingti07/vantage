package dev.vantage.common.auth;

import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists signed-in accounts in {@code accounts.json} inside the launcher data directory. The file contains
 * refresh tokens, so it is written atomically and restricted to the current user where the OS supports it.
 * Shared by the launcher and the in-game account switcher.
 */
public final class AccountStore {
	private static final class Data {
		String selected;
		List<MinecraftAccount> accounts = new ArrayList<>();
	}

	private final Path file;
	private Data data = new Data();

	public AccountStore(Path launcherDir) {
		this.file = launcherDir.resolve("accounts.json");
	}

	public synchronized void load() throws IOException {
		if (!Files.isRegularFile(file)) {
			data = new Data();
			return;
		}
		Data loaded = Json.read(file, Data.class);
		data = loaded != null ? loaded : new Data();
		if (data.accounts == null) {
			data.accounts = new ArrayList<>();
		}
		data.accounts.removeIf(a -> a == null || a.uuid() == null);
	}

	public synchronized void save() throws IOException {
		Json.write(file, data);
		FileUtil.restrictToOwner(file);
	}

	public synchronized List<MinecraftAccount> accounts() {
		return List.copyOf(data.accounts);
	}

	public synchronized Optional<MinecraftAccount> selected() {
		return data.accounts.stream().filter(a -> a.uuid().equals(data.selected)).findFirst()
			.or(() -> data.accounts.stream().findFirst());
	}

	public synchronized void select(String uuid) throws IOException {
		data.selected = uuid;
		save();
	}

	/** Adds or replaces (by uuid) an account and selects it. */
	public synchronized void put(MinecraftAccount account) throws IOException {
		data.accounts.removeIf(a -> a.uuid().equals(account.uuid()));
		data.accounts.add(account);
		data.selected = account.uuid();
		save();
	}

	/** Updates tokens of an existing account without changing the selection. */
	public synchronized void update(MinecraftAccount account) throws IOException {
		for (int i = 0; i < data.accounts.size(); i++) {
			if (data.accounts.get(i).uuid().equals(account.uuid())) {
				data.accounts.set(i, account);
				save();
				return;
			}
		}
	}

	public synchronized void remove(String uuid) throws IOException {
		data.accounts.removeIf(a -> a.uuid().equals(uuid));
		if (uuid.equals(data.selected)) {
			data.selected = data.accounts.isEmpty() ? null : data.accounts.getFirst().uuid();
		}
		save();
	}
}
