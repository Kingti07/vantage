package dev.vantage.common.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCipherTest {
	@TempDir
	Path dir;

	/** Keeps the key in memory, standing in for DPAPI / Keychain / Secret Service. */
	static final class MemoryKeyStorage implements KeyStorage {
		byte[] key;

		@Override
		public byte[] load() {
			return key;
		}

		@Override
		public void store(byte[] key) {
			this.key = key.clone();
		}

		@Override
		public String name() {
			return "memory";
		}
	}

	@Test
	void encryptsAndDecrypts() throws IOException {
		TokenCipher cipher = new TokenCipher(new MemoryKeyStorage());
		String encrypted = cipher.encrypt("secret-refresh-token");
		assertTrue(TokenCipher.isEncrypted(encrypted));
		assertFalse(encrypted.contains("secret"));
		assertNotEquals(encrypted, cipher.encrypt("secret-refresh-token"), "every value gets a fresh IV");
		assertEquals("secret-refresh-token", cipher.decrypt(encrypted));
		assertNull(cipher.encrypt(null));
		assertEquals("legacy-plain", cipher.decrypt("legacy-plain"));
	}

	@Test
	void otherKeyCannotDecrypt() throws IOException {
		String encrypted = new TokenCipher(new MemoryKeyStorage()).encrypt("token");
		TokenCipher other = new TokenCipher(new MemoryKeyStorage());
		other.encrypt("creates its own key");
		assertThrows(IOException.class, () -> other.decrypt(encrypted));
		assertThrows(IOException.class, () -> other.decrypt(TokenCipher.PREFIX + "!!!"));
	}

	@Test
	void plaintextAccountsFileIsEncryptedOnLoad() throws IOException {
		Files.writeString(dir.resolve("accounts.json"), """
			{"selected":"aaaa","accounts":[{"uuid":"aaaa","username":"Alice","microsoftRefreshToken":"plain-refresh",
			"minecraftAccessToken":"plain-access","minecraftTokenExpiry":1,"xuid":"0"}]}
			""", StandardCharsets.UTF_8);
		AccountStore store = new AccountStore(dir, new TokenCipher(new MemoryKeyStorage()));
		store.load();

		String onDisk = Files.readString(dir.resolve("accounts.json"));
		assertFalse(onDisk.contains("plain-refresh"));
		assertFalse(onDisk.contains("plain-access"));
		assertTrue(onDisk.contains(TokenCipher.PREFIX));
		MinecraftAccount account = store.selected().orElseThrow();
		assertEquals("plain-refresh", account.microsoftRefreshToken());
		assertEquals("plain-access", account.minecraftAccessToken());
	}

	@Test
	void lostKeyKeepsAccountButAsksToSignInAgain() throws IOException {
		AccountStore first = new AccountStore(dir, new TokenCipher(new MemoryKeyStorage()));
		first.load();
		first.put(new MinecraftAccount("aaaa", "Alice", "refresh", "access", Long.MAX_VALUE, "0", null));

		AccountStore otherMachine = new AccountStore(dir, new TokenCipher(new MemoryKeyStorage()));
		otherMachine.load();

		MinecraftAccount account = otherMachine.selected().orElseThrow();
		assertEquals("Alice", account.username());
		assertNull(account.microsoftRefreshToken());
		assertTrue(account.needsRefresh());
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void windowsProtectsTheKeyWithDpapi() throws IOException {
		KeyStorage storage = KeyStorage.forCurrentOs(dir);
		TokenCipher cipher = new TokenCipher(storage);
		String encrypted = cipher.encrypt("token");
		assertEquals("Windows DPAPI", cipher.protection());
		assertTrue(Files.exists(dir.resolve("token-key.dpapi")));
		assertFalse(Files.exists(dir.resolve("token-key.bin")));
		assertEquals("token", new TokenCipher(KeyStorage.forCurrentOs(dir)).decrypt(encrypted), "a new process can read it");
	}
}
