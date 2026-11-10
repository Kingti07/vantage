package dev.vantage.launcher.settings;

import dev.vantage.launcher.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Schema v2 (Vantage 0.2.0): performance mods for new installations, a one-time offer for existing ones. */
class PerformanceMigrationTest {
	@TempDir
	Path dir;

	@Test
	void newInstallationsGetPerformanceMods() {
		SettingsStore store = new SettingsStore(new LauncherPaths(dir));
		store.load();
		Instance instance = store.selectedInstance();
		assertTrue(instance.performanceMods);
		assertFalse(instance.offerPerformanceMods);
		assertEquals(Instance.Renderer.OPENGL, instance.renderer);
		assertTrue(store.createInstance("New", "1.21.11").performanceMods);
	}

	@Test
	void installationsFromVersion010AreAskedInsteadOfChanged() throws IOException {
		Files.writeString(dir.resolve("launcher.json"), "{\"configVersion\":1,\"selectedInstance\":\"default\"}", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("instances.json"), "[{\"id\":\"default\",\"name\":\"Vantage PvP\",\"memoryMb\":6144}]",
			StandardCharsets.UTF_8);

		SettingsStore store = new SettingsStore(new LauncherPaths(dir));
		store.load();

		Instance instance = store.selectedInstance();
		assertFalse(instance.performanceMods, "nothing changes without asking");
		assertTrue(instance.offerPerformanceMods);
		assertEquals(6144, instance.memoryMb, "existing values survive");
		assertEquals(SettingsStore.CONFIG_VERSION, store.settings().configVersion);

		SettingsStore reloaded = new SettingsStore(new LauncherPaths(dir));
		reloaded.load();
		assertTrue(reloaded.selectedInstance().offerPerformanceMods, "the migration runs once and is saved");
	}
}
