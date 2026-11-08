package dev.vantage.client.module;

import com.google.gson.JsonObject;
import dev.vantage.client.config.ConfigManager;
import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class of every feature. A module has an on/off state, typed settings, optional keybinds and lifecycle
 * hooks. Modules never run logic while disabled: mixins and event handlers check {@link #isEnabled()} first.
 */
public abstract class Module {
	protected static final Minecraft MC = Minecraft.getInstance();

	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final boolean defaultEnabled;
	private boolean enabled;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final List<Keybind> keybinds = new ArrayList<>();
	private boolean alwaysEnabled;

	protected Module(String id, String name, String description, Category category, boolean defaultEnabled) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public boolean isEnabled() {
		return enabled || alwaysEnabled;
	}

	/** Modules that are part of the client's chrome (e.g. Interface) cannot be switched off. */
	protected void setAlwaysEnabled() {
		this.alwaysEnabled = true;
		this.enabled = true;
	}

	public boolean canToggle() {
		return !alwaysEnabled;
	}

	public void setEnabled(boolean value) {
		if (alwaysEnabled || value == enabled) {
			return;
		}
		enabled = value;
		if (value) {
			onEnable();
		} else {
			onDisable();
		}
		ConfigManager.markDirty();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	/** Adds a keybind that toggles this module. */
	protected Keybind toggleKeybind(InputKey... defaults) {
		return keybind("toggle", "Toggle " + name, defaults).onPress(this::toggle);
	}

	protected Keybind keybind(String suffix, String label, InputKey... defaults) {
		Keybind keybind = new Keybind(id + "." + suffix, label, name, defaults);
		keybinds.add(keybind);
		return keybind;
	}

	/**
	 * Gives every switchable module a toggle keybind (unbound unless the module set a default), so any feature
	 * can be bound from the Keybinds screen.
	 */
	void ensureToggleKeybind() {
		if (canToggle() && keybinds.stream().noneMatch(k -> k.id().equals(id + ".toggle"))) {
			toggleKeybind();
		}
	}

	public List<Keybind> keybinds() {
		return Collections.unmodifiableList(keybinds);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick while enabled. */
	public void onTick() {
	}

	public void resetSettings() {
		settings.forEach(Setting::reset);
		keybinds.forEach(Keybind::reset);
	}

	public JsonObject save() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", enabled);
		JsonObject values = new JsonObject();
		for (Setting<?> setting : settings) {
			values.add(setting.id(), setting.toJson());
		}
		json.add("settings", values);
		saveExtra(json);
		return json;
	}

	public void load(JsonObject json) {
		boolean wasEnabled = enabled;
		enabled = json != null && json.has("enabled") ? json.get("enabled").getAsBoolean() : defaultEnabled;
		JsonObject values = json != null && json.has("settings") && json.get("settings").isJsonObject() ? json.getAsJsonObject("settings") : null;
		for (Setting<?> setting : settings) {
			if (values != null && values.has(setting.id())) {
				setting.fromJson(values.get(setting.id()));
			} else {
				setting.reset();
			}
		}
		loadExtra(json == null ? new JsonObject() : json);
		if (wasEnabled != enabled) {
			if (enabled) {
				onEnable();
			} else {
				onDisable();
			}
		}
	}

	/** Hook for subclasses that persist more than settings (e.g. HUD position). */
	protected void saveExtra(JsonObject json) {
	}

	protected void loadExtra(JsonObject json) {
	}

	protected static boolean inGame() {
		return MC.player != null && MC.level != null;
	}
}
