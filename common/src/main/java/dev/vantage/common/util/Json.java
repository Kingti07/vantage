package dev.vantage.common.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared Gson instances.
 * <ul>
 *   <li>{@link #GSON} – camelCase, pretty printed; used for our own config files and Mojang metadata.</li>
 *   <li>{@link #SNAKE} – snake_case field naming; used for the Modrinth API.</li>
 * </ul>
 */
public final class Json {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final Gson SNAKE = new GsonBuilder()
		.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.disableHtmlEscaping()
		.create();

	private Json() {
	}

	public static <T> T read(Path file, Type type) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, type);
		} catch (com.google.gson.JsonParseException e) {
			throw new IOException("Malformed JSON in " + file + ": " + e.getMessage(), e);
		}
	}

	public static JsonObject readObject(Path file) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement element = JsonParser.parseReader(reader);
			if (!element.isJsonObject()) {
				throw new IOException("Expected a JSON object in " + file);
			}
			return element.getAsJsonObject();
		} catch (com.google.gson.JsonParseException e) {
			throw new IOException("Malformed JSON in " + file + ": " + e.getMessage(), e);
		}
	}

	/** Writes JSON atomically (temp file + move) so a crash never leaves a half-written config behind. */
	public static void write(Path file, Object value) throws IOException {
		FileUtil.writeAtomically(file, GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
	}

	public static String string(JsonObject object, String key, String fallback) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}
}
