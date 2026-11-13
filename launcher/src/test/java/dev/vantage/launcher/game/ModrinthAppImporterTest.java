package dev.vantage.launcher.game;

import dev.vantage.common.util.ProgressListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses a database written by SQLite with the Modrinth App's table layout ({@code /modrinth-app}): profile "pvp (1)"
 * (Fabric 1.21.11, renamed in the write-ahead log) and "forge" (Forge 1.20.1).
 */
class ModrinthAppImporterTest {
	@TempDir
	Path dir;

	private Path dataDir() throws IOException {
		Path data = dir.resolve("ModrinthApp");
		Files.createDirectories(data);
		for (String name : List.of("app.db", "app.db-wal")) {
			try (InputStream in = ModrinthAppImporterTest.class.getResourceAsStream("/modrinth-app/" + name)) {
				Files.copy(in, data.resolve(name));
			}
		}
		Path pvp = data.resolve("profiles").resolve("pvp (1)");
		write(pvp.resolve("mods/sodium.jar"), "jar");
		write(pvp.resolve("mods/old.jar.disabled"), "jar");
		write(pvp.resolve("config/sodium-options.json"), "{}");
		write(pvp.resolve("options.txt"), "guiScale:3");
		write(pvp.resolve("resourcepacks/pack.zip"), "zip");
		write(pvp.resolve("saves/World/level.dat"), "x".repeat(1000));
		write(pvp.resolve("saves/World/session.lock"), "lock");
		write(pvp.resolve("logs/latest.log"), "log");
		write(pvp.resolve(".fabric/remappedJars/a.jar"), "cache");
		write(pvp.resolve("hs_err_pid123.log"), "crash");
		Files.createDirectories(data.resolve("profiles").resolve("forge"));
		write(data.resolve("profiles/no-db/logs/latest.log"),
			"[12:00:00] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.20.1\n");
		Files.createDirectories(data.resolve("profiles").resolve("unknown"));
		return data;
	}

	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	private static ModrinthAppImporter.Profile find(List<ModrinthAppImporter.Profile> profiles, String folder) {
		return profiles.stream().filter(p -> p.folder().getFileName().toString().equals(folder)).findFirst().orElseThrow();
	}

	@Test
	void readsProfilesFromTheDatabaseIncludingTheWriteAheadLog() throws IOException {
		List<ModrinthAppImporter.Profile> profiles = ModrinthAppImporter.profiles(dataDir());
		assertEquals(4, profiles.size());
		ModrinthAppImporter.Profile pvp = find(profiles, "pvp (1)");
		assertEquals("PvP Pack Renamed", pvp.name());
		assertEquals("1.21.11", pvp.gameVersion());
		assertEquals("fabric", pvp.loader());
		assertEquals("0.19.3", pvp.loaderVersion());
		assertNull(pvp.problem());
		ModrinthAppImporter.Profile forge = find(profiles, "forge");
		assertEquals("Forge Stuff", forge.name());
		assertFalse(forge.isSupported());
		assertNotNull(forge.problem());
	}

	@Test
	void fallsBackToTheGameLogWithoutDatabaseEntry() throws IOException {
		List<ModrinthAppImporter.Profile> profiles = ModrinthAppImporter.profiles(dataDir());
		ModrinthAppImporter.Profile noDb = find(profiles, "no-db");
		assertEquals("1.21.11", noDb.gameVersion());
		assertEquals("0.20.1", noDb.loaderVersion());
		assertTrue(noDb.isSupported());
		ModrinthAppImporter.Profile unknown = find(profiles, "unknown");
		assertNull(unknown.gameVersion());
		assertNotNull(unknown.problem());
	}

	@Test
	void copiesTheProfileWithoutLogsCachesAndCrashDumps() throws IOException {
		ModrinthAppImporter.Profile pvp = find(ModrinthAppImporter.profiles(dataDir()), "pvp (1)");
		Path target = dir.resolve("instance");
		assertEquals(5, ModrinthAppImporter.copy(pvp, target, false, ProgressListener.NONE));
		assertTrue(Files.isRegularFile(target.resolve("mods/sodium.jar")));
		assertTrue(Files.isRegularFile(target.resolve("mods/old.jar.disabled")));
		assertEquals("guiScale:3", Files.readString(target.resolve("options.txt")));
		assertTrue(Files.isRegularFile(target.resolve("config/sodium-options.json")));
		assertTrue(Files.isRegularFile(target.resolve("resourcepacks/pack.zip")));
		assertFalse(Files.exists(target.resolve("saves")));
		assertFalse(Files.exists(target.resolve("logs")));
		assertFalse(Files.exists(target.resolve(".fabric")));
		assertFalse(Files.exists(target.resolve("hs_err_pid123.log")));
	}

	@Test
	void copiesWorldsOnRequestButNotTheirLocks() throws IOException {
		ModrinthAppImporter.Profile pvp = find(ModrinthAppImporter.profiles(dataDir()), "pvp (1)");
		assertEquals(1000, ModrinthAppImporter.size(pvp, true) - ModrinthAppImporter.size(pvp, false));
		Path target = dir.resolve("instance");
		ModrinthAppImporter.copy(pvp, target, true, ProgressListener.NONE);
		assertTrue(Files.isRegularFile(target.resolve("saves/World/level.dat")));
		assertFalse(Files.exists(target.resolve("saves/World/session.lock")));
	}
}
