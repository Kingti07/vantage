package dev.vantage.common.mods;

import dev.vantage.common.modpack.MrPack;
import dev.vantage.common.modrinth.ModrinthApi;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.modrinth.model.ModrinthModels.VersionFile;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.ProgressListener;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Installs resource packs and shader packs from Modrinth into an installation's {@code resourcepacks/} or
 * {@code shaderpacks/} folder, verified by SHA-1. Unlike mods they are not loaded by Fabric, so there is no
 * dependency resolution; shader packs only need the Iris mod, which the caller offers separately.
 */
public final class PackInstaller {
	/** Kinds of packs and where they go. */
	public enum Kind {
		RESOURCE_PACK("resourcepack", "minecraft", "resourcepacks"),
		SHADER("shader", "iris", "shaderpacks");

		public final String projectType;
		/** Modrinth "loader" of the pack's versions. */
		public final String loader;
		public final String folder;

		Kind(String projectType, String loader, String folder) {
			this.projectType = projectType;
			this.loader = loader;
			this.folder = folder;
		}
	}

	/** The chosen version and whether it was made for the installation's Minecraft version. */
	public record Choice(Version version, boolean exactGameVersion) {
	}

	private final ModrinthApi api;
	private final Path gameDir;
	private final String gameVersion;

	public PackInstaller(ModrinthApi api, Path gameDir, String gameVersion) {
		this.api = api;
		this.gameDir = gameDir;
		this.gameVersion = gameVersion;
	}

	/**
	 * Newest release for the installation's Minecraft version; resource and shader packs usually also work on other
	 * versions, so without one the newest version overall is used (and {@link Choice#exactGameVersion()} is false).
	 */
	public Choice choose(String projectId, Kind kind) throws IOException {
		List<Version> all = api.projectVersions(projectId, kind.loader, null);
		Version exact = null;
		Version newest = null;
		for (Version version : all) {
			if (version.primaryFile() == null) {
				continue;
			}
			if (newest == null) {
				newest = version;
			}
			if (version.gameVersions() != null && version.gameVersions().contains(gameVersion)) {
				if ("release".equals(version.versionType())) {
					return new Choice(version, true);
				}
				if (exact == null) {
					exact = version;
				}
			}
		}
		if (exact != null) {
			return new Choice(exact, true);
		}
		if (newest == null) {
			throw new IOException(kind == Kind.SHADER ? "This shader has no version for Iris." : "This pack has no downloadable version.");
		}
		return new Choice(newest, false);
	}

	/** Downloads the version's main file into the pack folder. Returns the installed file. */
	public Path install(Version version, Kind kind, ProgressListener progress) throws IOException {
		VersionFile file = version.primaryFile();
		if (file == null || file.url() == null || !MrPack.isAllowedDownload(file.url())) {
			throw new IOException("This pack has no file Vantage can download safely.");
		}
		Path folder = gameDir.resolve(kind.folder);
		Files.createDirectories(folder);
		Path target = FileUtil.resolveInside(folder, FileUtil.sanitizeFileName(file.filename()));
		String sha1 = file.hashes() == null ? null : file.hashes().sha1();
		long total = Math.max(1, file.size());
		long[] done = {0};
		progress.status("Downloading " + file.filename());
		Http.download(URI.create(file.url()), target, sha1 != null ? "SHA-1" : null, sha1, bytes -> {
			done[0] += bytes;
			progress.progress(Math.clamp(done[0] / (double) total, 0.0, 1.0));
		}, progress::isCancelled);
		return target;
	}

	/** True if a file with the version's file name is already in the pack folder. */
	public boolean isInstalled(Version version, Kind kind) {
		VersionFile file = version.primaryFile();
		return file != null && Files.isRegularFile(gameDir.resolve(kind.folder).resolve(FileUtil.sanitizeFileName(file.filename())));
	}
}
