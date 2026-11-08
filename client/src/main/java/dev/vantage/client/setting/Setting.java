package dev.vantage.client.setting;

import com.google.gson.JsonElement;
import dev.vantage.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A typed, persisted option of a module. Changing a value marks the configuration dirty so it is saved
 * automatically; the settings UI is generated from these objects, so every option is configurable in-game.
 */
public abstract class Setting<T> {
	private final String id;
	private final String name;
	private final String description;
	protected T defaultValue;
	protected T value;
	private BooleanSupplier visible = () -> true;
	private final List<Consumer<T>> listeners = new ArrayList<>();

	protected Setting(String id, String name, String description, T defaultValue) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
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

	public T get() {
		return value;
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void set(T newValue) {
		T validated = validate(newValue);
		if (validated == null || validated.equals(value)) {
			return;
		}
		value = validated;
		listeners.forEach(listener -> listener.accept(validated));
		ConfigManager.markDirty();
	}

	/** Changes the default (and current) value; used by modules whose defaults differ from the shared base. */
	public Setting<T> overrideDefault(T newDefault) {
		this.defaultValue = newDefault;
		this.value = newDefault;
		return this;
	}

	public void reset() {
		set(defaultValue);
	}

	public boolean isDefault() {
		return value.equals(defaultValue);
	}

	/** Only shows the setting in the UI while the condition holds (e.g. sub-options of a toggle). */
	public Setting<T> visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
		return this;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	public Setting<T> onChange(Consumer<T> listener) {
		listeners.add(listener);
		return this;
	}

	protected T validate(T newValue) {
		return newValue;
	}

	public abstract JsonElement toJson();

	/** Loads a stored value; invalid or missing data keeps the current value. */
	public abstract void fromJson(JsonElement json);

	/** Sets the value without notifying listeners or marking dirty (used while loading). */
	protected void load(T loaded) {
		T validated = validate(loaded);
		if (validated != null) {
			value = validated;
			listeners.forEach(listener -> listener.accept(validated));
		}
	}
}
