package dev.vantage.common.mods;

import dev.vantage.common.modrinth.ModrinthApi;
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

class ModManagerAnalyzeTest {
	@TempDir
	Path mods;

	private void jar(String name, String fabricJson) throws IOException {
		try (OutputStream out = Files.newOutputStream(mods.resolve(name)); ZipOutputStream zip = new ZipOutputStream(out)) {
			if (fabricJson != null) {
				zip.putNextEntry(new ZipEntry("fabric.mod.json"));
				zip.write(fabricJson.getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			} else {
				zip.putNextEntry(new ZipEntry("META-INF/mods.toml"));
				zip.write("modLoader=\"javafml\"".getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
	}

	@Test
	void detectsCommonProblems() throws IOException {
		jar("a.jar", "{\"id\":\"a\",\"version\":\"1.0\",\"depends\":{\"minecraft\":\"~1.21.10\",\"b\":\">=2.0\",\"missing\":\"*\"}}");
		jar("b.jar", "{\"id\":\"b\",\"version\":\"1.5\"}");
		jar("old.jar", "{\"id\":\"old\",\"version\":\"1.0\",\"depends\":{\"minecraft\":\"1.20.x\"}}");
		jar("forge.jar", null);
		jar("dup1.jar", "{\"id\":\"dup\",\"version\":\"1\"}");
		jar("dup2.jar", "{\"id\":\"dup\",\"version\":\"2\"}");
		jar("breaker.jar", "{\"id\":\"breaker\",\"version\":\"1\",\"breaks\":{\"b\":\"<2\"}}");

		ModManager manager = new ModManager(mods, new ModrinthApi("http://localhost:1"), "1.21.11", "fabric");
		List<InstalledMod> installed = manager.installed();
		assertEquals(7, installed.size());
		List<ModProblem> problems = manager.analyze(installed);
		assertTrue(has(problems, ModProblem.Kind.DEPENDENCY_VERSION, "a.jar"), problems.toString());
		assertTrue(has(problems, ModProblem.Kind.MISSING_DEPENDENCY, "a.jar"), problems.toString());
		assertTrue(has(problems, ModProblem.Kind.INCOMPATIBLE_GAME_VERSION, "old.jar"), problems.toString());
		assertTrue(has(problems, ModProblem.Kind.WRONG_LOADER, "forge.jar"), problems.toString());
		assertTrue(has(problems, ModProblem.Kind.DUPLICATE, "dup1.jar"), problems.toString());
		assertTrue(has(problems, ModProblem.Kind.BREAKS, "breaker.jar"), problems.toString());
		assertFalse(has(problems, ModProblem.Kind.INCOMPATIBLE_GAME_VERSION, "a.jar"), problems.toString());
	}

	@Test
	void disableAndRemove() throws IOException {
		jar("x.jar", "{\"id\":\"x\",\"version\":\"1\"}");
		ModManager manager = new ModManager(mods, new ModrinthApi("http://localhost:1"), "1.21.11", "fabric");
		InstalledMod mod = manager.installed().getFirst();
		manager.setEnabled(mod, false);
		assertTrue(Files.exists(mods.resolve("x.jar.disabled")));
		InstalledMod disabled = manager.installed().getFirst();
		assertFalse(disabled.enabled());
		manager.remove(disabled);
		assertTrue(manager.installed().isEmpty());
	}

	private static boolean has(List<ModProblem> problems, ModProblem.Kind kind, String file) {
		return problems.stream().anyMatch(p -> p.kind() == kind && file.equals(p.fileName()));
	}
}
