package dev.vantage.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A bounded number shown as a slider. */
public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String unit;

	public NumberSetting(String id, String name, String description, double defaultValue, double min, double max, double step, String unit) {
		super(id, name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit == null ? "" : unit;
	}

	public NumberSetting(String id, String name, String description, double defaultValue, double min, double max, double step) {
		this(id, name, description, defaultValue, min, max, step, "");
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public float floatValue() {
		return value.floatValue();
	}

	public int intValue() {
		return (int) Math.round(value);
	}

	public String format() {
		String number = step >= 1 ? Integer.toString(intValue()) : step >= 0.1
			? String.format(Locale.ROOT, "%.1f", value) : String.format(Locale.ROOT, "%.2f", value);
		return number + unit;
	}

	@Override
	protected Double validate(Double newValue) {
		if (newValue == null || newValue.isNaN()) {
			return null;
		}
		double clamped = Math.max(min, Math.min(max, newValue));
		if (step > 0) {
			clamped = min + Math.round((clamped - min) / step) * step;
			clamped = Math.max(min, Math.min(max, clamped));
		}
		return Math.round(clamped * 10_000d) / 10_000d;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			load(json.getAsDouble());
		}
	}
}
