package dev.vantage.common.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.vantage.common.util.HashUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Reads mod jars from a mods folder: loader type, fabric.mod.json, jar-in-jar module ids and hashes.
 */
public final class ModScanner {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ModScanner.class);

	private ModScanner() {
	}

	public static List<InstalledMod> scan(Path modsDir, ModIndex index) throws IOException {
		if (!Files.isDirectory(modsDir)) {
			return List.of();
		}
		List<Path> files;
		try (Stream<Path> stream = Files.list(modsDir)) {
			files = stream.filter(Files::isRegularFile)
				.filter(p -> {
					String name = p.getFileName().toString().toLowerCase();
					return name.endsWith(".jar") || name.endsWith(".jar" + InstalledMod.DISABLED_SUFFIX);
				})
				.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
				.toList();
		}
		List<InstalledMod> mods = new ArrayList<>(files.size());
		for (Path file : files) {
			try {
				mods.add(read(file, index));
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Could not read mod jar {}: {}", file.getFileName(), e.toString());
				boolean enabled = !file.getFileName().toString().endsWith(InstalledMod.DISABLED_SUFFIX);
				mods.add(new InstalledMod(file, enabled, "unknown", null, List.of(), null, Files.size(file), null, null));
			}
		}
		return mods;
	}

	public static InstalledMod read(Path file, ModIndex index) throws IOException {
		boolean enabled = !file.getFileName().toString().endsWith(InstalledMod.DISABLED_SUFFIX);
		String loader = "unknown";
		ModMetadata metadata = null;
		List<String> provided = new ArrayList<>();
		try (ZipFile zip = new ZipFile(file.toFile())) {
			ZipEntry fabric = zip.getEntry("fabric.mod.json");
			if (fabric != null) {
				loader = "fabric";
				metadata = parse(zip.getInputStream(fabric));
				if (metadata != null) {
					for (String nested : metadata.nestedJars()) {
						collectNestedIds(zip, nested, provided, 0);
					}
				}
			} else if (zip.getEntry("quilt.mod.json") != null) {
				loader = "quilt";
			} else if (zip.getEntry("META-INF/neoforge.mods.toml") != null) {
				loader = "neoforge";
			} else if (zip.getEntry("META-INF/mods.toml") != null || zip.getEntry("mcmod.info") != null) {
				loader = "forge";
			}
		}
		String sha1 = HashUtil.sha1(file);
		String base = file.getFileName().toString();
		if (base.endsWith(InstalledMod.DISABLED_SUFFIX)) {
			base = base.substring(0, base.length() - InstalledMod.DISABLED_SUFFIX.length());
		}
		ModIndex.Entry entry = index != null ? index.get(base) : null;
		return new InstalledMod(file, enabled, loader, metadata, List.copyOf(provided), sha1, Files.size(file), entry, null);
	}

	/** Reads raw bytes of a file inside a mod jar (e.g. its icon). Returns null if absent. */
	public static byte[] readEntry(Path jar, String entryPath) {
		if (entryPath == null) {
			return null;
		}
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry(entryPath.startsWith("/") ? entryPath.substring(1) : entryPath);
			if (entry == null || entry.getSize() > 4 * 1024 * 1024) {
				return null;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				return in.readAllBytes();
			}
		} catch (IOException e) {
			return null;
		}
	}

	private static void collectNestedIds(ZipFile zip, String path, List<String> out, int depth) throws IOException {
		ZipEntry entry = zip.getEntry(path);
		if (entry == null || depth > 1) {
			return;
		}
		byte[] bytes;
		try (InputStream in = zip.getInputStream(entry)) {
			bytes = in.readAllBytes();
		}
		try (ZipInputStream nested = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry inner;
			while ((inner = nested.getNextEntry()) != null) {
				if (inner.getName().equals("fabric.mod.json")) {
					ModMetadata meta = parse(nested);
					if (meta != null && meta.id() != null) {
						out.add(meta.id());
						out.addAll(meta.provides());
					}
					break;
				}
			}
		}
	}

	private static ModMetadata parse(InputStream in) {
		try {
			// JsonParser.parseReader is lenient, which matches Fabric Loader's tolerance for comments etc.
			JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			return json.isJsonObject() ? ModMetadata.fromFabricJson(json.getAsJsonObject()) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
