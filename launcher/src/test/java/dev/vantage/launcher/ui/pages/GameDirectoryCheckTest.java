package dev.vantage.launcher.ui.pages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameDirectoryCheckTest {
	@TempDir
	Path dir;

	@Test
	void acceptsDefaultExistingAndNewFolders() {
		assertNull(InstallationsPage.checkGameDirectory(""));
		assertNull(InstallationsPage.checkGameDirectory(dir.toString()));
		assertNull(InstallationsPage.checkGameDirectory(dir.resolve("new folder ä (1)").resolve("deeper").toString()));
	}

	@Test
	void rejectsFilesAndRelativePaths() throws IOException {
		Path file = Files.writeString(dir.resolve("file.txt"), "x");
		assertNotNull(InstallationsPage.checkGameDirectory(file.toString()));
		assertNotNull(InstallationsPage.checkGameDirectory("relative/folder"));
	}
}
