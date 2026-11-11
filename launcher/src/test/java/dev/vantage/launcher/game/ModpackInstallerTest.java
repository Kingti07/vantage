package dev.vantage.launcher.game;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.modpack.MrPack;
import dev.vantage.common.modrinth.model.ModrinthModels.Hashes;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.modrinth.model.ModrinthModels.VersionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModpackInstallerTest {
	@TempDir
	Path dir;

	private MrPack pack(String dependencies) throws IOException {
		Path file = dir.resolve("pack-" + System.nanoTime() + ".mrpack");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
			zip.putNextEntry(new ZipEntry(MrPack.INDEX));
			zip.write(("{\"formatVersion\": 1, \"game\": \"minecraft\", \"versionId\": \"1\", \"name\": \"P\", \"files\": [], \"dependencies\": "
				+ dependencies + "}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return MrPack.open(file);
	}

	@Test
	void keepsThePacksLoaderWhenItIsNewEnough() throws IOException {
		MrPack pack = pack("{\"minecraft\": \"1.21.11\", \"fabric-loader\": \"0.25.0\"}");
		assertEquals("0.25.0", ModpackInstaller.loaderVersion(pack, true));
		assertEquals("0.25.0", ModpackInstaller.loaderVersion(pack, false));
	}

	@Test
	void raisesAnOldLoaderOnlyForTheVantageClient() throws IOException {
		MrPack pack = pack("{\"minecraft\": \"1.21.11\", \"fabric-loader\": \"0.15.0\"}");
		assertEquals(VantageInfo.CLIENT_MIN_LOADER_VERSION, ModpackInstaller.loaderVersion(pack, true));
		assertEquals("0.15.0", ModpackInstaller.loaderVersion(pack, false));
	}

	@Test
	void vanillaPacksUseTheNewestLoader() throws IOException {
		assertEquals("", ModpackInstaller.loaderVersion(pack("{\"minecraft\": \"1.21.11\"}"), true));
	}

	@Test
	void picksTheMrpackFileOfAVersion() throws IOException {
		VersionFile jar = new VersionFile(new Hashes("a", "b"), "https://cdn.modrinth.com/x.jar", "x.jar", true, 1, null);
		VersionFile pack = new VersionFile(new Hashes("c", "d"), "https://cdn.modrinth.com/p.mrpack", "p.mrpack", false, 1, null);
		Version version = new Version("v", "p", null, "1", "1", null, List.of(), List.of("1.21.11"), "release", List.of("fabric"), false,
			"listed", null, 0, List.of(jar, pack));
		assertEquals("p.mrpack", ModpackInstaller.packFile(version).filename());
		Version none = new Version("v", "p", null, "1", "1", null, List.of(), List.of("1.21.11"), "release", List.of("fabric"), false,
			"listed", null, 0, List.of(jar));
		assertThrows(IOException.class, () -> ModpackInstaller.packFile(none));
	}
}
