package dev.vantage.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.Json;
import dev.vantage.launcher.LauncherPaths;
import dev.vantage.launcher.game.MojangModels.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Version lists from Mojang (game versions) and Fabric (loader versions). Results are cached on disk so the
 * launcher keeps working offline for versions that were installed before.
 */
public final class GameMeta {
	public static final URI MOJANG_MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
	public static final String FABRIC_META = "https://meta.fabricmc.net/v2";
	private static final Logger LOGGER = LoggerFactory.getLogger(GameMeta.class);

	public record LoaderVersion(String version, boolean stable) {
	}

	private final LauncherPaths paths;
	private volatile Manifest manifest;

	public GameMeta(LauncherPaths paths) {
		this.paths = paths;
	}

	/** The Mojang version manifest; falls back to the cached copy when offline. */
	public Manifest manifest() throws IOException {
		Manifest cached = manifest;
		if (cached != null) {
			return cached;
		}
		Path cacheFile = paths.versions().resolve("version_manifest_v2.json");
		try {
			String body = Http.getString(MOJANG_MANIFEST);
			FileUtil.writeAtomically(cacheFile, body.getBytes(StandardCharsets.UTF_8));
			manifest = Json.GSON.fromJson(body, Manifest.class);
		} catch (IOException e) {
			if (!Files.isRegularFile(cacheFile)) {
				throw new IOException("Could not download the Minecraft version list: " + e.getMessage(), e);
			}
			LOGGER.warn("Using cached version manifest (offline?): {}", e.getMessage());
			manifest = Json.read(cacheFile, Manifest.class);
		}
		return manifest;
	}

	public MojangModels.ManifestEntry entry(String versionId) throws IOException {
		return manifest().versions().stream().filter(v -> v.id().equals(versionId)).findFirst()
			.orElseThrow(() -> new IOException("Unknown Minecraft version " + versionId));
	}

	/** Fabric loader versions that support the given game version, newest first. */
	public List<LoaderVersion> loaderVersions(String gameVersion) throws IOException {
		Path cacheFile = paths.cache().resolve("fabric-loaders-" + gameVersion + ".json");
		String body;
		try {
			body = Http.getString(URI.create(FABRIC_META + "/versions/loader/" + Http.encode(gameVersion)));
			FileUtil.writeAtomically(cacheFile, body.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			if (!Files.isRegularFile(cacheFile)) {
				throw new IOException("Could not load Fabric versions: " + e.getMessage(), e);
			}
			body = Files.readString(cacheFile);
		}
		List<LoaderVersion> result = new ArrayList<>();
		JsonArray array = JsonParser.parseString(body).getAsJsonArray();
		for (JsonElement element : array) {
			var loader = element.getAsJsonObject().getAsJsonObject("loader");
			result.add(new LoaderVersion(loader.get("version").getAsString(), loader.has("stable") && loader.get("stable").getAsBoolean()));
		}
		return result;
	}

	/** Newest stable loader for the game version (falls back to newest of any kind). */
	public String latestLoader(String gameVersion) throws IOException {
		List<LoaderVersion> versions = loaderVersions(gameVersion);
		if (versions.isEmpty()) {
			throw new IOException("Fabric does not support Minecraft " + gameVersion + " (yet).");
		}
		return versions.stream().filter(LoaderVersion::stable).findFirst().orElse(versions.getFirst()).version();
	}

	public static String fabricVersionId(String gameVersion, String loaderVersion) {
		return "fabric-loader-" + loaderVersion + "-" + gameVersion;
	}

	public static URI fabricProfileUri(String gameVersion, String loaderVersion) {
		return URI.create(FABRIC_META + "/versions/loader/" + Http.encode(gameVersion) + "/" + Http.encode(loaderVersion) + "/profile/json");
	}
}
