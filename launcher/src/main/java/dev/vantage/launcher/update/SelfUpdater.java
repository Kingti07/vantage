package dev.vantage.launcher.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vantage.common.VantageInfo;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.Json;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.common.version.SemanticVersion;
import dev.vantage.launcher.LauncherPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Self-update through GitHub Releases.
 * <ol>
 *   <li>{@link #check} finds the newest release (pre-releases included) that is newer than the running launcher,
 *       has a jar for this platform and publishes a SHA-256 checksum for it (GitHub's asset digest or a
 *       {@code SHA256SUMS.txt} asset). Releases without a checksum are never offered.</li>
 *   <li>{@link #download} stores the jar in {@code updates/}, verifies the checksum and records it in
 *       {@code updates/staged.json}.</li>
 *   <li>On the next start {@code Main} hands over to the staged jar, but only if its checksum still matches. If the
 *       new version does not come up, it is recorded as rejected and the current version keeps running.</li>
 * </ol>
 * Release assets must be named {@code vantage-launcher-<version>-all-<platform>.jar}, which is what
 * {@code ./gradlew :launcher:fatJar} produces. The bundled in-game client is inside that jar, so one update
 * covers both.
 */
public final class SelfUpdater {
	private static final Logger LOGGER = LoggerFactory.getLogger(SelfUpdater.class);
	private static final Pattern REPOSITORY = Pattern.compile("[\\w.-]+/[\\w.-]+");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
	static final String STAGED_FILE = "staged.json";
	static final String REJECTED_FILE = "rejected.json";
	private static final String CHECKSUMS_ASSET = "SHA256SUMS.txt";

	/**
	 * A release that can be installed.
	 *
	 * @param sha256       expected SHA-256 of the asset; null until {@link #check} resolved it
	 * @param checksumsUrl URL of the release's {@code SHA256SUMS.txt}, or null
	 */
	public record Release(String version, String name, String notes, String assetName, String assetUrl, long size,
		String sha256, String checksumsUrl) {
		Release withSha256(String hash) {
			return new Release(version, name, notes, assetName, assetUrl, size, hash, checksumsUrl);
		}
	}

	/** A downloaded, verified update waiting in {@code updates/}. */
	public record StagedUpdate(String version, Path jar, String sha256) {
	}

	private record StagedRecord(String version, String file, String sha256) {
	}

	private final LauncherPaths paths;

	public SelfUpdater(LauncherPaths paths) {
		this.paths = paths;
	}

	/** Platform part of the release asset name: win, mac, mac-aarch64, linux or linux-aarch64. */
	public static String currentPlatform() {
		boolean arm = dev.vantage.common.util.OperatingSystem.isArm64();
		return switch (dev.vantage.common.util.OperatingSystem.current()) {
			case WINDOWS -> "win";
			case MACOS -> arm ? "mac-aarch64" : "mac";
			case LINUX -> arm ? "linux-aarch64" : "linux";
		};
	}

	public static boolean isOfficialRepository(String repository) {
		return VantageInfo.DEFAULT_UPDATE_REPOSITORY.equalsIgnoreCase(repository);
	}

	/**
	 * Returns the newest verifiable release that is newer than the running version, or empty if there is none.
	 * GitHub's "latest release" endpoint is not used because it ignores pre-releases.
	 */
	public Optional<Release> check(String repository, String platform) throws IOException {
		if (repository == null || !REPOSITORY.matcher(repository).matches()) {
			throw new IOException("No update repository configured (expected \"owner/repo\").");
		}
		String body = Http.getString(URI.create("https://api.github.com/repos/" + repository + "/releases?per_page=30"));
		Optional<Release> candidate = newest(JsonParser.parseString(body).getAsJsonArray(), VantageInfo.VERSION, platform);
		if (candidate.isEmpty()) {
			return Optional.empty();
		}
		Release release = candidate.get();
		if (release.sha256() == null && release.checksumsUrl() != null) {
			String sums = Http.getString(URI.create(release.checksumsUrl()));
			release = release.withSha256(checksumFor(sums, release.assetName()));
		}
		if (release.sha256() == null) {
			LOGGER.warn("Vantage {} publishes no checksum for {}; not offering it", release.version(), release.assetName());
			return Optional.empty();
		}
		if (rejected(paths).contains(release.sha256().toLowerCase(Locale.ROOT))) {
			LOGGER.info("Vantage {} failed to start before; not offering it again", release.version());
			return Optional.empty();
		}
		return Optional.of(release);
	}

	/** Picks the update from a GitHub release list; package-private for tests. */
	static Optional<Release> newest(JsonArray releases, String currentVersion, String platform) {
		SemanticVersion current = SemanticVersion.tryParse(currentVersion);
		if (current == null) {
			return Optional.empty();
		}
		String suffix = "-all-" + platform + ".jar";
		Release best = null;
		SemanticVersion bestVersion = null;
		for (JsonElement element : releases) {
			JsonObject release = element.getAsJsonObject();
			if (release.has("draft") && release.get("draft").getAsBoolean()) {
				continue;
			}
			String tag = release.get("tag_name").getAsString();
			SemanticVersion version = SemanticVersion.tryParse(tag);
			if (version == null || version.compareTo(current) <= 0 || bestVersion != null && version.compareTo(bestVersion) <= 0) {
				continue;
			}
			JsonObject asset = null;
			String checksumsUrl = null;
			for (JsonElement assetElement : release.getAsJsonArray("assets")) {
				JsonObject candidate = assetElement.getAsJsonObject();
				String name = candidate.get("name").getAsString();
				if (name.endsWith(suffix)) {
					asset = candidate;
				} else if (CHECKSUMS_ASSET.equals(name)) {
					checksumsUrl = candidate.get("browser_download_url").getAsString();
				}
			}
			if (asset == null) {
				LOGGER.info("Release {} has no asset for platform {}", tag, platform);
				continue;
			}
			String plain = tag.length() > 1 && (tag.charAt(0) == 'v' || tag.charAt(0) == 'V') ? tag.substring(1) : tag;
			best = new Release(plain,
				string(release, "name", tag),
				string(release, "body", ""),
				asset.get("name").getAsString(),
				asset.get("browser_download_url").getAsString(),
				asset.get("size").getAsLong(),
				digestOf(asset),
				checksumsUrl);
			bestVersion = version;
		}
		return Optional.ofNullable(best);
	}

	/** GitHub publishes {@code "digest": "sha256:<hex>"} for every release asset uploaded since mid-2025. */
	static String digestOf(JsonObject asset) {
		String digest = string(asset, "digest", "");
		if (digest.startsWith("sha256:") && SHA256.matcher(digest.substring(7)).matches()) {
			return digest.substring(7).toLowerCase(Locale.ROOT);
		}
		return null;
	}

	/** Finds the checksum of {@code fileName} in {@code sha256sum} output ("<hex>  <name>" or "<hex> *<name>"). */
	static String checksumFor(String sums, String fileName) {
		for (String line : sums.split("\\R")) {
			String[] parts = line.trim().split("\\s+", 2);
			if (parts.length == 2 && SHA256.matcher(parts[0]).matches()) {
				String name = parts[1].startsWith("*") ? parts[1].substring(1) : parts[1];
				if (name.equals(fileName)) {
					return parts[0].toLowerCase(Locale.ROOT);
				}
			}
		}
		return null;
	}

	/** Downloads and verifies the release and stages it for the next start. */
	public Path download(Release release, String platform, ProgressListener listener) throws IOException {
		if (release.sha256() == null) {
			throw new IOException("Vantage " + release.version() + " cannot be verified, so it is not installed.");
		}
		Path target = paths.updates().resolve("vantage-launcher-" + release.version() + "-all-" + platform + ".jar");
		AtomicLong done = new AtomicLong();
		listener.status("Downloading Vantage " + release.version());
		Http.download(URI.create(release.assetUrl()), target, "SHA-256", release.sha256(), bytes -> {
			long now = done.addAndGet(bytes);
			listener.progress(now / (double) Math.max(1, release.size()));
		}, listener::isCancelled);
		Json.write(paths.updates().resolve(STAGED_FILE), new StagedRecord(release.version(), target.getFileName().toString(),
			release.sha256().toLowerCase(Locale.ROOT)));
		listener.status("Update " + release.version() + " is ready – restart the launcher to apply it.");
		return target;
	}

	/**
	 * The staged update to start instead of this version, if there is one that is newer, was not rejected and
	 * still has the checksum it was downloaded with.
	 */
	public static Optional<StagedUpdate> findStagedUpdate(LauncherPaths paths) {
		Path recordFile = paths.updates().resolve(STAGED_FILE);
		if (!Files.isRegularFile(recordFile)) {
			return Optional.empty();
		}
		try {
			StagedRecord record = Json.read(recordFile, StagedRecord.class);
			if (record == null || record.file() == null || record.sha256() == null || record.version() == null) {
				return Optional.empty();
			}
			SemanticVersion staged = SemanticVersion.tryParse(record.version());
			SemanticVersion current = SemanticVersion.tryParse(VantageInfo.VERSION);
			Path jar = paths.updates().resolve(record.file()).normalize();
			if (staged == null || current == null || staged.compareTo(current) <= 0 || !jar.startsWith(paths.updates())
				|| !Files.isRegularFile(jar) || rejected(paths).contains(record.sha256())) {
				return Optional.empty();
			}
			if (!HashUtil.sha256(jar).equalsIgnoreCase(record.sha256())) {
				LOGGER.warn("Staged update {} does not match its checksum; ignoring it", jar);
				return Optional.empty();
			}
			return Optional.of(new StagedUpdate(record.version(), jar, record.sha256()));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read the staged update", e);
			return Optional.empty();
		}
	}

	/** Remembers that an update did not start, so it is neither started nor offered again. */
	public static void reject(LauncherPaths paths, StagedUpdate update) {
		try {
			List<String> list = new ArrayList<>(rejected(paths));
			list.add(update.sha256().toLowerCase(Locale.ROOT));
			Json.write(paths.updates().resolve(REJECTED_FILE), list);
			Files.deleteIfExists(paths.updates().resolve(STAGED_FILE));
		} catch (IOException e) {
			LOGGER.error("Could not record the failed update", e);
		}
	}

	/** Forgets the staged update once it has been installed in place of the old launcher jar. */
	public static void clearStaged(LauncherPaths paths) {
		try {
			Files.deleteIfExists(paths.updates().resolve(STAGED_FILE));
		} catch (IOException e) {
			LOGGER.warn("Could not clear the staged update", e);
		}
	}

	static List<String> rejected(LauncherPaths paths) {
		Path file = paths.updates().resolve(REJECTED_FILE);
		if (!Files.isRegularFile(file)) {
			return List.of();
		}
		try {
			String[] list = Json.read(file, String[].class);
			return list == null ? List.of() : List.of(list);
		} catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * Deletes launcher jars in {@code updates/} that are not the staged update (already installed, older or
	 * rejected) and partial downloads older than a week. Runs in the background after start-up.
	 */
	public static void cleanUp(LauncherPaths paths) {
		if (!Files.isDirectory(paths.updates())) {
			return;
		}
		Optional<StagedUpdate> staged = findStagedUpdate(paths);
		if (staged.isEmpty()) {
			clearStaged(paths);
		}
		long weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
		try (Stream<Path> files = Files.list(paths.updates())) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				boolean oldJar = name.startsWith("vantage-launcher-") && name.endsWith(".jar")
					&& staged.map(s -> !s.jar().equals(file)).orElse(true);
				boolean stalePart = name.endsWith(".part") && Files.getLastModifiedTime(file).toMillis() < weekAgo;
				if (oldJar || stalePart) {
					try {
						Files.deleteIfExists(file);
					} catch (IOException e) {
						// Still in use (e.g. this very launcher runs from it); try again next time.
						LOGGER.debug("Could not delete {} yet", file);
					}
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Could not clean up the updates folder", e);
		}
	}

	/** The jar this launcher runs from, or null when it runs from a class folder (development). */
	public static Path runningJar() {
		try {
			Path location = Path.of(SelfUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			return Files.isRegularFile(location) && location.toString().endsWith(".jar") ? location : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Copies the running (updated) jar over the jar the user starts, so the next start needs no hand-over. Waits for
	 * the old launcher process to exit first because Windows keeps running jars locked.
	 */
	public static void replaceOldJar(LauncherPaths paths, Path oldJar) {
		Path self = runningJar();
		if (self == null || oldJar == null || self.equals(oldJar)) {
			return;
		}
		ProcessHandle.current().parent().ifPresent(parent -> parent.onExit().join());
		try {
			Path temp = Files.createTempFile(oldJar.toAbsolutePath().getParent(), "vantage-update", ".tmp");
			try {
				Files.copy(self, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				FileUtil.moveReplacing(temp, oldJar);
			} finally {
				Files.deleteIfExists(temp);
			}
			clearStaged(paths);
			LOGGER.info("Installed Vantage {} in place of {}", VantageInfo.VERSION, oldJar);
		} catch (IOException e) {
			LOGGER.warn("Could not replace {} (the update stays staged and starts via hand-over): {}", oldJar, e.toString());
		}
	}

	private static String string(JsonObject object, String key, String fallback) {
		JsonElement element = object.get(key);
		return element != null && !element.isJsonNull() ? element.getAsString() : fallback;
	}
}
