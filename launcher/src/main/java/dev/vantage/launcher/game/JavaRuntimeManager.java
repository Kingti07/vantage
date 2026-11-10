package dev.vantage.launcher.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.OperatingSystem;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.LauncherPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Downloads and manages the Java runtimes Mojang publishes for each Minecraft version
 * ({@code javaVersion.component} in the version JSON), exactly like the official launcher does.
 */
public final class JavaRuntimeManager {
	private static final URI RUNTIME_INDEX = URI.create(
		"https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json");
	private static final Pattern VERSION_PATTERN = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?");

	public record InstalledRuntime(String component, String version, Path home, Path executable) {
	}

	private final LauncherPaths paths;

	public JavaRuntimeManager(LauncherPaths paths) {
		this.paths = paths;
	}

	/** Mojang's platform key for this machine, e.g. "windows-x64" or "mac-os-arm64". */
	public static String platformKey() {
		boolean arm = OperatingSystem.isArm64();
		return switch (OperatingSystem.current()) {
			case WINDOWS -> arm ? "windows-arm64" : OperatingSystem.is64Bit() ? "windows-x64" : "windows-x86";
			case MACOS -> arm ? "mac-os-arm64" : "mac-os";
			case LINUX -> arm ? "linux-arm64" : OperatingSystem.is64Bit() ? "linux" : "linux-i386";
		};
	}

	/** Installs (if necessary) the runtime component and returns the path of its java executable. */
	public Path ensure(String component, ProgressListener listener, double progressStart, double progressEnd, int threads) throws IOException {
		Path home = FileUtil.resolveInside(paths.runtimes(), component + "/" + platformKey());
		Path marker = home.resolve(".vantage-runtime-version");
		listener.status("Checking Java runtime (" + component + ")");

		JsonObject index;
		try {
			index = JsonParser.parseString(Http.getString(RUNTIME_INDEX)).getAsJsonObject();
		} catch (IOException e) {
			// Offline: use what is installed.
			Path exe = executable(home);
			if (Files.isRegularFile(marker) && Files.isRegularFile(exe)) {
				listener.progress(progressEnd);
				return exe;
			}
			throw new IOException("Could not download the Java runtime list: " + e.getMessage(), e);
		}
		JsonObject platform = index.getAsJsonObject(platformKey());
		if (platform == null || !platform.has(component) || platform.getAsJsonArray(component).isEmpty()) {
			throw new IOException("Mojang provides no '" + component + "' runtime for " + platformKey()
				+ ". Choose a custom Java executable in the installation settings.");
		}
		JsonObject entry = platform.getAsJsonArray(component).get(0).getAsJsonObject();
		String version = entry.getAsJsonObject("version").get("name").getAsString();
		Path exe = executable(home);
		if (Files.isRegularFile(marker) && Files.readString(marker).trim().equals(version) && Files.isRegularFile(exe)) {
			listener.progress(progressEnd);
			return exe;
		}

		listener.status("Downloading Java " + version);
		JsonObject manifestRef = entry.getAsJsonObject("manifest");
		// The file list is verified like every other download (its SHA-1 is published in the runtime index).
		Path manifestFile = home.resolveSibling(platformKey() + ".manifest.json");
		Http.download(URI.create(manifestRef.get("url").getAsString()), manifestFile, "SHA-1", manifestRef.get("sha1").getAsString(), null);
		JsonObject manifest = JsonParser.parseString(Files.readString(manifestFile)).getAsJsonObject();
		Set<Path> expected = new HashSet<>();
		DownloadQueue queue = new DownloadQueue(true);
		List<Path> executables = new ArrayList<>();
		List<Map.Entry<Path, String>> links = new ArrayList<>();
		for (Map.Entry<String, JsonElement> file : manifest.getAsJsonObject("files").entrySet()) {
			JsonObject info = file.getValue().getAsJsonObject();
			Path target = FileUtil.resolveInside(home, file.getKey());
			expected.add(target);
			switch (info.get("type").getAsString()) {
				case "directory" -> Files.createDirectories(target);
				case "file" -> {
					JsonObject raw = info.getAsJsonObject("downloads").getAsJsonObject("raw");
					queue.add(raw.get("url").getAsString(), target, raw.get("sha1").getAsString(), raw.get("size").getAsLong());
					if (info.has("executable") && info.get("executable").getAsBoolean()) {
						executables.add(target);
					}
				}
				case "link" -> links.add(Map.entry(target, info.get("target").getAsString()));
				default -> {
				}
			}
		}
		queue.run(listener, "Downloading Java " + version, progressStart, progressEnd, threads);
		for (Path executable : executables) {
			executable.toFile().setExecutable(true, false);
		}
		if (OperatingSystem.current() != OperatingSystem.WINDOWS) {
			for (Map.Entry<Path, String> link : links) {
				Files.deleteIfExists(link.getKey());
				Files.createDirectories(link.getKey().getParent());
				Files.createSymbolicLink(link.getKey(), Path.of(link.getValue()));
			}
		}
		removeStaleFiles(home, expected, marker);
		FileUtil.writeAtomically(marker, version.getBytes(StandardCharsets.UTF_8));
		return exe;
	}

