package dev.vantage.launcher.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vantage.common.VantageInfo;
import dev.vantage.common.util.Http;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Self-update through GitHub Releases. The newest release whose tag is a higher version than the running
 * launcher is downloaded into {@code updates/}; {@link #findStagedUpdate} lets {@code Main} start that jar
 * instead of the current one on the next start-up, so no running file ever needs to be replaced.
 * <p>
 * Release assets must be named {@code vantage-launcher-<version>-all-<platform>.jar}, which is what
 * {@code ./gradlew :launcher:fatJar} produces. The bundled in-game client is inside that jar, so one update
 * covers both.
 */
public final class SelfUpdater {
	private static final Logger LOGGER = LoggerFactory.getLogger(SelfUpdater.class);

	public record Release(String version, String name, String notes, String assetUrl, long size) {
	}

	private final LauncherPaths paths;

	public SelfUpdater(LauncherPaths paths) {
		this.paths = paths;
	}

	/**
	 * Returns the newest published release (pre-releases included, drafts excluded) that is newer than the
	 * running version and has a launcher jar for this platform. GitHub's "latest release" endpoint is not used
	 * because it ignores pre-releases.
	 */
	public Optional<Release> check(String repository, String platform) throws IOException {
		if (repository == null || !repository.matches("[\\w.-]+/[\\w.-]+")) {
			throw new IOException("No update repository configured (expected \"owner/repo\").");
		}
		String body = Http.getString(URI.create("https://api.github.com/repos/" + repository + "/releases?per_page=30"));
		return newest(JsonParser.parseString(body).getAsJsonArray(), VantageInfo.VERSION, platform);
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
			if (version == null || version.compareTo(current) <= 0 || (bestVersion != null && version.compareTo(bestVersion) <= 0)) {
				continue;
			}
			for (JsonElement assetElement : release.getAsJsonArray("assets")) {
				JsonObject asset = assetElement.getAsJsonObject();
				if (asset.get("name").getAsString().endsWith(suffix)) {
					String plain = tag.length() > 1 && (tag.charAt(0) == 'v' || tag.charAt(0) == 'V') ? tag.substring(1) : tag;
					best = new Release(plain,
						release.has("name") && !release.get("name").isJsonNull() ? release.get("name").getAsString() : tag,
						release.has("body") && !release.get("body").isJsonNull() ? release.get("body").getAsString() : "",
						asset.get("browser_download_url").getAsString(), asset.get("size").getAsLong());
					bestVersion = version;
					break;
				}
			}
			if (bestVersion != version) {
				LOGGER.info("Release {} has no asset for platform {}", tag, platform);
			}
		}
		return Optional.ofNullable(best);
	}

	public Path download(Release release, String platform, ProgressListener listener) throws IOException {
		Path target = paths.updates().resolve("vantage-launcher-" + release.version() + "-all-" + platform + ".jar");
		AtomicLong done = new AtomicLong();
		listener.status("Downloading Vantage " + release.version());
		Http.download(URI.create(release.assetUrl()), target, null, null,
			bytes -> listener.progress(done.addAndGet(bytes) / (double) Math.max(1, release.size())));
		listener.status("Update " + release.version() + " is ready – restart the launcher to apply it.");
		return target;
	}

	/** Newest staged launcher jar that is newer than the running version, if any. */
	public static Optional<Path> findStagedUpdate(LauncherPaths paths) {
		if (!Files.isDirectory(paths.updates())) {
			return Optional.empty();
		}
		SemanticVersion current = SemanticVersion.tryParse(VantageInfo.VERSION);
		List<Path> candidates = new ArrayList<>();
		try (Stream<Path> files = Files.list(paths.updates())) {
			files.filter(p -> p.getFileName().toString().startsWith("vantage-launcher-") && p.toString().endsWith(".jar"))
				.forEach(candidates::add);
		} catch (IOException e) {
			return Optional.empty();
		}
		return candidates.stream()
			.filter(p -> {
				SemanticVersion v = versionOf(p);
				return v != null && current != null && v.compareTo(current) > 0;
			})
			.max(Comparator.comparing(SelfUpdater::versionOf));
	}

	private static SemanticVersion versionOf(Path jar) {
		String name = jar.getFileName().toString();
		String rest = name.substring("vantage-launcher-".length());
		int end = rest.indexOf("-all-");
		return SemanticVersion.tryParse(end > 0 ? rest.substring(0, end) : rest);
	}
}
