package dev.vantage.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** One of a fixed set of choices; enum {@code toString()} is used as the display label. */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] values;

	public EnumSetting(String id, String name, String description, E defaultValue) {
		super(id, name, description, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public E[] values() {
		return values;
	}

	public void cycle(int direction) {
		int index = (value.ordinal() + direction + values.length) % values.length;
		set(values[index]);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value.name());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			String name = json.getAsString();
			for (E candidate : values) {
				if (candidate.name().equals(name)) {
					load(candidate);
					return;
				}
			}
		}
	}
}
