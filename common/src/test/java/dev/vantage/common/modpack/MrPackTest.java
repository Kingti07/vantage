package dev.vantage.common.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MrPackTest {
	private static final String FILES = """
		[
		  {"path": "mods/sodium.jar", "hashes": {"sha1": "aa", "sha512": "bb"}, "env": {"client": "required", "server": "unsupported"},
		   "downloads": ["https://cdn.modrinth.com/data/AANobbMI/versions/1/sodium.jar"], "fileSize": 100},
		  {"path": "mods/server-only.jar", "hashes": {"sha1": "cc"}, "env": {"client": "unsupported", "server": "required"},
		   "downloads": ["https://cdn.modrinth.com/data/x/versions/1/server-only.jar"], "fileSize": 50},
		  {"path": "shaderpacks/pretty.zip", "hashes": {"sha1": "dd"}, "env": {"client": "optional", "server": "unsupported"},
		   "downloads": ["https://github.com/someone/pretty/releases/download/1/pretty.zip"], "fileSize": 25}
		]""";

	@TempDir
	Path dir;

	private Path pack(String dependencies, String files, Map<String, String> extra) throws IOException {
		Path file = dir.resolve("pack.mrpack");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
			put(zip, MrPack.INDEX, """
				{"formatVersion": 1, "game": "minecraft", "versionId": "1.2.0", "name": "Test Pack", "summary": "For tests",
				 "files": %s, "dependencies": %s}""".formatted(files, dependencies));
			for (Map.Entry<String, String> entry : extra.entrySet()) {
				put(zip, entry.getKey(), entry.getValue());
			}
		}
		return file;
	}

	private static void put(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		OutputStream out = zip;
		out.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	@Test
	void readsTheIndex() throws IOException {
		MrPack pack = MrPack.open(pack("{\"minecraft\": \"1.21.11\", \"fabric-loader\": \"0.18.1\"}", FILES, Map.of()));
		assertEquals("Test Pack", pack.name());
		assertEquals("1.2.0", pack.versionId());
		assertEquals("1.21.11", pack.minecraftVersion());
		assertEquals(MrPack.Loader.FABRIC, pack.loader());
		assertEquals("0.18.1", pack.loaderVersion());
		assertTrue(pack.isSupported());
	}

	@Test
	void skipsServerOnlyFilesAndOptionalOnesOnRequest() throws IOException {
		MrPack pack = MrPack.open(pack("{\"minecraft\": \"1.21.11\", \"fabric-loader\": \"0.18.1\"}", FILES, Map.of()));
		assertEquals(1, pack.clientFiles(false).size());
		assertEquals(2, pack.clientFiles(true).size());
		assertEquals(100, pack.downloadSize(false));
		assertEquals(125, pack.downloadSize(true));
		assertEquals("pretty.zip", pack.clientFiles(true).get(1).fileName());
	}

	@Test
	void recognisesOtherLoaders() throws IOException {
		assertEquals(MrPack.Loader.NEOFORGE, MrPack.open(pack("{\"minecraft\": \"1.21.1\", \"neoforge\": \"21.1.1\"}", "[]", Map.of())).loader());
		assertEquals(MrPack.Loader.FORGE, MrPack.open(pack("{\"minecraft\": \"1.20.1\", \"forge\": \"47.2.0\"}", "[]", Map.of())).loader());
		MrPack quilt = MrPack.open(pack("{\"minecraft\": \"1.20.1\", \"quilt-loader\": \"0.26.0\"}", "[]", Map.of()));
		assertEquals(MrPack.Loader.QUILT, quilt.loader());
		assertFalse(quilt.isSupported());
		MrPack vanilla = MrPack.open(pack("{\"minecraft\": \"1.21.11\"}", "[]", Map.of()));
		assertEquals(MrPack.Loader.VANILLA, vanilla.loader());
		assertTrue(vanilla.isSupported());
	}

	@Test
	void rejectsBrokenPacks() throws IOException {
		assertThrows(IOException.class, () -> MrPack.open(pack("{\"fabric-loader\": \"0.18.1\"}", "[]", Map.of())));
		Path notAZip = Files.writeString(dir.resolve("broken.mrpack"), "not a zip");
		assertThrows(IOException.class, () -> MrPack.open(notAZip));
		Path noIndex = dir.resolve("empty.mrpack");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(noIndex))) {
			put(zip, "readme.txt", "hello");
		}
		assertThrows(IOException.class, () -> MrPack.open(noIndex));
	}

	@Test
	void onlyAllowsModrinthApprovedHosts() throws IOException {
		assertTrue(MrPack.isAllowedDownload("https://cdn.modrinth.com/data/a/versions/b/c.jar"));
		assertTrue(MrPack.isAllowedDownload("https://github.com/a/b/releases/download/1/c.jar"));
		assertTrue(MrPack.isAllowedDownload("https://raw.githubusercontent.com/a/b/main/c.zip"));
		assertFalse(MrPack.isAllowedDownload("http://cdn.modrinth.com/data/a/c.jar"));
		assertFalse(MrPack.isAllowedDownload("https://evil.example.com/c.jar"));
		assertFalse(MrPack.isAllowedDownload("https://cdn.modrinth.com.evil.example/c.jar"));
		assertFalse(MrPack.isAllowedDownload("not a url"));
		MrPack.PackFile foreign = new MrPack.PackFile("mods/x.jar", Map.of("sha1", "aa"), null, java.util.List.of("https://evil.example.com/x.jar"), 1);
		assertThrows(IOException.class, () -> MrPack.downloadUri(foreign));
	}

	@Test
	void refusesPathsOutsideTheGameDirectory() {
		Path gameDir = dir.resolve("game");
		MrPack.PackFile escape = new MrPack.PackFile("../../evil.dll", Map.of("sha1", "aa"), null, java.util.List.of(), 1);
		assertThrows(IOException.class, () -> MrPack.target(gameDir, escape));
		MrPack.PackFile backslash = new MrPack.PackFile("mods\\..\\..\\evil.dll", Map.of("sha1", "aa"), null, java.util.List.of(), 1);
		assertThrows(IOException.class, () -> MrPack.target(gameDir, backslash));
	}

	@Test
	void copiesOverridesWithClientOverridesWinning() throws IOException {
		Map<String, String> extra = new LinkedHashMap<>();
		extra.put("overrides/config/sodium-options.json", "{\"quality\": \"fast\"}");
		extra.put("overrides/options.txt", "guiScale:2");
		extra.put("client-overrides/options.txt", "guiScale:3");
		extra.put("server-overrides/server.properties", "motd=no");
		MrPack pack = MrPack.open(pack("{\"minecraft\": \"1.21.11\"}", "[]", extra));
		assertEquals(3, pack.overrideCount());
		Path gameDir = dir.resolve("game");
		assertEquals(3, pack.extractOverrides(gameDir));
		assertEquals("guiScale:3", Files.readString(gameDir.resolve("options.txt")));
		assertTrue(Files.isRegularFile(gameDir.resolve("config/sodium-options.json")));
		assertFalse(Files.exists(gameDir.resolve("server.properties")));
	}

	@Test
	void refusesOverridesThatEscape() throws IOException {
		MrPack pack = MrPack.open(pack("{\"minecraft\": \"1.21.11\"}", "[]", Map.of("overrides/../../evil.txt", "x")));
		assertThrows(IOException.class, () -> pack.extractOverrides(dir.resolve("game")));
		assertFalse(Files.exists(dir.resolve("evil.txt")));
	}
}
