package dev.vantage.common.modpack;

import com.google.gson.JsonParseException;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A Modrinth modpack file ({@code .mrpack}, see https://support.modrinth.com/en/articles/8802351). It is a zip with
 * {@code modrinth.index.json} (the files to download and the required Minecraft and loader versions) plus
 * {@code overrides/} and {@code client-overrides/} folders that are copied into the game directory as they are:
 * configs, options, resource packs, shader packs and so on.
 * <p>
 * Everything in a pack comes from a third party, so it is checked: downloads only from the hosts Modrinth allows,
 * every file with its hash, and no path may leave the game directory.
 */
public final class MrPack {
	public static final String INDEX = "modrinth.index.json";
	/** The only hosts Modrinth allows in {@code downloads}. */
	public static final Set<String> ALLOWED_HOSTS = Set.of("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com");
	private static final String OVERRIDES = "overrides/";
	private static final String CLIENT_OVERRIDES = "client-overrides/";

	/** Mod loaders a pack can require. */
	public enum Loader {
		VANILLA("Vanilla", null),
		FABRIC("Fabric", "fabric-loader"),
		QUILT("Quilt", "quilt-loader"),
		FORGE("Forge", "forge"),
		NEOFORGE("NeoForge", "neoforge");

		public final String label;
		public final String dependencyKey;

		Loader(String label, String dependencyKey) {
			this.label = label;
			this.dependencyKey = dependencyKey;
		}
	}

	public record Env(String client, String server) {
	}

	public record PackFile(String path, Map<String, String> hashes, Env env, List<String> downloads, long fileSize) {
		/** False for files the pack marks as server-only. */
		public boolean forClient() {
			return env == null || !"unsupported".equals(env.client());
		}

		public boolean optional() {
			return env != null && "optional".equals(env.client());
		}

		public String sha1() {
			return hashes == null ? null : hashes.get("sha1");
		}

		public String fileName() {
			int slash = path.lastIndexOf('/');
			return slash >= 0 ? path.substring(slash + 1) : path;
		}
	}

	public record Index(int formatVersion, String game, String versionId, String name, String summary, List<PackFile> files,
		Map<String, String> dependencies) {
	}

	private final Path file;
	private final Index index;

	private MrPack(Path file, Index index) {
		this.file = file;
		this.index = index;
	}

	/** Reads and checks the index of {@code file}. */
	public static MrPack open(Path file) throws IOException {
		Index index;
		try (ZipFile zip = new ZipFile(file.toFile())) {
			ZipEntry entry = zip.getEntry(INDEX);
			if (entry == null) {
				throw new IOException("This is not a Modrinth modpack: " + INDEX + " is missing.");
			}
			try (InputStream in = zip.getInputStream(entry); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				index = Json.GSON.fromJson(reader, Index.class);
			} catch (JsonParseException e) {
				throw new IOException("The modpack index is damaged: " + e.getMessage(), e);
			}
		} catch (java.util.zip.ZipException e) {
			throw new IOException("The modpack file is damaged or not a zip file.", e);
		}
		MrPack pack = new MrPack(file, index);
		pack.validate();
		return pack;
	}

	private void validate() throws IOException {
		if (index == null) {
			throw new IOException("The modpack index is empty.");
		}
		if (index.formatVersion() != 1) {
			throw new IOException("Unsupported modpack format version " + index.formatVersion() + ".");
		}
		if (!"minecraft".equals(index.game())) {
			throw new IOException("This modpack is for " + index.game() + ", not Minecraft.");
		}
		if (minecraftVersion() == null) {
			throw new IOException("The modpack does not say which Minecraft version it needs.");
		}
		for (PackFile packFile : files()) {
			if (packFile.path() == null || packFile.path().isBlank()) {
				throw new IOException("The modpack lists a file without a path.");
			}
		}
	}

	public Path file() {
		return file;
	}

	public String name() {
		return index.name() == null || index.name().isBlank() ? "Modpack" : index.name().strip();
	}

	public String versionId() {
		return index.versionId() == null ? "" : index.versionId();
	}

	public String summary() {
		return index.summary() == null ? "" : index.summary();
	}

	public List<PackFile> files() {
		return index.files() == null ? List.of() : index.files();
	}

	public String minecraftVersion() {
		return index.dependencies() == null ? null : index.dependencies().get("minecraft");
	}

	public Loader loader() {
		Map<String, String> dependencies = index.dependencies() == null ? Map.of() : index.dependencies();
		for (Loader loader : Loader.values()) {
			if (loader.dependencyKey != null && dependencies.containsKey(loader.dependencyKey)) {
				return loader;
			}
		}
		return Loader.VANILLA;
	}

	/** Version of the required loader, or null for vanilla packs. */
	public String loaderVersion() {
		Loader loader = loader();
		return loader.dependencyKey == null ? null : index.dependencies().get(loader.dependencyKey);
	}

	/** Vantage can run vanilla and Fabric packs. */
	public boolean isSupported() {
		return loader() == Loader.VANILLA || loader() == Loader.FABRIC;
	}

	/** Files to download for the client: everything except server-only files, optional ones only when asked. */
	public List<PackFile> clientFiles(boolean includeOptional) {
		List<PackFile> result = new ArrayList<>();
		for (PackFile packFile : files()) {
			if (packFile.forClient() && (includeOptional || !packFile.optional())) {
				result.add(packFile);
			}
		}
		return result;
	}

	public long downloadSize(boolean includeOptional) {
		return clientFiles(includeOptional).stream().mapToLong(f -> Math.max(0, f.fileSize())).sum();
	}

	/** The first download address of {@code packFile} on an allowed host. */
	public static URI downloadUri(PackFile packFile) throws IOException {
		if (packFile.downloads() != null) {
			for (String url : packFile.downloads()) {
				if (isAllowedDownload(url)) {
					return URI.create(url);
				}
			}
		}
		throw new IOException("The modpack wants to download " + packFile.path() + " from a server Modrinth does not allow.");
	}

	public static boolean isAllowedDownload(String url) {
		try {
			URI uri = URI.create(url);
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
				&& ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Where {@code packFile} goes inside {@code gameDir}; refuses paths that would leave it. */
	public static Path target(Path gameDir, PackFile packFile) throws IOException {
		return FileUtil.resolveInside(gameDir, packFile.path().replace('\\', '/'));
	}

	/** Number of files in {@code overrides/} and {@code client-overrides/} (configs, options, packs …). */
	public int overrideCount() throws IOException {
		int count = 0;
		try (ZipFile zip = new ZipFile(file.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName().replace('\\', '/');
				if (!entry.isDirectory() && (name.startsWith(OVERRIDES) || name.startsWith(CLIENT_OVERRIDES))) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * Copies {@code overrides/} and then {@code client-overrides/} into {@code gameDir}, replacing existing files.
	 * Returns how many files were written.
	 */
	public int extractOverrides(Path gameDir) throws IOException {
		int count = 0;
		try (ZipFile zip = new ZipFile(file.toFile())) {
			for (String prefix : List.of(OVERRIDES, CLIENT_OVERRIDES)) {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName().replace('\\', '/');
					if (entry.isDirectory() || !name.startsWith(prefix) || name.length() == prefix.length()) {
						continue;
					}
					Path target = FileUtil.resolveInside(gameDir, name.substring(prefix.length()));
					Files.createDirectories(target.getParent());
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
					}
					count++;
				}
			}
		}
		return count;
	}
}
