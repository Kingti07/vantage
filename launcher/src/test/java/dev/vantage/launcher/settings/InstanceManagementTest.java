package dev.vantage.launcher.settings;

import dev.vantage.launcher.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creating, selecting and deleting installations ("profiles"). */
class InstanceManagementTest {
	@TempDir
	Path dir;

	private SettingsStore store() {
		SettingsStore store = new SettingsStore(new LauncherPaths(dir));
		store.load();
		return store;
	}

	@Test
	void newInstallationsGetUniqueSafeIdsAndOwnFolders() {
		SettingsStore store = store();
		Instance a = store.createInstance("PvP 1.21", "1.21.11");
		Instance b = store.createInstance("Old", "1.8.9");
		assertNotEquals(a.id, b.id);
		assertTrue(Instance.VALID_ID.matcher(a.id).matches());
		assertEquals(dir.resolve("instances").resolve(a.id), a.resolveGameDirectory(dir.resolve("instances")));
		assertTrue(a.vantageClient, "the Vantage client is enabled where it is supported");
		assertFalse(b.vantageClient, "and disabled on other Minecraft versions");
	}

	@Test
	void deletingTheSelectedInstallationSelectsAnother() {
		SettingsStore store = store();
		Instance extra = store.createInstance("Extra", "1.21.11");
		store.select(extra);
		store.deleteInstance(extra);
		assertEquals(1, store.instances().size());
		assertEquals("default", store.selectedInstance().id);
		assertEquals("default", store().selectedInstance().id, "the change is saved");
	}

	@Test
	void theLastInstallationCannotBeDeleted() {
		SettingsStore store = store();
		assertThrows(IllegalStateException.class, () -> store.deleteInstance(store.selectedInstance()));
	}

	@Test
	void editsOfACopyOnlyApplyWhenSaved() {
		SettingsStore store = store();
		Instance copy = store.selectedInstance().copy();
		copy.memoryMb = 8192;
		assertEquals(Instance.DEFAULT_MEMORY_MB, store().selectedInstance().memoryMb);
		store.updateInstance(copy);
		assertEquals(8192, store().selectedInstance().memoryMb);
	}
}
