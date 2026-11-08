package dev.vantage.common.util;

import java.nio.file.Path;
import java.util.Locale;

/** Host OS detection using the names Mojang's launcher metadata uses ("windows", "osx", "linux"). */
public enum OperatingSystem {
	WINDOWS("windows"),
	MACOS("osx"),
	LINUX("linux");

	private final String mojangName;

	OperatingSystem(String mojangName) {
		this.mojangName = mojangName;
	}

	public String mojangName() {
		return mojangName;
	}

	public static OperatingSystem current() {
		String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (name.contains("win")) {
			return WINDOWS;
		}
		if (name.contains("mac") || name.contains("darwin")) {
			return MACOS;
		}
		return LINUX;
	}

	public static boolean isArm64() {
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		return arch.equals("aarch64") || arch.equals("arm64");
	}

	public static boolean is64Bit() {
		return System.getProperty("os.arch", "").contains("64");
	}

	/** Per-user application data directory for Vantage (never a hardcoded user path). */
	public static Path defaultDataDirectory() {
		String home = System.getProperty("user.home");
		return switch (current()) {
			case WINDOWS -> {
				String appData = System.getenv("APPDATA");
				yield Path.of(appData != null && !appData.isBlank() ? appData : home, ".vantage");
			}
			case MACOS -> Path.of(home, "Library", "Application Support", "vantage");
			case LINUX -> {
				String xdg = System.getenv("XDG_DATA_HOME");
				yield xdg != null && !xdg.isBlank() ? Path.of(xdg, "vantage") : Path.of(home, ".local", "share", "vantage");
			}
		};
	}
}
