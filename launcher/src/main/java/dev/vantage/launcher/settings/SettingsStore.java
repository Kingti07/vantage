package dev.vantage.launcher.settings;

import com.google.gson.reflect.TypeToken;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Json;
import dev.vantage.launcher.LauncherPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Loads and saves {@link LauncherSettings} and the list of {@link Instance}s. Every mutation is followed by an
 * immediate save, so the user never has to press "save" and never edits files by hand.
 * <p>
 * Loading never destroys user data: a file that cannot be parsed is moved aside (see {@link FileUtil#moveAside})
 * before defaults are written, out-of-range values are repaired, and older file versions are migrated. What was
 * repaired is reported through {@link #warnings()} so the UI can tell the user.
 */
public final class SettingsStore {
	/** Current schema version of {@code launcher.json} / {@code instances.json}. */
	public static final int CONFIG_VERSION = 2;
	private static final Logger LOGGER = LoggerFactory.getLogger(SettingsStore.class);
	private static final Type INSTANCE_LIST = new TypeToken<List<Instance>>() {
	}.getType();

	private final LauncherPaths paths;
	private LauncherSettings settings = new LauncherSettings();
	private final List<Instance> instances = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();
	/** Files that could neither be read nor moved aside; they are never overwritten. */
	private final Set<Path> protectedFiles = new HashSet<>();

	public SettingsStore(LauncherPaths paths) {
		this.paths = paths;
	}

	public void load() {
		warnings.clear();
		protectedFiles.clear();
		boolean dirty = false;

		LauncherSettings loaded = read(paths.settingsFile(), LauncherSettings.class, "launcher settings");
		if (loaded == null) {
			loaded = new LauncherSettings();
			loaded.configVersion = CONFIG_VERSION;
			dirty = true;
		}
		settings = loaded;

		instances.clear();
		List<Instance> loadedInstances = read(paths.instancesFile(), INSTANCE_LIST, "installations");
		if (loadedInstances != null) {
			loadedInstances.stream().filter(i -> i != null).forEach(instances::add);
		}

		dirty |= migrate(settings, instances);
		dirty |= settings.validate();
		dirty |= repairInstances();
		if (instances.isEmpty()) {
			Instance instance = new Instance();
			instance.id = "default";
			instance.name = "Vantage PvP";
			instances.add(instance);
			dirty = true;
		}
		if (instance(settings.selectedInstance).isEmpty()) {
			settings.selectedInstance = instances.getFirst().id;
			dirty = true;
		}
		if (dirty) {
			saveInstances();
			save();
		}
	}

	/** Human-readable notes about data that had to be repaired during the last {@link #load()}. */
	public List<String> warnings() {
		return List.copyOf(warnings);
	}

	/**
	 * Upgrades files written by older launcher versions. Each step moves the data one version up, so a file of
	 * any age reaches {@link #CONFIG_VERSION}. Returns true if anything changed.
	 */
	static boolean migrate(LauncherSettings settings, List<Instance> instances) {
		boolean changed = false;
		if (settings.configVersion < 1) {
			// v0 -> v1: versioning was introduced; the layout of both files is unchanged.
			settings.configVersion = 1;
			changed = true;
		}
		if (settings.configVersion < 2) {
			// v1 -> v2 (Vantage 0.2.0): performance mods are on for new installations. Existing ones keep their mods
			// as they are and are asked once instead of being changed behind the player's back.
			for (Instance instance : instances) {
				instance.performanceMods = false;
				instance.offerPerformanceMods = true;
			}
			settings.configVersion = 2;
			changed = true;
		}
		if (settings.configVersion > CONFIG_VERSION) {
			LOGGER.warn("launcher.json was written by a newer Vantage version (schema {}); unknown options are ignored",
				settings.configVersion);
		}
		return changed;
	}

	private boolean repairInstances() {
		boolean changed = false;
		Set<String> seen = new HashSet<>();
		for (Instance instance : instances) {
			changed |= instance.validate();
			boolean validId = instance.id != null && Instance.VALID_ID.matcher(instance.id).matches();
			if (validId && seen.add(instance.id)) {
				continue;
			}
			if (validId && instance.gameDirectory.isBlank()) {
				// A duplicate id: keep this installation pointing at the folder it used so far.
				instance.gameDirectory = instance.resolveGameDirectory(paths.instances()).toString();
			}
			LOGGER.warn("Installation '{}' had an invalid or duplicate id ({}); assigning a new one", instance.name, instance.id);
			instance.id = newId(seen);
			seen.add(instance.id);
			changed = true;
		}
		return changed;
	}

	private <T> T read(Path file, Type type, String what) {
		if (!Files.exists(file)) {
			return null;
		}
		try {
			return Json.read(file, type);
		} catch (IOException e) {
			LOGGER.error("Could not read {} from {}", what, file, e);
			try {
				Path backup = FileUtil.moveAside(file);
				warnings.add("Your " + what + " file was damaged and has been reset. The old file was kept as "
					+ backup.getFileName() + " in the Vantage data folder.");
			} catch (IOException moveError) {
				LOGGER.error("Could not move {} aside; it will not be overwritten", file, moveError);
				protectedFiles.add(file);
				warnings.add("Your " + what + " file could not be read (" + e.getMessage() + "). Changes in this session "
					+ "will not be saved so the file is not overwritten.");
			}
			return null;
		}
	}

	private static String newId(Set<String> taken) {
		String id;
		do {
			id = UUID.randomUUID().toString().substring(0, 8);
		} while (taken.contains(id));
		return id;
	}

	public LauncherSettings settings() {
		return settings;
	}

	public void save() {
		write(paths.settingsFile(), settings, "launcher settings");
	}

	public List<Instance> instances() {
		return List.copyOf(instances);
	}

	public Optional<Instance> instance(String id) {
		return instances.stream().filter(i -> i.id.equals(id)).findFirst();
	}

	public Instance selectedInstance() {
		return instance(settings.selectedInstance).orElse(instances.getFirst());
	}

	public void select(Instance instance) {
		settings.selectedInstance = instance.id;
		save();
	}

	public Instance createInstance(String name, String minecraftVersion) {
		Instance instance = new Instance();
		Set<String> taken = new HashSet<>();
		instances.forEach(i -> taken.add(i.id));
		instance.id = newId(taken);
		instance.name = name;
		instance.minecraftVersion = minecraftVersion;
		instance.vantageClient = instance.supportsVantageClient();
		instance.validate();
		instances.add(instance);
		saveInstances();
		return instance;
	}

	/** Replaces the stored instance with the same id. */
	public void updateInstance(Instance updated) {
		updated.validate();
		for (int i = 0; i < instances.size(); i++) {
			if (instances.get(i).id.equals(updated.id)) {
				instances.set(i, updated);
			}
		}
		saveInstances();
	}

	public void deleteInstance(Instance instance) {
		if (instances.size() <= 1) {
			throw new IllegalStateException("At least one installation must remain");
		}
		instances.removeIf(i -> i.id.equals(instance.id));
		if (settings.selectedInstance.equals(instance.id)) {
			settings.selectedInstance = instances.getFirst().id;
			save();
		}
		saveInstances();
	}

	public void saveInstances() {
		write(paths.instancesFile(), instances, "installations");
	}

	private void write(Path file, Object value, String what) {
		if (protectedFiles.contains(file)) {
			LOGGER.warn("Not saving {}: the existing file could not be read and is protected", what);
			return;
		}
		try {
			Json.write(file, value);
		} catch (IOException e) {
			LOGGER.error("Could not save {}", what, e);
		}
	}
}
