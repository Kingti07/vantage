package dev.vantage.launcher.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vantage.launcher.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsStoreTest {
	@TempDir
	Path dir;

	private SettingsStore load() {
		SettingsStore store = new SettingsStore(new LauncherPaths(dir));
		store.load();
		return store;
	}

	private List<Path> backups(String prefix) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.getFileName().toString().startsWith(prefix + ".broken-")).toList();
		}
	}

	@Test
	void freshStartCreatesDefaultsWithCurrentVersion() {
		SettingsStore store = load();
		assertEquals(1, store.instances().size());
		assertEquals("default", store.selectedInstance().id);
		assertEquals(SettingsStore.CONFIG_VERSION, store.settings().configVersion);
		assertTrue(store.warnings().isEmpty());
		assertTrue(Files.exists(dir.resolve("launcher.json")));
	}

	@Test
	void damagedInstancesFileIsKeptAsBackup() throws IOException {
		String broken = "[{\"id\":\"abc\",\"name\":\"My PvP\"";
		Files.writeString(dir.resolve("instances.json"), broken, StandardCharsets.UTF_8);

		SettingsStore store = load();

		List<Path> backups = backups("instances");
		assertEquals(1, backups.size());
		assertEquals(broken, Files.readString(backups.getFirst()));
		assertEquals(1, store.warnings().size());
		assertEquals(1, store.instances().size());
	}

	@Test
	void damagedSettingsFileIsKeptAsBackup() throws IOException {
		Files.writeString(dir.resolve("launcher.json"), "not json at all {", StandardCharsets.UTF_8);
		SettingsStore store = load();
		assertEquals(1, backups("launcher").size());
		assertFalse(store.warnings().isEmpty());
		assertEquals(LauncherSettings.DEFAULT_ACCENT, store.settings().accentColor);
	}

	@Test
	void invalidValuesAreRepaired() throws IOException {
		Files.writeString(dir.resolve("launcher.json"), """
			{"configVersion":1,"accentColor":"red","afterLaunch":"TELEPORT","downloadThreads":0,
			 "updateRepository":"not a repo","selectedInstance":"missing"}
			""", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("instances.json"), """
			[{"id":"a1","name":"  ","memoryMb":0,"width":-5,"height":10,"minecraftVersion":null},
			 {"id":"../evil","name":"Escape"}]
			""", StandardCharsets.UTF_8);

		SettingsStore store = load();
		LauncherSettings settings = store.settings();
		assertEquals(LauncherSettings.DEFAULT_ACCENT, settings.accentColor);
		assertEquals(LauncherSettings.AfterLaunch.MINIMIZE, settings.afterLaunch);
		assertEquals(LauncherSettings.DEFAULT_DOWNLOAD_THREADS, settings.downloadThreads);
		assertEquals("", settings.updateRepository);
		assertEquals("a1", settings.selectedInstance);

		Instance first = store.instance("a1").orElseThrow();
		assertEquals("Installation", first.name);
		assertEquals(Instance.DEFAULT_MEMORY_MB, first.memoryMb);
		assertEquals(Instance.DEFAULT_WIDTH, first.width);
		assertEquals(dev.vantage.common.VantageInfo.CLIENT_MINECRAFT_VERSION, first.minecraftVersion);

		Instance second = store.instances().get(1);
		assertNotEquals("../evil", second.id);
		assertTrue(Instance.VALID_ID.matcher(second.id).matches());
		assertTrue(store.warnings().isEmpty(), "repairs of single values are silent");
	}

	@Test
	void duplicateIdKeepsItsGameFolder() throws IOException {
		Files.writeString(dir.resolve("instances.json"), """
			[{"id":"same","name":"One"},{"id":"same","name":"Two"}]
			""", StandardCharsets.UTF_8);
		SettingsStore store = load();
		Instance two = store.instances().get(1);
		assertNotEquals("same", two.id);
		assertEquals(dir.resolve("instances").resolve("same").toString(), two.gameDirectory);
	}

	@Test
	void unversionedFileIsMigratedAndSaved() throws IOException {
		Files.writeString(dir.resolve("launcher.json"), "{\"accentColor\":\"#112233\"}", StandardCharsets.UTF_8);
		load();
		JsonObject saved = JsonParser.parseString(Files.readString(dir.resolve("launcher.json"))).getAsJsonObject();
		assertEquals(SettingsStore.CONFIG_VERSION, saved.get("configVersion").getAsInt());
		assertEquals("#112233", saved.get("accentColor").getAsString(), "user values survive the migration");
	}

	@Test
	void changesArePersisted() throws IOException {
		SettingsStore store = load();
		Instance created = store.createInstance("Second", "1.21.11");
		created.memoryMb = 6144;
		store.updateInstance(created);
		store.select(created);

		SettingsStore reloaded = load();
		assertEquals(2, reloaded.instances().size());
		assertEquals(created.id, reloaded.selectedInstance().id);
		assertEquals(6144, reloaded.selectedInstance().memoryMb);
		JsonArray raw = JsonParser.parseString(Files.readString(dir.resolve("instances.json"))).getAsJsonArray();
		assertEquals(2, raw.size());
	}
}
