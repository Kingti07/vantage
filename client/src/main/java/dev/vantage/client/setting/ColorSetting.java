package dev.vantage.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.vantage.client.util.Colors;

/** ARGB colour with optional animated "chroma" (rainbow) mode. Alpha doubles as transparency. */
public final class ColorSetting extends Setting<Integer> {
	private boolean chroma;
	private final boolean allowChroma;

	public ColorSetting(String id, String name, String description, int defaultArgb, boolean allowChroma) {
		super(id, name, description, defaultArgb);
		this.allowChroma = allowChroma;
	}

	public ColorSetting(String id, String name, String description, int defaultArgb) {
		this(id, name, description, defaultArgb, true);
	}

	/** Current colour, resolving chroma mode. {@code offset} desynchronises chroma between elements. */
	public int color(float offset) {
		return chroma ? Colors.chroma(0.25f, offset, Colors.alpha(value)) : value;
	}

	public int color() {
		return color(0);
	}

	public boolean chroma() {
		return chroma;
	}

	public boolean allowChroma() {
		return allowChroma;
	}

	public void setChroma(boolean chroma) {
		if (this.chroma != chroma && allowChroma) {
			this.chroma = chroma;
			dev.vantage.client.config.ConfigManager.markDirty();
		}
	}

	@Override
	public boolean isDefault() {
		return super.isDefault() && !chroma;
	}

	@Override
	public void reset() {
		super.reset();
		setChroma(false);
	}

	@Override
	public JsonElement toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("argb", Colors.toHex(value));
		if (chroma) {
			json.addProperty("chroma", true);
		}
		return json;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json instanceof JsonObject object) {
			if (object.get("argb") instanceof JsonPrimitive hex) {
				Integer parsed = parse(hex.getAsString());
				if (parsed != null) {
					load(parsed);
				}
			}
			chroma = allowChroma && object.has("chroma") && object.get("chroma").getAsBoolean();
		}
	}

	private static Integer parse(String hex) {
		try {
			String clean = hex.startsWith("#") ? hex.substring(1) : hex;
			long v = Long.parseLong(clean, 16);
			return clean.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
