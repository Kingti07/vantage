package dev.vantage.launcher.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.vantage.common.VantageInfo;
import dev.vantage.common.util.HashUtil;
import dev.vantage.launcher.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfUpdaterTest {
	private static final String HASH = "a".repeat(64);

	@TempDir
	Path dir;

	private static JsonArray releases(String json) {
		return JsonParser.parseString(json).getAsJsonArray();
	}

	private static String asset(String name, String digest) {
		return "{\"name\":\"" + name + "\",\"browser_download_url\":\"https://example.com/" + name + "\",\"size\":1"
			+ (digest != null ? ",\"digest\":\"" + digest + "\"" : "") + "}";
	}

	private static String release(String tag, boolean draft, String... assets) {
		return "{\"tag_name\":\"" + tag + "\",\"name\":\"" + tag + "\",\"body\":\"\",\"draft\":" + draft + ",\"prerelease\":true"
			+ ",\"assets\":[" + String.join(",", assets) + "]}";
	}

	@Test
	void picksNewestIncludingPreReleases() {
		JsonArray list = releases("[" + release("v0.3.0", true, asset("vantage-launcher-0.3.0-all-win.jar", null)) + ","
			+ release("v0.2.1", false, asset("vantage-launcher-0.2.1-all-win.jar", "sha256:" + HASH)) + ","
			+ release("v0.2.0", false, asset("vantage-launcher-0.2.0-all-win.jar", null)) + ","
			+ release("v0.1.0", false, asset("vantage-launcher-0.1.0-all-win.jar", null)) + "]");
		Optional<SelfUpdater.Release> update = SelfUpdater.newest(list, "0.1.0", "win");
		assertTrue(update.isPresent());
		assertEquals("0.2.1", update.get().version());
		assertEquals(HASH, update.get().sha256(), "GitHub's asset digest is used as checksum");
	}

	@Test
	void ignoresOlderSameAndReleasesWithoutPlatformAsset() {
		JsonArray list = releases("[" + release("v0.2.0", false, asset("vantage-launcher-0.2.0-all-linux.jar", null)) + ","
			+ release("v0.1.0", false, asset("vantage-launcher-0.1.0-all-win.jar", null)) + "]");
		assertTrue(SelfUpdater.newest(list, "0.1.0", "win").isEmpty());
	}

	@Test
	void remembersChecksumFileWhenThereIsNoDigest() {
		JsonArray list = releases("[" + release("v0.2.0", false, asset("vantage-launcher-0.2.0-all-win.jar", null),
			asset("SHA256SUMS.txt", null)) + "]");
		SelfUpdater.Release release = SelfUpdater.newest(list, "0.1.0", "win").orElseThrow();
		assertNull(release.sha256());
		assertEquals("https://example.com/SHA256SUMS.txt", release.checksumsUrl());
	}

	@Test
	void readsChecksumFiles() {
		String sums = HASH + "  vantage-launcher-0.2.0-all-linux.jar\n" + "B".repeat(64) + " *vantage-launcher-0.2.0-all-win.jar\n";
		assertEquals("b".repeat(64), SelfUpdater.checksumFor(sums, "vantage-launcher-0.2.0-all-win.jar"));
		assertNull(SelfUpdater.checksumFor(sums, "other.jar"));
	}

	@Test
	void rejectsMalformedDigests() {
		assertNull(SelfUpdater.digestOf(JsonParser.parseString("{\"digest\":\"sha256:xyz\"}").getAsJsonObject()));
		assertNull(SelfUpdater.digestOf(JsonParser.parseString("{\"digest\":\"md5:" + HASH + "\"}").getAsJsonObject()));
	}

	private LauncherPaths stage(String version, byte[] jar, String recordedHash) throws IOException {
		LauncherPaths paths = new LauncherPaths(dir);
		paths.createDirectories();
		Files.write(paths.updates().resolve("vantage-launcher-" + version + "-all-win.jar"), jar);
		Files.writeString(paths.updates().resolve(SelfUpdater.STAGED_FILE), "{\"version\":\"" + version + "\",\"file\":\"vantage-launcher-"
			+ version + "-all-win.jar\",\"sha256\":\"" + recordedHash + "\"}", StandardCharsets.UTF_8);
		return paths;
	}

	@Test
	void stagedUpdateIsStartedOnlyWithMatchingChecksum() throws IOException {
		byte[] jar = "pretend this is a jar".getBytes(StandardCharsets.UTF_8);
		Path probe = dir.resolve("probe");
		Files.write(probe, jar);
		String sha = HashUtil.sha256(probe);

		LauncherPaths paths = stage("99.0.0", jar, sha);
		assertTrue(SelfUpdater.findStagedUpdate(paths).isPresent());

		Files.write(paths.updates().resolve("vantage-launcher-99.0.0-all-win.jar"), "tampered".getBytes(StandardCharsets.UTF_8));
		assertTrue(SelfUpdater.findStagedUpdate(paths).isEmpty(), "a modified jar must not be started");
	}

	@Test
	void rejectedUpdateIsNotStartedAgain() throws IOException {
		byte[] jar = "jar".getBytes(StandardCharsets.UTF_8);
		Path probe = dir.resolve("probe");
		Files.write(probe, jar);
		LauncherPaths paths = stage("99.0.0", jar, HashUtil.sha256(probe));
		SelfUpdater.StagedUpdate update = SelfUpdater.findStagedUpdate(paths).orElseThrow();

		SelfUpdater.reject(paths, update);

		assertTrue(SelfUpdater.findStagedUpdate(paths).isEmpty());
		assertTrue(SelfUpdater.rejected(paths).contains(update.sha256()));
	}

	@Test
	void cleanUpRemovesInstalledAndOldJars() throws IOException {
		LauncherPaths paths = stage(VantageInfo.VERSION, new byte[]{1, 2, 3}, HASH);
		Path old = paths.updates().resolve("vantage-launcher-0.0.1-all-win.jar");
		Files.write(old, new byte[]{4});

		SelfUpdater.cleanUp(paths);

		assertFalse(Files.exists(old));
		assertFalse(Files.exists(paths.updates().resolve("vantage-launcher-" + VantageInfo.VERSION + "-all-win.jar")));
		assertFalse(Files.exists(paths.updates().resolve(SelfUpdater.STAGED_FILE)));
	}
}
