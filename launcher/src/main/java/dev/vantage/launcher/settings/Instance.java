package dev.vantage.launcher.settings;

import dev.vantage.common.VantageInfo;

import java.nio.file.Path;

/**
 * One installation ("profile"): a Minecraft version + Fabric loader version with its own game directory,
 * memory and Java settings.
 */
public final class Instance {
	public String id;
	public String name;
	public String minecraftVersion = VantageInfo.CLIENT_MINECRAFT_VERSION;
	/** Fabric loader version; empty selects the newest stable loader at install time. */
	public String loaderVersion = "";
	/** Custom game directory; empty uses {@code instances/<id>} inside the launcher data directory. */
	public String gameDirectory = "";
	public int memoryMb = 4096;
	/** Custom Java executable; empty uses the Mojang-provided runtime for this Minecraft version. */
	public String javaPath = "";
	public String extraJvmArgs = "";
	public int width = 1280;
	public int height = 720;
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
