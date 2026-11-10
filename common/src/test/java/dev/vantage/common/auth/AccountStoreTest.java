package dev.vantage.common.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {
	@TempDir
	Path dir;

	private static MinecraftAccount account(String uuid, String name) {
		return new MinecraftAccount(uuid, name, "refresh-" + name, "access-" + name, Long.MAX_VALUE, "0", null);
	}

	@Test
	void damagedFileIsMovedAsideInsteadOfOverwritten() throws IOException {
		Files.writeString(dir.resolve("accounts.json"), "{\"accounts\":[{\"uuid\":", StandardCharsets.UTF_8);
		AccountStore store = new AccountStore(dir);

		Optional<Path> backup = store.load();

		assertTrue(backup.isPresent());
		assertTrue(Files.exists(backup.get()));
		assertFalse(Files.exists(dir.resolve("accounts.json")));
		assertTrue(store.accounts().isEmpty());
	}

	@Test
	void changesFromAnotherProcessAreNotLost() throws IOException {
		// Two stores stand for the launcher and the in-game account switcher.
		AccountStore launcher = new AccountStore(dir);
		AccountStore game = new AccountStore(dir);
		launcher.load();
		game.load();

		game.put(account("aaaa", "Alice"));
		launcher.put(account("bbbb", "Bob"));

		AccountStore check = new AccountStore(dir);
		check.load();
		assertEquals(2, check.accounts().size());
		assertEquals("bbbb", check.selected().orElseThrow().uuid());
	}

	@Test
	void tokenUpdateKeepsSelection() throws IOException {
		AccountStore store = new AccountStore(dir);
		store.load();
		store.put(account("aaaa", "Alice"));
		store.put(account("bbbb", "Bob"));
		store.select("aaaa");

		store.update(new MinecraftAccount("bbbb", "Bob", "new-refresh", "new-access", 1L, "0", null));

		AccountStore check = new AccountStore(dir);
		check.load();
		assertEquals("aaaa", check.selected().orElseThrow().uuid());
		assertEquals("new-access", check.accounts().stream().filter(a -> a.uuid().equals("bbbb")).findFirst()
			.orElseThrow().minecraftAccessToken());
	}

	@Test
	void removingSelectedAccountSelectsAnother() throws IOException {
		AccountStore store = new AccountStore(dir);
		store.load();
		store.put(account("aaaa", "Alice"));
		store.put(account("bbbb", "Bob"));
		store.remove("bbbb");
		assertEquals("aaaa", store.selected().orElseThrow().uuid());
	}
}
