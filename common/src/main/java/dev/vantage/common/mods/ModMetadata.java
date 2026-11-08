package dev.vantage.common.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The subset of {@code fabric.mod.json} the mod manager needs for display and compatibility checks.
 *
 * @param depends  mod id -> list of version predicates (OR-ed)
 * @param breaks   mod id -> list of version predicates; a match means the two mods must not be loaded together
 * @param provides additional mod ids this mod satisfies
 */
public record ModMetadata(
	String id,
	String version,
	String name,
	String description,
	List<String> authors,
	String iconPath,
	Map<String, List<String>> depends,
	Map<String, List<String>> breaks,
	List<String> provides,
	List<String> nestedJars,
	String environment
) {
	public static ModMetadata fromFabricJson(JsonObject json) {
		String id = string(json, "id");
		String version = string(json, "version");
		String name = string(json, "name");
		List<String> authors = new ArrayList<>();
		if (json.get("authors") instanceof JsonArray array) {
			for (JsonElement author : array) {
				if (author.isJsonPrimitive()) {
					authors.add(author.getAsString());
				} else if (author.isJsonObject() && author.getAsJsonObject().has("name")) {
					authors.add(author.getAsJsonObject().get("name").getAsString());
				}
			}
		}
		String icon = null;
		JsonElement iconElement = json.get("icon");
		if (iconElement != null && iconElement.isJsonPrimitive()) {
			icon = iconElement.getAsString();
		} else if (iconElement instanceof JsonObject sizes) {
			// Pick the largest size up to 128px.
			int best = -1;
			for (Map.Entry<String, JsonElement> entry : sizes.entrySet()) {
				try {
					int size = Integer.parseInt(entry.getKey());
					if (size <= 128 && size > best) {
						best = size;
						icon = entry.getValue().getAsString();
					}
				} catch (NumberFormatException ignored) {
					// skip malformed keys
				}
			}
		}
		List<String> provides = new ArrayList<>();
		if (json.get("provides") instanceof JsonArray array) {
			array.forEach(e -> provides.add(e.getAsString()));
		}
		List<String> nested = new ArrayList<>();
		if (json.get("jars") instanceof JsonArray array) {
			for (JsonElement element : array) {
				if (element.isJsonObject() && element.getAsJsonObject().has("file")) {
					nested.add(element.getAsJsonObject().get("file").getAsString());
				}
			}
		}
		return new ModMetadata(
			id,
			version == null ? "?" : version,
			name == null ? id : name,
			string(json, "description"),
			List.copyOf(authors),
			icon,
			predicates(json.get("depends")),
			predicates(json.get("breaks")),
			List.copyOf(provides),
			List.copyOf(nested),
			string(json, "environment"));
	}

	private static Map<String, List<String>> predicates(JsonElement element) {
		Map<String, List<String>> map = new LinkedHashMap<>();
		if (element instanceof JsonObject object) {
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				List<String> values = new ArrayList<>();
				if (entry.getValue().isJsonArray()) {
					entry.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsString()));
				} else if (entry.getValue().isJsonPrimitive()) {
					values.add(entry.getValue().getAsString());
				}
				map.put(entry.getKey(), List.copyOf(values));
			}
		}
		return map;
	}

	private static String string(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}
}
