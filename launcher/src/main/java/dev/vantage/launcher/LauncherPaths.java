package dev.vantage.launcher;

import dev.vantage.common.util.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Layout of the launcher data directory. Shared game files (libraries, assets, versions, Java runtimes) live
 * once at the top level; every installation gets its own game directory under {@code instances/}.
 * <p>
 * The root can be overridden with {@code -Dvantage.dataDir=...} or the {@code VANTAGE_DATA_DIR} environment
 * variable (portable installs).
 */
public record LauncherPaths(Path root) {
	public static LauncherPaths resolve() {
		String override = System.getProperty("vantage.dataDir", System.getenv("VANTAGE_DATA_DIR"));
		Path root = override != null && !override.isBlank() ? Path.of(override) : OperatingSystem.defaultDataDirectory();
		return new LauncherPaths(root.toAbsolutePath().normalize());
	}

	public void createDirectories() throws IOException {
		for (Path dir : new Path[]{root, versions(), libraries(), assets(), runtimes(), instances(), logs(), updates(), cache()}) {
			Files.createDirectories(dir);
		}
	}

	public Path versions() {
		return root.resolve("versions");
	}

	public Path libraries() {
		return root.resolve("libraries");
	}

	public Path assets() {
		return root.resolve("assets");
	}

	public Path runtimes() {
		return root.resolve("runtimes");
	}

	public Path instances() {
		return root.resolve("instances");
	}

	public Path logs() {
		return root.resolve("logs");
	}

	public Path updates() {
		return root.resolve("updates");
	}

	public Path cache() {
		return root.resolve("cache");
	}

	public Path settingsFile() {
		return root.resolve("launcher.json");
	}

	public Path instancesFile() {
		return root.resolve("instances.json");
	}
}
