package dev.vantage.common.mods;

import com.google.gson.reflect.TypeToken;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * While Minecraft is running, the JVM keeps every loaded mod jar open, and Windows refuses to delete or rename
 * open files. Changes made from the in-game mod manager are therefore queued in
 * {@code .vantage-pending.json} and applied by the launcher right before the next launch.
 */
public final class PendingOperations {
	public static final String FILE_NAME = ".vantage-pending.json";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PendingOperations.class);

	public enum Action {DELETE, ENABLE, DISABLE}

	private final Path modsDir;
	private final Path file;
	/** base file name -> action */
	private final Map<String, Action> actions = new LinkedHashMap<>();

	public PendingOperations(Path modsDir) {
		this.modsDir = modsDir;
		this.file = modsDir.resolve(FILE_NAME);
	}

	public synchronized void load() {
		actions.clear();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			Map<String, Action> loaded = Json.read(file, new TypeToken<Map<String, Action>>() {
			}.getType());
			if (loaded != null) {
				loaded.forEach((k, v) -> {
					if (v != null) {
						actions.put(k, v);
					}
				});
			}
		} catch (IOException e) {
			LOGGER.warn("Ignoring unreadable pending mod operations: {}", e.getMessage());
		}
	}

	public synchronized Action get(String baseFileName) {
		return actions.get(baseFileName);
	}

	public synchronized void put(String baseFileName, Action action) throws IOException {
		if (action == null) {
			actions.remove(baseFileName);
		} else {
			actions.put(baseFileName, action);
		}
		save();
	}

	public synchronized boolean isEmpty() {
		return actions.isEmpty();
	}

	/**
	 * Applies every queued change. Must only be called while the game is not running.
	 *
	 * @return number of operations that could not be applied (they stay queued)
	 */
	public synchronized int applyAll() {
		int failures = 0;
		var iterator = actions.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			Path enabledFile = modsDir.resolve(entry.getKey());
			Path disabledFile = modsDir.resolve(entry.getKey() + InstalledMod.DISABLED_SUFFIX);
			try {
				switch (entry.getValue()) {
					case DELETE -> {
						Files.deleteIfExists(enabledFile);
						Files.deleteIfExists(disabledFile);
					}
					case ENABLE -> {
						if (Files.exists(disabledFile)) {
							FileUtil.moveReplacing(disabledFile, enabledFile);
						}
					}
					case DISABLE -> {
						if (Files.exists(enabledFile)) {
							FileUtil.moveReplacing(enabledFile, disabledFile);
						}
					}
				}
				iterator.remove();
			} catch (IOException e) {
				failures++;
				LOGGER.warn("Could not apply pending {} for {}: {}", entry.getValue(), entry.getKey(), e.toString());
			}
		}
		try {
			save();
		} catch (IOException e) {
			LOGGER.warn("Could not save pending mod operations: {}", e.getMessage());
		}
		return failures;
	}

	private void save() throws IOException {
		if (actions.isEmpty()) {
			Files.deleteIfExists(file);
		} else {
			Json.write(file, actions);
		}
	}
}
