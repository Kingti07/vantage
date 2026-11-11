package dev.vantage.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vantage.client.hud.HudModule;
import dev.vantage.client.keybind.KeybindManager;
import dev.vantage.client.module.Module;
import dev.vantage.client.module.ModuleManager;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Json;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Persists all module settings, HUD layout and keybinds as JSON under {@code config/vantage/}.
 * <ul>
 *   <li>{@code profiles/<name>.json} – complete configuration profiles (switchable in-game)</li>
 *   <li>{@code hud-layouts/<name>.json} – HUD-only layouts saved from the HUD editor</li>
 *   <li>{@code client.json} – which profile is active</li>
 * </ul>
 * Changes are written automatically about a second after the last modification and on shutdown, so users
 * never need to save or edit files manually.
 */
public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("Vantage/Config");
	private static final long SAVE_DELAY_MS = 800;
	private static volatile boolean dirty;
	private static volatile long lastChange;
	private static boolean loading;

	private final Path root = FabricLoader.getInstance().getConfigDir().resolve("vantage");
	private final Path profiles = root.resolve("profiles");
	private final Path layouts = root.resolve("hud-layouts");
	private final ModuleManager modules;
	private final KeybindManager keybinds;
	private String activeProfile = "default";
	/** Server address (lower case, without the default port) -> profile used while playing there. */
	private final java.util.Map<String, String> serverProfiles = new java.util.LinkedHashMap<>();
	/** Personal bests of the practice tools, e.g. "cps" -> 11.4. */
	private final java.util.Map<String, Double> records = new java.util.LinkedHashMap<>();
	/** The profile that was active before a server profile took over; restored when leaving the server. */
	private String profileBeforeServer;

	public ConfigManager(ModuleManager modules, KeybindManager keybinds) {
		this.modules = modules;
		this.keybinds = keybinds;
	}

	/** Records that something changed; the actual write happens later on the client thread. */
	public static void markDirty() {
		if (!loading) {
			dirty = true;
			lastChange = System.currentTimeMillis();
		}
	}

	public Path root() {
		return root;
	}

	public String activeProfile() {
		return activeProfile;
	}

	public void load() {
		try {
			Files.createDirectories(profiles);
			Files.createDirectories(layouts);
			Path client = root.resolve("client.json");
			if (Files.isRegularFile(client)) {
				JsonObject json = Json.readObject(client);
				activeProfile = sanitize(Json.string(json, "activeProfile", "default"));
				if (json.has("serverProfiles") && json.get("serverProfiles").isJsonObject()) {
					json.getAsJsonObject("serverProfiles").entrySet().forEach(e -> serverProfiles.put(e.getKey(), sanitize(e.getValue().getAsString())));
				}
				if (json.has("records") && json.get("records").isJsonObject()) {
					json.getAsJsonObject("records").entrySet().forEach(e -> records.put(e.getKey(), e.getValue().getAsDouble()));
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Could not read client.json: {}", e.getMessage());
		}
		loadProfile(activeProfile);
	}

	/** Called every client tick: saves once changes have settled. */
	public void tick() {
		if (dirty && System.currentTimeMillis() - lastChange > SAVE_DELAY_MS) {
			save();
		}
	}

	public void save() {
		dirty = false;
		JsonObject profile = new JsonObject();
		profile.addProperty("format", 1);
		JsonObject moduleData = new JsonObject();
		for (Module module : modules.all()) {
			moduleData.add(module.id(), module.save());
		}
		profile.add("modules", moduleData);
		profile.add("keybinds", keybinds.save());
		try {
			write(profiles.resolve(activeProfile + ".json"), profile);
			JsonObject client = new JsonObject();
			client.addProperty("activeProfile", profileBeforeServer != null ? profileBeforeServer : activeProfile);
			JsonObject servers = new JsonObject();
			serverProfiles.forEach(servers::addProperty);
			client.add("serverProfiles", servers);
			JsonObject best = new JsonObject();
			records.forEach(best::addProperty);
			client.add("records", best);
			write(root.resolve("client.json"), client);
		} catch (IOException e) {
			LOGGER.error("Could not save Vantage configuration", e);
		}
	}

	private void loadProfile(String name) {
		Path file = profiles.resolve(name + ".json");
		JsonObject json = new JsonObject();
		if (Files.isRegularFile(file)) {
			try {
				json = Json.readObject(file);
			} catch (IOException e) {
				LOGGER.error("Profile {} is unreadable, backing it up and using defaults", name, e);
				try {
					Files.copy(file, file.resolveSibling(name + ".broken-" + System.currentTimeMillis() + ".json"));
				} catch (IOException ignored) {
					// best effort
				}
			}
		}
		loading = true;
		try {
			JsonObject moduleData = json.has("modules") && json.get("modules").isJsonObject() ? json.getAsJsonObject("modules") : new JsonObject();
			for (Module module : modules.all()) {
				JsonElement element = moduleData.get(module.id());
				module.load(element != null && element.isJsonObject() ? element.getAsJsonObject() : null);
			}
			keybinds.load(json.has("keybinds") && json.get("keybinds").isJsonObject() ? json.getAsJsonObject("keybinds") : null);
		} finally {
			loading = false;
		}
		dirty = false;
	}

	// ------------------------------------------------------------------------------------------------ profiles

	public List<String> profiles() {
		return listNames(profiles);
	}

	public void switchProfile(String name) {
		save();
		activeProfile = sanitize(name);
		loadProfile(activeProfile);
		save();
	}

	/** Copies the current configuration into a new profile and activates it. */
	public void createProfile(String name) {
		save();
		activeProfile = sanitize(name);
		save();
	}

	public void deleteProfile(String name) throws IOException {
		String clean = sanitize(name);
		if (clean.equals(activeProfile)) {
			throw new IOException("Switch to another profile before deleting this one.");
		}
		Files.deleteIfExists(profiles.resolve(clean + ".json"));
	}

	/** Restores every module, HUD element and keybind of the active profile to defaults. */
	public void resetActiveProfile() {
		for (Module module : modules.all()) {
			module.load(null);
		}
		keybinds.load(null);
		markDirty();
	}

	// ------------------------------------------------------------------------------------------------ server profiles

	/** Normalises a server address: lower case, default port removed. */
	public static String serverKey(String address) {
		String key = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
		return key.endsWith(":25565") ? key.substring(0, key.length() - 6) : key;
	}

	public java.util.Map<String, String> serverProfiles() {
		return java.util.Collections.unmodifiableMap(serverProfiles);
	}

	public String profileForServer(String address) {
		return serverProfiles.get(serverKey(address));
	}

	public void linkServer(String address, String profile) {
		serverProfiles.put(serverKey(address), sanitize(profile));
		markDirty();
	}

	public void unlinkServer(String address) {
		if (serverProfiles.remove(serverKey(address)) != null) {
			markDirty();
		}
	}

	/** Switches to the profile linked to {@code address}, if any. Returns the profile that is now active, or null. */
	public String enterServer(String address) {
		String profile = profileForServer(address);
		if (profile == null || !profiles().contains(profile) || profile.equals(activeProfile)) {
			return null;
		}
		String previous = activeProfile;
		switchProfile(profile);
		profileBeforeServer = previous;
		return profile;
	}

	/** Goes back to the profile used before {@link #enterServer}. */
	public void leaveServer() {
		if (profileBeforeServer != null) {
			String previous = profileBeforeServer;
			profileBeforeServer = null;
			switchProfile(previous);
		}
	}

	// ------------------------------------------------------------------------------------------------ sharing

	/** The active profile as text that can be pasted into another Vantage client. */
	public String exportActiveProfile() {
		save();
		JsonObject wrapper = new JsonObject();
		wrapper.addProperty("vantageProfile", 1);
		wrapper.addProperty("name", activeProfile);
		try {
			wrapper.add("data", Json.readObject(profiles.resolve(activeProfile + ".json")));
		} catch (IOException e) {
			wrapper.add("data", new JsonObject());
		}
		return new com.google.gson.Gson().toJson(wrapper);
	}

	/** Creates a new profile from exported text and activates it. Returns its name. */
	public String importProfile(String text) throws IOException {
		JsonObject wrapper;
		try {
			wrapper = parse(text);
		} catch (RuntimeException e) {
			throw new IOException("The clipboard does not contain a Vantage profile.");
		}
		if (!wrapper.has("vantageProfile") || !wrapper.has("data") || !wrapper.get("data").isJsonObject()) {
			throw new IOException("The clipboard does not contain a Vantage profile.");
		}
		String base = sanitize(Json.string(wrapper, "name", "imported"));
		String name = base;
		List<String> existing = profiles();
		for (int i = 2; existing.contains(name); i++) {
			name = sanitize(base + "-" + i);
		}
		save();
		write(profiles.resolve(name + ".json"), wrapper.getAsJsonObject("data"));
		switchProfile(name);
		return name;
	}

	// ------------------------------------------------------------------------------------------------ records

	public double record(String key) {
		return records.getOrDefault(key, 0.0);
	}

	public void setRecord(String key, double value) {
		records.put(key, value);
		markDirty();
	}

	// ------------------------------------------------------------------------------------------------ HUD layouts

	public List<String> hudLayouts() {
		return listNames(layouts);
	}

	public void saveHudLayout(String name) throws IOException {
		JsonObject json = new JsonObject();
		for (HudModule hud : modules.hudModules()) {
			json.add(hud.id(), hud.save());
		}
		write(layouts.resolve(sanitize(name) + ".json"), json);
	}

	public void loadHudLayout(String name) throws IOException {
		JsonObject json = Json.readObject(layouts.resolve(sanitize(name) + ".json"));
		for (HudModule hud : modules.hudModules()) {
			JsonElement element = json.get(hud.id());
			if (element != null && element.isJsonObject()) {
				hud.load(element.getAsJsonObject());
			}
		}
		markDirty();
	}

	public void deleteHudLayout(String name) throws IOException {
		Files.deleteIfExists(layouts.resolve(sanitize(name) + ".json"));
	}

	private static List<String> listNames(Path dir) {
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return names;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.map(p -> p.getFileName().toString())
				.filter(n -> n.endsWith(".json") && !n.contains(".broken-"))
				.map(n -> n.substring(0, n.length() - 5))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.forEach(names::add);
		} catch (IOException e) {
			LOGGER.warn("Could not list {}", dir, e);
		}
		return names;
	}

	public static String sanitize(String name) {
		String clean = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\- ]", "").replace(' ', '-');
		return clean.isEmpty() ? "default" : clean.length() > 40 ? clean.substring(0, 40) : clean;
	}

	private static void write(Path file, JsonObject json) throws IOException {
		FileUtil.writeAtomically(file, Json.GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
	}

	/** Parses JSON text (used by tests and imports). */
	static JsonObject parse(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}
}
