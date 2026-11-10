package dev.vantage.launcher.game;

import com.google.gson.JsonElement;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.Json;
import dev.vantage.common.util.OperatingSystem;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.LauncherPaths;
import dev.vantage.launcher.game.MojangModels.Artifact;
import dev.vantage.launcher.game.MojangModels.AssetIndex;
import dev.vantage.launcher.game.MojangModels.AssetObject;
import dev.vantage.launcher.game.MojangModels.Library;
import dev.vantage.launcher.game.MojangModels.VersionJson;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs everything needed to start a Fabric instance: version JSONs, client jar, libraries, natives, assets,
 * logging configuration and the Java runtime. Re-running is cheap: files that are already present are skipped.
 */
public final class GameInstaller {
	private static final java.util.regex.Pattern ASSET_HASH = java.util.regex.Pattern.compile("[0-9a-f]{40}");


	/** Everything {@link GameLauncher} needs to build the command line. */
	public record PreparedGame(
		VersionJson version,
		String vanillaId,
		List<Path> classpath,
		Path nativesDir,
		Path assetsDir,
		String assetIndexId,
		Path loggingConfig,
		String loggingArgument,
		Path javaExecutable
	) {
	}

	private final LauncherPaths paths;
	private final GameMeta meta;
	private final JavaRuntimeManager runtimes;

	public GameInstaller(LauncherPaths paths, GameMeta meta, JavaRuntimeManager runtimes) {
		this.paths = paths;
		this.meta = meta;
		this.runtimes = runtimes;
	}

