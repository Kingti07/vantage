package dev.vantage.launcher.game;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.modpack.MrPack;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.modrinth.model.ModrinthModels.VersionFile;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.common.version.SemanticVersion;
import dev.vantage.launcher.LauncherPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs Modrinth modpacks: downloads the {@code .mrpack} of a version (or takes a local file), downloads every
 * mod, resource pack and shader pack it lists with hash checks, and copies its configs and settings into the
 * installation's game directory. Creating the installation itself is left to the caller.
 */
public final class ModpackInstaller {
	private final LauncherPaths paths;

	public ModpackInstaller(LauncherPaths paths) {
		this.paths = paths;
	}

	/** The {@code .mrpack} file of a Modrinth modpack version. */
	public static VersionFile packFile(Version version) throws IOException {
		VersionFile fallback = null;
		if (version.files() != null) {
			for (VersionFile file : version.files()) {
				if (file.filename() != null && file.filename().endsWith(".mrpack")) {
					if (file.primary()) {
						return file;
					}
					if (fallback == null) {
						fallback = file;
					}
				}
			}
		}
		if (fallback == null) {
			throw new IOException("This modpack version has no .mrpack file.");
		}
		return fallback;
	}

	/** Downloads the {@code .mrpack} of {@code version} into the cache (or reuses a verified copy). */
	public Path download(Version version, ProgressListener progress) throws IOException {
		VersionFile file = packFile(version);
		if (file.url() == null || !MrPack.isAllowedDownload(file.url())) {
			throw new IOException("The modpack file is not hosted on Modrinth.");
		}
		Path dir = paths.cache().resolve("modpacks");
		Files.createDirectories(dir);
		Path target = FileUtil.resolveInside(dir, FileUtil.sanitizeFileName(file.filename()));
		String sha1 = file.hashes() == null ? null : file.hashes().sha1();
		if (sha1 != null && Files.isRegularFile(target) && HashUtil.sha1(target).equalsIgnoreCase(sha1)) {
			return target;
		}
		progress.status("Downloading " + file.filename());
		long total = Math.max(1, file.size());
		long[] done = {0};
		Http.download(URI.create(file.url()), target, sha1 != null ? "SHA-1" : null, sha1, bytes -> {
			done[0] += bytes;
			progress.progress(Math.clamp(done[0] / (double) total, 0.0, 1.0));
		}, progress::isCancelled);
		return target;
	}

	/**
	 * Downloads every client file of {@code pack} into {@code gameDir} and then copies the pack's overrides (configs,
	 * options, resource packs …) over it. Returns the number of override files.
	 */
	public int installFiles(MrPack pack, Path gameDir, boolean includeOptional, int threads, ProgressListener progress) throws IOException {
		if (!pack.isSupported()) {
			throw new IOException(unsupportedMessage(pack));
		}
		Files.createDirectories(gameDir);
		DownloadQueue queue = new DownloadQueue(true);
		for (MrPack.PackFile file : pack.clientFiles(includeOptional)) {
			if (file.sha1() == null || file.sha1().isBlank()) {
				throw new IOException("The modpack gives no checksum for " + file.path() + ", so it cannot be verified.");
			}
			queue.add(MrPack.downloadUri(file).toString(), MrPack.target(gameDir, file), file.sha1(), file.fileSize());
		}
		queue.run(progress, "Downloading modpack files", 0.0, 0.9, threads);
		progress.checkCancelled();
		progress.status("Copying configs and settings");
		int overrides = pack.extractOverrides(gameDir);
		progress.progress(1.0);
		return overrides;
	}

	/**
	 * The Fabric Loader version an installation of {@code pack} should use: the pack's own, unless the Vantage client
	 * is added and needs a newer one (Fabric Loader runs mods made for older versions). Empty means "newest stable".
	 */
	public static String loaderVersion(MrPack pack, boolean vantageClient) {
		return loaderVersion(pack.loader() == MrPack.Loader.FABRIC && pack.loaderVersion() != null ? pack.loaderVersion() : "", vantageClient);
	}

	/** Same rule for any wanted Fabric Loader version (empty = newest stable). */
	public static String loaderVersion(String wanted, boolean vantageClient) {
		if (!vantageClient || wanted == null || wanted.isEmpty()) {
			return wanted;
		}
		SemanticVersion packLoader = SemanticVersion.tryParse(wanted);
		SemanticVersion minimum = SemanticVersion.tryParse(VantageInfo.CLIENT_MIN_LOADER_VERSION);
		if (packLoader != null && minimum != null && packLoader.compareTo(minimum) < 0) {
			return VantageInfo.CLIENT_MIN_LOADER_VERSION;
		}
		return wanted;
	}

	public static String unsupportedMessage(MrPack pack) {
		return pack.loader().label + " modpacks cannot be installed yet. Vantage runs Fabric and vanilla modpacks.";
	}
}
