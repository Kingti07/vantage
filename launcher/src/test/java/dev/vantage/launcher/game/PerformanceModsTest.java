package dev.vantage.launcher.game;

import dev.vantage.common.modrinth.ModrinthApi;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.settings.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renderer switching and the performance-mod toggle, offline (Modrinth is unreachable on purpose). */
class PerformanceModsTest {
	@TempDir
	Path dir;

	private ModManager mods() {
		// Port 1 refuses connections at once: identification and downloads fail fast, like being offline.
		return new ModManager(dir, new ModrinthApi("http://127.0.0.1:1"), "1.21.11", "fabric");
	}

	private void mod(String fileName, String modId) throws IOException {
		try (OutputStream out = Files.newOutputStream(dir.resolve(fileName)); ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"schemaVersion\":1,\"id\":\"" + modId + "\",\"version\":\"1.0.0\"}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}

	private static Instance instance() {
		Instance instance = new Instance();
		instance.id = "test";
		instance.performanceMods = true;
		instance.performanceModsInstalled = true;
		return instance;
	}

	@Test
	void vulkanDisablesOpenGlModsAndSwitchingBackRestoresThem() throws IOException {
		mod("sodium.jar", "sodium");
		mod("lithium.jar", "lithium");
		mod("vulkanmod.jar.disabled", "vulkanmod");
		Instance instance = instance();

		instance.renderer = Instance.Renderer.VULKAN;
		PerformanceMods.apply(instance, mods(), ProgressListener.NONE);
		assertTrue(Files.exists(dir.resolve("sodium.jar.disabled")), "Sodium cannot run with VulkanMod");
		assertTrue(Files.exists(dir.resolve("vulkanmod.jar")));
		assertTrue(Files.exists(dir.resolve("lithium.jar")), "Lithium does not touch rendering and stays on");
		assertEquals(List.of("sodium.jar"), instance.disabledForVulkan);

		instance.renderer = Instance.Renderer.OPENGL;
		PerformanceMods.apply(instance, mods(), ProgressListener.NONE);
		assertTrue(Files.exists(dir.resolve("sodium.jar")));
		assertTrue(Files.exists(dir.resolve("vulkanmod.jar.disabled")));
		assertTrue(instance.disabledForVulkan.isEmpty());
	}

	@Test
	void turningPerformanceModsOffDisablesThemWithoutDeleting() throws IOException {
		mod("sodium.jar", "sodium");
		mod("ferritecore.jar", "ferritecore");
		mod("other.jar", "othermod");
		Instance instance = instance();
		instance.performanceMods = false;

		PerformanceMods.apply(instance, mods(), ProgressListener.NONE);

		assertTrue(Files.exists(dir.resolve("sodium.jar.disabled")));
		assertTrue(Files.exists(dir.resolve("ferritecore.jar.disabled")));
		assertTrue(Files.exists(dir.resolve("other.jar")), "mods the player installed are not touched");
		assertFalse(instance.performanceModsInstalled);
	}

	@Test
	void missingInternetPostponesTheInstallation() throws IOException {
		Instance instance = instance();
		instance.performanceModsInstalled = false;
		long start = System.nanoTime();

		List<String> notes = PerformanceMods.apply(instance, mods(), ProgressListener.NONE);

		assertFalse(instance.performanceModsInstalled, "retried on the next launch");
		assertTrue(notes.stream().anyMatch(n -> n.contains("offline")), notes.toString());
		assertTrue((System.nanoTime() - start) / 1_000_000_000L < 10, "offline must not block the launch");
	}

	@Test
	void removingAPerformanceModByHandIsRespected() throws IOException {
		mod("lithium.jar", "lithium");
		Instance instance = instance();

		List<String> notes = PerformanceMods.apply(instance, mods(), ProgressListener.NONE);

		assertTrue(notes.isEmpty(), "nothing is reinstalled once the pack was installed: " + notes);
	}
}
