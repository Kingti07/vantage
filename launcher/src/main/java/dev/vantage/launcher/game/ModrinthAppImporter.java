package dev.vantage.launcher.game;

import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.OperatingSystem;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.common.util.SqliteReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Imports profiles ("instances") of the Modrinth App on this computer. Names, Minecraft and loader versions come
 * from a copy of the app's database ({@code app.db}, read with {@link SqliteReader}); without it they are taken from
 * the profile's last game log. Importing copies the profile folder into a new Vantage installation, so the Modrinth
 * App's own profiles are never changed.
 */
public final class ModrinthAppImporter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModrinthAppImporter.class);
	private static final Pattern LOG_LINE = Pattern.compile("Loading Minecraft (\\S+) with (Fabric|Quilt) Loader (\\S+)");
	/** Top-level entries that are caches, logs or crash data and not worth copying. */
	private static final Set<String> SKIPPED = Set.of("logs", "crash-reports", ".fabric", ".quilt", "debug", "downloads", ".bobby",
		".mixin.out", "profiles");

	/** One Modrinth App profile. {@code gameVersion} is null when it could not be determined. */
	public record Profile(String name, Path folder, String gameVersion, String loader, String loaderVersion, Path icon) {
		public boolean isSupported() {
			return gameVersion != null && ("fabric".equals(loader) || "vanilla".equals(loader));
		}

		public String loaderLabel() {
			return switch (loader == null ? "" : loader) {
				case "fabric" -> "Fabric";
				case "quilt" -> "Quilt";
				case "forge" -> "Forge";
				case "neoforge" -> "NeoForge";
				case "vanilla" -> "Vanilla";
				default -> loader == null || loader.isBlank() ? "Unknown loader" : loader;
			};
		}

		/** Why the profile cannot be imported, or null. */
		public String problem() {
			if (gameVersion == null) {
				return "Its Minecraft version is unknown. Start it once in the Modrinth App, then try again.";
			}
			if (!isSupported()) {
				return loaderLabel() + " profiles cannot be imported yet. Vantage runs Fabric and vanilla profiles.";
			}
			return null;
		}
	}

	private ModrinthAppImporter() {
	}

	/** Where the Modrinth App keeps its data on this system, if it is installed. */
	public static Optional<Path> findDataDir() {
		for (Path candidate : candidateDirs()) {
			if (Files.isDirectory(candidate.resolve("profiles"))) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	static List<Path> candidateDirs() {
		String home = System.getProperty("user.home");
		List<Path> bases = new ArrayList<>();
		switch (OperatingSystem.current()) {
			case WINDOWS -> {
				String appData = System.getenv("APPDATA");
				bases.add(appData != null ? Path.of(appData) : Path.of(home, "AppData", "Roaming"));
			}
			case MACOS -> bases.add(Path.of(home, "Library", "Application Support"));
			default -> {
				String xdg = System.getenv("XDG_DATA_HOME");
				bases.add(xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "share"));
			}
		}
		List<Path> result = new ArrayList<>();
		for (Path base : bases) {
			result.add(base.resolve("ModrinthApp"));
			result.add(base.resolve("com.modrinth.theseus"));
		}
		return result;
	}

	/** All profiles in {@code dataDir}, sorted by name. */
	public static List<Profile> profiles(Path dataDir) throws IOException {
		Path profilesDir = dataDir.resolve("profiles");
		Map<String, Profile> known = new HashMap<>();
		try {
			known = readDatabase(dataDir);
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read the Modrinth App database; using the profile folders only", e);
		}
		List<Profile> result = new ArrayList<>();
		try (Stream<Path> folders = Files.list(profilesDir)) {
			for (Path folder : folders.filter(Files::isDirectory).toList()) {
				Profile profile = known.get(folder.getFileName().toString());
				result.add(profile != null ? profile : fromFolder(folder));
			}
		}
		result.sort(Comparator.comparing((Profile p) -> p.name().toLowerCase(Locale.ROOT)).thenComparing(p -> p.folder().getFileName().toString()));
		return result;
	}

	/** Folder name -> profile, from a copy of the database (the app may be writing to the original). */
	static Map<String, Profile> readDatabase(Path dataDir) throws IOException {
		Path db = dataDir.resolve("app.db");
		if (!Files.isRegularFile(db)) {
			return Map.of();
		}
		Path temp = Files.createTempDirectory("vantage-modrinth");
		try {
			Files.copy(db, temp.resolve("app.db"));
			Path wal = dataDir.resolve("app.db-wal");
			if (Files.isRegularFile(wal)) {
				Files.copy(wal, temp.resolve("app.db-wal"));
			}
			SqliteReader reader = SqliteReader.open(temp.resolve("app.db"));
			Path profilesDir = dataDir.resolve("profiles");
			Map<String, Profile> result = new HashMap<>();
			if (reader.hasTable("instances") && reader.hasTable("instance_content_sets")) {
				Map<String, List<Map<String, Object>>> sets = new HashMap<>();
				for (Map<String, Object> set : reader.rows("instance_content_sets")) {
					sets.computeIfAbsent(text(set.get("instance_id")), k -> new ArrayList<>()).add(set);
				}
				for (Map<String, Object> instance : reader.rows("instances")) {
					String folder = text(instance.get("path"));
					if (folder == null) {
						continue;
					}
					List<Map<String, Object>> candidates = sets.getOrDefault(text(instance.get("id")), List.of());
					Map<String, Object> set = chooseContentSet(candidates, text(instance.get("applied_content_set_id")));
					result.put(folder, new Profile(nameOr(instance.get("name"), folder), profilesDir.resolve(folder),
						set == null ? null : text(set.get("game_version")), set == null ? null : text(set.get("loader")),
						set == null ? null : text(set.get("loader_version")), icon(instance.get("icon_path"))));
				}
			} else if (reader.hasTable("profiles")) {
				// Older versions of the app (Theseus) kept everything in one table.
				for (Map<String, Object> row : reader.rows("profiles")) {
					String folder = text(row.get("path"));
					if (folder != null) {
						result.put(folder, new Profile(nameOr(row.get("name"), folder), profilesDir.resolve(folder), text(row.get("game_version")),
							text(row.get("mod_loader")), text(row.get("mod_loader_version")), icon(row.get("icon_path"))));
					}
				}
			}
			return result;
		} finally {
			FileUtil.deleteRecursively(temp);
		}
	}

	private static Map<String, Object> chooseContentSet(List<Map<String, Object>> sets, String applied) {
		Map<String, Object> best = null;
		for (Map<String, Object> set : sets) {
			if (applied != null && applied.equals(text(set.get("id")))) {
				return set;
			}
			if (best == null || "available".equals(text(set.get("status"))) && !"available".equals(text(best.get("status")))) {
				best = set;
			}
		}
		return best;
	}

	/** Without the database: the folder name and the version line Fabric/Quilt writes at the top of every game log. */
	static Profile fromFolder(Path folder) {
		String name = folder.getFileName().toString();
		Path log = folder.resolve("logs").resolve("latest.log");
		if (Files.isRegularFile(log)) {
			try (BufferedReader reader = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
				String line;
				for (int i = 0; i < 400 && (line = reader.readLine()) != null; i++) {
					Matcher matcher = LOG_LINE.matcher(line);
					if (matcher.find()) {
						return new Profile(name, folder, matcher.group(1), matcher.group(2).toLowerCase(Locale.ROOT), matcher.group(3), null);
					}
				}
			} catch (IOException e) {
				LOGGER.debug("Could not read {}", log, e);
			}
		}
		return new Profile(name, folder, null, null, null, null);
	}

	private static String text(Object value) {
		return value instanceof String s && !s.isBlank() ? s : null;
	}

	private static String nameOr(Object value, String fallback) {
		String name = text(value);
		return name != null ? name.strip() : fallback;
	}

	private static Path icon(Object value) {
		String path = text(value);
		if (path == null) {
			return null;
		}
		try {
			Path icon = Path.of(path);
			return Files.isRegularFile(icon) ? icon : null;
		} catch (java.nio.file.InvalidPathException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------------------------------------ copying

	/** Bytes {@link #copy} would copy. */
	public static long size(Profile profile, boolean worlds) throws IOException {
		long[] total = {0};
		visit(profile.folder(), worlds, (file, relative, attrs) -> total[0] += attrs.size());
		return total[0];
	}

	/**
	 * Copies the profile into {@code target}: mods, configs, options, resource and shader packs, servers, screenshots
	 * and, with {@code worlds}, the saves. Logs, caches and crash dumps are left out. Returns the number of files.
	 */
	public static int copy(Profile profile, Path target, boolean worlds, ProgressListener progress) throws IOException {
		long total = Math.max(1, size(profile, worlds));
		long[] done = {0};
		int[] files = {0};
		Files.createDirectories(target);
		progress.status("Copying " + profile.name());
		visit(profile.folder(), worlds, (file, relative, attrs) -> {
			progress.checkCancelled();
			Path destination = FileUtil.resolveInside(target, relative);
			Files.createDirectories(destination.getParent());
			Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			done[0] += attrs.size();
			files[0]++;
			progress.progress(Math.clamp(done[0] / (double) total, 0.0, 1.0));
		});
		return files[0];
	}

	private interface FileAction {
		void accept(Path file, String relative, BasicFileAttributes attrs) throws IOException;
	}

	private static void visit(Path root, boolean worlds, FileAction action) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (dir.equals(root)) {
					return FileVisitResult.CONTINUE;
				}
				if (dir.getParent().equals(root)) {
					String name = dir.getFileName().toString();
					if (SKIPPED.contains(name) || (!worlds && "saves".equals(name))) {
						return FileVisitResult.SKIP_SUBTREE;
					}
				}
				return attrs.isSymbolicLink() ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!attrs.isRegularFile()) {
					return FileVisitResult.CONTINUE;
				}
				String name = file.getFileName().toString();
				// JVM crash dumps in the profile root, and world locks of a running game.
				if (file.getParent().equals(root) && (name.startsWith("hs_err_pid") || name.startsWith("replay_pid")) || "session.lock".equals(name)) {
					return FileVisitResult.CONTINUE;
				}
				action.accept(file, root.relativize(file).toString().replace('\\', '/'), attrs);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				LOGGER.warn("Skipping unreadable file {}", file, exc);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