	/**
	 * @param loaderVersion Fabric loader version, or null to launch vanilla
	 * @param customJava    user-selected java executable, or null to use the Mojang runtime
	 * @param verify        re-hash every file (repair mode) instead of trusting sizes
	 */
	public PreparedGame install(String gameVersion, String loaderVersion, Path customJava, boolean verify, int threads,
		ProgressListener listener) throws IOException {
		listener.progress(0);
		listener.status("Loading version information");
		VersionJson vanilla = vanillaVersion(gameVersion);
		VersionJson merged = vanilla;
		if (loaderVersion != null) {
			merged = merge(vanilla, fabricProfile(gameVersion, loaderVersion));
		}
		listener.progress(0.03);

		// Client jar + libraries
		DownloadQueue queue = new DownloadQueue(verify);
		Path clientJar = FileUtil.resolveInside(paths.versions(), vanilla.id() + "/" + vanilla.id() + ".jar");
		var client = vanilla.downloads().client();
		queue.add(client.url(), clientJar, client.sha1(), client.size());
		List<Path> classpath = new ArrayList<>();
		List<Path> legacyNatives = new ArrayList<>();
		for (Library library : merged.libraries()) {
			if (!Rules.allows(library.rules(), Map.of())) {
				continue;
			}
			Artifact artifact = artifact(library);
			if (artifact != null) {
				Path target = FileUtil.resolveInside(paths.libraries(), artifact.path());
				if (artifact.url() != null && !artifact.url().isBlank()) {
					queue.add(artifact.url(), target, artifact.sha1(), artifact.size());
				}
				classpath.add(target);
			}
			Artifact nativeArtifact = legacyNative(library);
			if (nativeArtifact != null) {
				Path target = FileUtil.resolveInside(paths.libraries(), nativeArtifact.path());
				queue.add(nativeArtifact.url(), target, nativeArtifact.sha1(), nativeArtifact.size());
				legacyNatives.add(target);
			}
		}
		classpath.add(clientJar);
		queue.run(listener, "Downloading game libraries", 0.03, 0.25, threads);

		listener.checkCancelled();
		// Natives: modern LWJGL extracts itself from the classpath into this directory; legacy versions need extraction.
		Path nativesDir = FileUtil.resolveInside(paths.versions(), merged.id() + "/natives");
		Files.createDirectories(nativesDir);
		for (Path jar : legacyNatives) {
			extractNatives(jar, nativesDir);
		}

		listener.checkCancelled();
		// Assets
		var indexRef = vanilla.assetIndex();
		Path indexFile = FileUtil.resolveInside(paths.assets().resolve("indexes"), indexRef.id() + ".json");
		DownloadQueue indexQueue = new DownloadQueue(true);
		indexQueue.add(indexRef.url(), indexFile, indexRef.sha1(), indexRef.size());
		indexQueue.run(listener, "Downloading asset index", 0.25, 0.27, 1);
		AssetIndex index = Json.read(indexFile, AssetIndex.class);
		if (index.virtual() || index.mapToResources()) {
			// Minecraft before 1.7.10 expects a different asset layout (virtual / resources folder).
			throw new IOException("Minecraft " + gameVersion + " is too old for this launcher. Vantage supports Minecraft 1.7.10 and newer.");
		}
		DownloadQueue assets = new DownloadQueue(verify);
		Map<String, AssetObject> unique = new LinkedHashMap<>();
		for (AssetObject object : index.objects().values()) {
			unique.putIfAbsent(object.hash(), object);
		}
		for (AssetObject object : unique.values()) {
			if (object.hash() == null || !ASSET_HASH.matcher(object.hash()).matches()) {
				throw new IOException("Invalid asset hash in index " + indexRef.id() + ": " + object.hash());
			}
			String prefix = object.hash().substring(0, 2);
			assets.add("https://resources.download.minecraft.net/" + prefix + "/" + object.hash(),
				paths.assets().resolve("objects").resolve(prefix).resolve(object.hash()), object.hash(), object.size());
		}
		assets.run(listener, "Downloading game assets", 0.27, 0.75, Math.max(threads, 8));

		listener.checkCancelled();
		// Logging configuration
		Path loggingConfig = null;
		String loggingArgument = null;
		if (vanilla.logging() != null && vanilla.logging().client() != null && vanilla.logging().client().file() != null) {
			var logging = vanilla.logging().client();
			loggingConfig = FileUtil.resolveInside(paths.assets().resolve("log_configs"), logging.file().id());
			DownloadQueue logQueue = new DownloadQueue(true);
			logQueue.add(logging.file().url(), loggingConfig, logging.file().sha1(), logging.file().size());
			logQueue.run(listener, "Downloading logging configuration", 0.75, 0.76, 1);
			loggingArgument = logging.argument();
		}

		listener.checkCancelled();
		// Java runtime
		Path java;
		if (customJava != null) {
			if (!Files.isRegularFile(customJava)) {
				throw new IOException("The selected Java executable does not exist: " + customJava);
			}
			java = customJava;
			listener.progress(1);
		} else {
			String component = vanilla.javaVersion() != null ? vanilla.javaVersion().component() : "jre-legacy";
			java = runtimes.ensure(component, listener, 0.76, 1.0, threads);
		}
		listener.progress(1);
		listener.status("Ready");
		return new PreparedGame(merged, vanilla.id(), classpath, nativesDir, paths.assets(), indexRef.id(), loggingConfig, loggingArgument, java);
	}

	/** Re-hashes every file of an installation and re-downloads anything that is corrupt. */
	public PreparedGame repair(String gameVersion, String loaderVersion, Path customJava, int threads, ProgressListener listener) throws IOException {
		return install(gameVersion, loaderVersion, customJava, true, threads, listener);
	}

	private VersionJson vanillaVersion(String gameVersion) throws IOException {
		Path file = FileUtil.resolveInside(paths.versions(), gameVersion + "/" + gameVersion + ".json");
		MojangModels.ManifestEntry entry;
		try {
			entry = meta.entry(gameVersion);
		} catch (IOException e) {
			if (Files.isRegularFile(file)) {
				return Json.read(file, VersionJson.class);
			}
			throw e;
		}
		if (!Files.isRegularFile(file) || !HashUtil.sha1(file).equalsIgnoreCase(entry.sha1())) {
			Http.download(URI.create(entry.url()), file, "SHA-1", entry.sha1(), null);
		}
		return Json.read(file, VersionJson.class);
	}

