package dev.vantage.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilTest {
	@TempDir
	Path dir;

	@Test
	void resolvesPathsInsideTheBase() throws IOException {
		assertEquals(dir.resolve("org/lwjgl/lwjgl.jar").toAbsolutePath().normalize(),
			FileUtil.resolveInside(dir, "org/lwjgl/lwjgl.jar"));
		assertEquals(dir.resolve("a/c.txt").toAbsolutePath().normalize(), FileUtil.resolveInside(dir, "a/b/../c.txt"));
	}

	@Test
	void refusesPathsThatEscape() {
		assertThrows(IOException.class, () -> FileUtil.resolveInside(dir, "../evil.dll"));
		assertThrows(IOException.class, () -> FileUtil.resolveInside(dir, "a/../../evil.dll"));
		assertThrows(IOException.class, () -> FileUtil.resolveInside(dir, dir.getRoot().resolve("Windows").toString()));
		assertThrows(IOException.class, () -> FileUtil.resolveInside(dir, "."));
	}

	@Test
	void movesDamagedFilesAsideWithoutOverwriting() throws IOException {
		Path file = Files.writeString(dir.resolve("instances.json"), "one");
		Path first = FileUtil.moveAside(file);
		Files.writeString(file, "two");
		Path second = FileUtil.moveAside(file);
		assertFalse(Files.exists(file));
		assertTrue(first.getFileName().toString().startsWith("instances.broken-"));
		assertEquals("one", Files.readString(first));
		assertEquals("two", Files.readString(second));
	}
}
