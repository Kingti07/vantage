package dev.vantage.launcher.game;

import dev.vantage.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcFlagsTest {
	@Test
	void balancedUsesTunedG1() {
		List<String> flags = GameLauncher.gcFlags(LauncherSettings.GcMode.BALANCED, 21);
		assertEquals("-XX:+UseG1GC", flags.getFirst());
		assertTrue(flags.contains("-XX:G1NewSizePercent=20"));
		assertTrue(flags.contains("-XX:+UnlockExperimentalVMOptions"));
	}

	@Test
	void lowLatencyUsesGenerationalZgcOnlyWhereItExists() {
		assertEquals(List.of("-XX:+UseZGC", "-XX:+ZGenerational"), GameLauncher.gcFlags(LauncherSettings.GcMode.LOW_LATENCY, 21));
		assertEquals("-XX:+UseG1GC", GameLauncher.gcFlags(LauncherSettings.GcMode.LOW_LATENCY, 17).getFirst(),
			"Java 17 (older Minecraft versions) has no generational ZGC");
	}

	@Test
	void gpuPreferenceTargetsTheWindowedJava() {
		assertEquals("javaw.exe", WindowsTuning.gameExecutable(java.nio.file.Path.of("C:", "nowhere", "bin", "javaw.exe")).getFileName().toString());
	}
}