	/** Deletes files an older runtime version left behind (files of a running game stay until the next update). */
	private static void removeStaleFiles(Path home, Set<Path> expected, Path marker) {
		try (Stream<Path> files = Files.walk(home)) {
			for (Path file : files.filter(p -> !Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList()) {
				if (!expected.contains(file.toAbsolutePath().normalize()) && !file.equals(marker)) {
					try {
						Files.deleteIfExists(file);
					} catch (IOException ignored) {
						// in use; removed with the next update
					}
				}
			}
		} catch (IOException ignored) {
			// nothing to clean
		}
	}

	public static Path executable(Path home) {
		return switch (OperatingSystem.current()) {
			case WINDOWS -> home.resolve("bin").resolve("javaw.exe");
			case MACOS -> home.resolve("jre.bundle").resolve("Contents").resolve("Home").resolve("bin").resolve("java");
			case LINUX -> home.resolve("bin").resolve("java");
		};
	}

	public List<InstalledRuntime> installed() throws IOException {
		List<InstalledRuntime> result = new ArrayList<>();
		if (!Files.isDirectory(paths.runtimes())) {
			return result;
		}
		try (Stream<Path> components = Files.list(paths.runtimes())) {
			for (Path component : components.filter(Files::isDirectory).toList()) {
				Path home = component.resolve(platformKey());
				Path marker = home.resolve(".vantage-runtime-version");
				if (Files.isRegularFile(marker)) {
					result.add(new InstalledRuntime(component.getFileName().toString(), Files.readString(marker).trim(), home, executable(home)));
				}
			}
		}
		return result;
	}

	public void delete(InstalledRuntime runtime) throws IOException {
		FileUtil.deleteRecursively(runtime.home());
	}

	/**
	 * Runs {@code java -version} and returns the major version (e.g. 21), or -1 if the executable does not work.
	 * Uses {@code java} instead of {@code javaw} on Windows so that output can be read.
	 */
	public static int probeMajorVersion(Path javaExecutable) {
		Path exe = javaExecutable;
		if (exe.getFileName().toString().equalsIgnoreCase("javaw.exe")) {
			Path console = exe.resolveSibling("java.exe");
			if (Files.isRegularFile(console)) {
				exe = console;
			}
		}
		try {
			Process process = new ProcessBuilder(exe.toString(), "-version").redirectErrorStream(true).start();
			StringBuilder output = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append('\n');
				}
			}
			if (!process.waitFor(15, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return -1;
			}
			Matcher matcher = VERSION_PATTERN.matcher(output);
			if (!matcher.find()) {
				return -1;
			}
			int major = Integer.parseInt(matcher.group(1));
			// Java 8 and older report "1.8.0_x"
			return major == 1 && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : major;
		} catch (IOException e) {
			return -1;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		}
	}
}
