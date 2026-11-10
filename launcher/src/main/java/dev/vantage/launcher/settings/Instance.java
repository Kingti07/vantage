package dev.vantage.launcher.settings;

import dev.vantage.common.VantageInfo;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * One installation ("profile"): a Minecraft version + Fabric loader version with its own game directory,
 * memory and Java settings.
 */
public final class Instance {
	public static final int DEFAULT_MEMORY_MB = 4096;
	public static final int MIN_MEMORY_MB = 1024;
	public static final int MAX_MEMORY_MB = 131_072;
	public static final int DEFAULT_WIDTH = 1280;
	public static final int DEFAULT_HEIGHT = 720;
	public static final int MAX_NAME_LENGTH = 64;
	/** Ids double as folder names under {@code instances/}, so only safe characters are allowed. */
	public static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	public String id;
	public String name;
	public String minecraftVersion = VantageInfo.CLIENT_MINECRAFT_VERSION;
	/** Fabric loader version; empty selects the newest stable loader at install time. */
	public String loaderVersion = "";
	/** Custom game directory; empty uses {@code instances/<id>} inside the launcher data directory. */
	public String gameDirectory = "";
	public int memoryMb = DEFAULT_MEMORY_MB;
	/** Custom Java executable; empty uses the Mojang-provided runtime for this Minecraft version. */
	public String javaPath = "";
	public String extraJvmArgs = "";
	public int width = DEFAULT_WIDTH;
	public int height = DEFAULT_HEIGHT;
	public boolean customResolution = false;
	public boolean fullscreen = false;
	/** Install the bundled Vantage client mod (only possible on the Minecraft version it was built for). */
	public boolean vantageClient = true;
	public long lastPlayed = 0;
	public long created = System.currentTimeMillis();

	public Path resolveGameDirectory(Path instancesRoot) {
		if (gameDirectory != null && !gameDirectory.isBlank()) {
			return Path.of(gameDirectory);
		}
		return instancesRoot.resolve(id);
	}

	public boolean supportsVantageClient() {
		return VantageInfo.CLIENT_MINECRAFT_VERSION.equals(minecraftVersion);
	}

	/**
	 * Replaces missing or invalid values (except the id, which {@link SettingsStore} manages) with defaults.
	 * Returns true if anything had to be changed.
	 */
	public boolean validate() {
		boolean changed = false;
		if (name == null || name.isBlank()) {
			name = "Installation";
			changed = true;
		} else if (name.length() > MAX_NAME_LENGTH || !name.equals(name.strip())) {
			String stripped = name.strip();
			name = stripped.length() > MAX_NAME_LENGTH ? stripped.substring(0, MAX_NAME_LENGTH) : stripped;
			changed = true;
		}
		if (minecraftVersion == null || minecraftVersion.isBlank()) {
			minecraftVersion = VantageInfo.CLIENT_MINECRAFT_VERSION;
			changed = true;
		}
		if (loaderVersion == null) {
			loaderVersion = "";
			changed = true;
		}
		if (gameDirectory == null) {
			gameDirectory = "";
			changed = true;
		}
		if (javaPath == null) {
			javaPath = "";
			changed = true;
		}
		if (extraJvmArgs == null) {
			extraJvmArgs = "";
			changed = true;
		}
		if (memoryMb < MIN_MEMORY_MB || memoryMb > MAX_MEMORY_MB) {
			memoryMb = DEFAULT_MEMORY_MB;
			changed = true;
		}
		if (width < 320 || width > 15_360 || height < 240 || height > 8_640) {
			width = DEFAULT_WIDTH;
			height = DEFAULT_HEIGHT;
			changed = true;
		}
		if (created <= 0) {
			created = System.currentTimeMillis();
			changed = true;
		}
		if (lastPlayed < 0) {
			lastPlayed = 0;
			changed = true;
		}
		return changed;
	}

	public Instance copy() {
		Instance copy = new Instance();
		copy.id = id;
		copy.name = name;
		copy.minecraftVersion = minecraftVersion;
		copy.loaderVersion = loaderVersion;
		copy.gameDirectory = gameDirectory;
		copy.memoryMb = memoryMb;
		copy.javaPath = javaPath;
		copy.extraJvmArgs = extraJvmArgs;
		copy.width = width;
		copy.height = height;
		copy.customResolution = customResolution;
		copy.fullscreen = fullscreen;
		copy.vantageClient = vantageClient;
		copy.lastPlayed = lastPlayed;
		copy.created = created;
		return copy;
	}

	@Override
	public String toString() {
		return name;
	}
}
