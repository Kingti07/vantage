package dev.vantage.launcher.settings;

import com.google.gson.reflect.TypeToken;
import dev.vantage.common.util.Json;
import dev.vantage.launcher.LauncherPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads and saves {@link LauncherSettings} and the list of {@link Instance}s. Every mutation is followed by an
 * immediate save, so the user never has to press "save" and never edits files by hand.
 */
public final class SettingsStore {
	private static final Logger LOGGER = LoggerFactory.getLogger(SettingsStore.class);

	private final LauncherPaths paths;
	private LauncherSettings settings = new LauncherSettings();
	private final List<Instance> instances = new ArrayList<>();

	public SettingsStore(LauncherPaths paths) {
		this.paths = paths;
	}

	public void load() {
		try {
			if (Files.isRegularFile(paths.settingsFile())) {
				LauncherSettings loaded = Json.read(paths.settingsFile(), LauncherSettings.class);
				if (loaded != null) {
					settings = loaded;
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Settings file unreadable, using defaults: {}", e.getMessage());
		}
		instances.clear();
		try {
			if (Files.isRegularFile(paths.instancesFile())) {
				List<Instance> loaded = Json.read(paths.instancesFile(), new TypeToken<List<Instance>>() {
				}.getType());
				if (loaded != null) {
					loaded.stream().filter(i -> i != null && i.id != null).forEach(instances::add);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Instances file unreadable: {}", e.getMessage());
		}
		if (instances.isEmpty()) {
			Instance instance = new Instance();
			instance.id = "default";
			instance.name = "Vantage PvP";
			instances.add(instance);
			settings.selectedInstance = instance.id;
			saveInstances();
			save();
		}
	}

	public LauncherSettings settings() {
		return settings;
	}

	public void save() {
		try {
			Json.write(paths.settingsFile(), settings);
		} catch (IOException e) {
			LOGGER.error("Could not save launcher settings", e);
		}
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
		instance.id = UUID.randomUUID().toString().substring(0, 8);
		instance.name = name;
		instance.minecraftVersion = minecraftVersion;
		instance.vantageClient = instance.supportsVantageClient();
		instances.add(instance);
		saveInstances();
		return instance;
	}

	/** Replaces the stored instance with the same id. */
	public void updateInstance(Instance updated) {
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
		try {
			Json.write(paths.instancesFile(), instances);
		} catch (IOException e) {
			LOGGER.error("Could not save installations", e);
		}
	}
}