	private VersionJson fabricProfile(String gameVersion, String loaderVersion) throws IOException {
		String id = GameMeta.fabricVersionId(gameVersion, loaderVersion);
		Path file = FileUtil.resolveInside(paths.versions(), id + "/" + id + ".json");
		if (!Files.isRegularFile(file)) {
			String body = Http.getString(GameMeta.fabricProfileUri(gameVersion, loaderVersion));
			FileUtil.writeAtomically(file, body.getBytes(StandardCharsets.UTF_8));
		}
		return Json.read(file, VersionJson.class);
	}

	/**
	 * Merges a child (Fabric) version into its parent. Child libraries win over parent libraries with the same
	 * group:artifact:classifier, and child arguments are appended to the parent's.
	 */
	static VersionJson merge(VersionJson parent, VersionJson child) {
		Map<String, Library> libraries = new LinkedHashMap<>();
		for (Library library : child.libraries()) {
			libraries.put(libraryKey(library.name()), library);
		}
		for (Library library : parent.libraries()) {
			libraries.putIfAbsent(libraryKey(library.name()), library);
		}
		List<JsonElement> game = new ArrayList<>();
		List<JsonElement> jvm = new ArrayList<>();
		if (parent.arguments() != null) {
			addAll(game, parent.arguments().game());
			addAll(jvm, parent.arguments().jvm());
		}
		if (child.arguments() != null) {
			addAll(game, child.arguments().game());
			addAll(jvm, child.arguments().jvm());
		}
		return new VersionJson(
			child.id(),
			null,
			parent.type(),
			child.mainClass() != null ? child.mainClass() : parent.mainClass(),
			parent.assets(),
			parent.assetIndex(),
			parent.downloads(),
			parent.javaVersion(),
			new ArrayList<>(libraries.values()),
			new MojangModels.Arguments(game, jvm),
			child.minecraftArguments() != null ? child.minecraftArguments() : parent.minecraftArguments(),
			parent.logging(),
			parent.releaseTime());
	}

	private static void addAll(List<JsonElement> target, List<JsonElement> source) {
		if (source != null) {
			target.addAll(source);
		}
	}

	private static String libraryKey(String mavenName) {
		String[] parts = mavenName.split(":");
		// group:artifact[:version][:classifier] -> group:artifact:classifier
		return parts.length >= 4 ? parts[0] + ":" + parts[1] + ":" + parts[3] : parts[0] + ":" + (parts.length > 1 ? parts[1] : "");
	}

	/** Resolves the downloadable artifact of a library from Mojang's "downloads" or Fabric's maven coordinates. */
	static Artifact artifact(Library library) {
		if (library.downloads() != null) {
			return library.downloads().artifact();
		}
		if (library.name() == null) {
			return null;
		}
		String path = mavenPath(library.name());
		String base = library.url() != null ? library.url() : "https://libraries.minecraft.net/";
		if (!base.endsWith("/")) {
			base += "/";
		}
		return new Artifact(path, library.sha1(), library.size(), base + path);
	}

	private static Artifact legacyNative(Library library) {
		if (library.natives() == null || library.downloads() == null || library.downloads().classifiers() == null) {
			return null;
		}
		String classifier = library.natives().get(OperatingSystem.current().mojangName());
		if (classifier == null) {
			return null;
		}
		classifier = classifier.replace("${arch}", OperatingSystem.is64Bit() ? "64" : "32");
		return library.downloads().classifiers().get(classifier);
	}

	static String mavenPath(String name) {
		String[] parts = name.split(":");
		if (parts.length < 3) {
			throw new IllegalArgumentException("Invalid maven coordinate: " + name);
		}
		String extension = "jar";
		String version = parts[2];
		int at = version.indexOf('@');
		if (at >= 0) {
			extension = version.substring(at + 1);
			version = version.substring(0, at);
		}
		String classifier = parts.length > 3 ? "-" + parts[3] : "";
		return parts[0].replace('.', '/') + "/" + parts[1] + "/" + version + "/" + parts[1] + "-" + version + classifier + "." + extension;
	}

	private static void extractNatives(Path jar, Path target) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (entry.isDirectory() || name.startsWith("META-INF")) {
					continue;
				}
				Path out = target.resolve(Path.of(name).getFileName().toString());
				if (Files.exists(out)) {
					continue;
				}
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, out);
				}
			}
		}
	}
}
