package dev.vantage.common.mods;

import com.google.gson.reflect.TypeToken;
import dev.vantage.common.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers where each jar in a mods folder came from (Modrinth project + version), keyed by base file name.
 * Stored as {@code .vantage-index.json} inside the mods folder so it travels with the instance.
 */
public final class ModIndex {
	public static final String FILE_NAME = ".vantage-index.json";

	public record Entry(String projectId, String versionId, String versionNumber, String title, String iconUrl, String sha1, boolean dependency) {
	}

	private final Path file;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public ModIndex(Path modsDir) {
		this.file = modsDir.resolve(FILE_NAME);
	}

	public synchronized void load() {
		entries.clear();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			Map<String, Entry> loaded = Json.read(file, new TypeToken<Map<String, Entry>>() {
			}.getType());
			if (loaded != null) {
				entries.putAll(loaded);
			}
		} catch (IOException e) {
			// A corrupt index only loses Modrinth attribution; jars are re-identified by hash on the next update check.
			org.slf4j.LoggerFactory.getLogger(ModIndex.class).warn("Ignoring unreadable mod index {}: {}", file, e.getMessage());
		}
	}

	public synchronized void save() throws IOException {
		Json.write(file, entries);
	}

	public synchronized Entry get(String baseFileName) {
		return entries.get(baseFileName);
	}

	public synchronized void put(String baseFileName, Entry entry) {
		entries.put(baseFileName, entry);
	}

	public synchronized void remove(String baseFileName) {
		entries.remove(baseFileName);
	}

	public synchronized Map<String, Entry> snapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
	}
}
