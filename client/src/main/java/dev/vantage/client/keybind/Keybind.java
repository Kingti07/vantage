package dev.vantage.client.keybind;

import dev.vantage.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A named action that can be bound to any number of keyboard keys and mouse buttons (OR-ed).
 * {@link #onPress}/{@link #onRelease} fire on input events while no screen is open; {@link #isDown()} polls the
 * physical state for "hold" style features.
 */
public final class Keybind {
	private final String id;
	private final String name;
	private final String group;
	private final List<InputKey> defaults;
	private final List<InputKey> keys = new ArrayList<>();
	private Runnable onPress = () -> {
	};
	private Runnable onRelease = () -> {
	};

	public Keybind(String id, String name, String group, InputKey... defaults) {
		this.id = id;
		this.name = name;
		this.group = group;
		this.defaults = List.of(defaults);
		this.keys.addAll(this.defaults);
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String group() {
		return group;
	}

	public List<InputKey> keys() {
		return List.copyOf(keys);
	}

	public List<InputKey> defaults() {
		return defaults;
	}

	public boolean isBound() {
		return !keys.isEmpty();
	}

	public boolean matches(InputKey key) {
		return keys.contains(key);
	}

	public void add(InputKey key) {
		if (!keys.contains(key)) {
			keys.add(key);
			ConfigManager.markDirty();
		}
	}

	public void remove(InputKey key) {
		if (keys.remove(key)) {
			ConfigManager.markDirty();
		}
	}

	public void clear() {
		if (!keys.isEmpty()) {
			keys.clear();
			ConfigManager.markDirty();
		}
	}

	public void reset() {
		keys.clear();
		keys.addAll(defaults);
		ConfigManager.markDirty();
	}

	public boolean isDefault() {
		return keys.equals(defaults);
	}

	/** Replaces bindings while loading (no dirty flag). */
	public void load(List<InputKey> loaded) {
		keys.clear();
		keys.addAll(loaded);
	}

	public boolean isDown() {
		for (InputKey key : keys) {
			if (key.isDown()) {
				return true;
			}
		}
		return false;
	}

	public Keybind onPress(Runnable action) {
		this.onPress = action;
		return this;
	}

	public Keybind onRelease(Runnable action) {
		this.onRelease = action;
		return this;
	}

	void firePress() {
		onPress.run();
	}

	void fireRelease() {
		onRelease.run();
	}

	public String describe() {
		if (keys.isEmpty()) {
			return "Not bound";
		}
		return String.join(" / ", keys.stream().map(InputKey::displayName).toList());
	}
}
