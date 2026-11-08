package dev.vantage.launcher.game;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

/**
 * Data model of Mojang's launcher metadata (version manifest, version JSON, asset index) and Fabric's profile
 * JSON, which uses the same format plus maven-style libraries.
 */
public final class MojangModels {
	private MojangModels() {
	}

	public record Manifest(Latest latest, List<ManifestEntry> versions) {
	}

	public record Latest(String release, String snapshot) {
	}

	public record ManifestEntry(String id, String type, String url, String time, String releaseTime, String sha1) {
	}

	public record Artifact(String path, String sha1, long size, String url) {
	}

	public record LibraryDownloads(Artifact artifact, Map<String, Artifact> classifiers) {
	}

	public record OsRule(String name, String arch, String version) {
	}

	public record Rule(String action, OsRule os, Map<String, Boolean> features) {
	}

	public record Extract(List<String> exclude) {
	}

	/**
	 * A library entry. Mojang libraries carry {@code downloads}; Fabric libraries carry a maven {@code name}
	 * plus repository {@code url} (and optionally {@code sha1}/{@code size}).
	 */
	public record Library(
		String name,
		LibraryDownloads downloads,
		List<Rule> rules,
		Map<String, String> natives,
		Extract extract,
		String url,
		String sha1,
		long size
	) {
	}

	public record AssetIndexRef(String id, String sha1, long size, long totalSize, String url) {
	}

	public record Download(String sha1, long size, String url) {
	}

	public record Downloads(Download client, Download server) {
	}

	public record JavaVersion(String component, int majorVersion) {
	}

	public record LoggingFile(String id, String sha1, long size, String url) {
	}

	public record LoggingConfig(String argument, LoggingFile file, String type) {
	}

	public record Logging(LoggingConfig client) {
	}

	public record Arguments(List<JsonElement> game, List<JsonElement> jvm) {
	}

	/** A (possibly partial, inheriting) version JSON. */
	public record VersionJson(
		String id,
		String inheritsFrom,
		String type,
		String mainClass,
		String assets,
		AssetIndexRef assetIndex,
		Downloads downloads,
		JavaVersion javaVersion,
		List<Library> libraries,
		Arguments arguments,
		String minecraftArguments,
		Logging logging,
		String releaseTime
	) {
	}

	public record AssetObject(String hash, long size) {
	}

	public record AssetIndex(Map<String, AssetObject> objects, boolean virtual, boolean mapToResources) {
	}
}
