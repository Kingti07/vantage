package dev.vantage.launcher;

import dev.vantage.common.VantageInfo;
import dev.vantage.launcher.ui.LauncherApp;
import dev.vantage.launcher.update.SelfUpdater;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Deliberately not a JavaFX {@code Application} subclass so the launcher can run from a plain
 * class-path fat jar. Before starting the UI it configures file logging and, if a verified newer launcher version
 * has been downloaded by the self-updater, hands over to that jar.
 * <p>
 * Hand-over protocol: the old version starts the new jar with {@code --handover-token=<id>} and waits. Once the new
 * window is visible, the new version writes {@code updates/handover-<id>.ok}; the old version then exits and the new
 * one copies itself over the old jar (so later starts need no hand-over). If the new version exits before
 * confirming, the update is recorded as rejected and the old version starts normally instead.
 */
public final class Main {
	static final String ARG_NO_HANDOVER = "--no-update-handover";
	static final String ARG_HANDOVER_TOKEN = "--handover-token=";
	static final String ARG_HANDOVER_FROM = "--handover-from=";
	private static final long HANDOVER_TIMEOUT_MS = 60_000;
	private static final int KEPT_LOG_FILES = 5;

	private enum Handover {STARTED, FAILED, NOT_POSSIBLE}

	/** Kept strongly reachable: java.util.logging forgets the level of loggers that were garbage collected. */
	private static final java.util.logging.Logger JAVAFX_LOGGER = java.util.logging.Logger.getLogger("javafx");
	private static String handoverToken;
	private static Path handoverFrom;
	private static String failedUpdate;

	private Main() {
	}

	public static void main(String[] args) throws IOException {
		LauncherPaths paths = LauncherPaths.resolve();
		paths.createDirectories();
		configureLogging(paths);
		Logger logger = LoggerFactory.getLogger(Main.class);
		logger.info("Vantage Launcher {} on {} {} ({}), Java {} ({}), data folder {}", VantageInfo.VERSION,
			System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
			System.getProperty("java.version"), System.getProperty("java.vendor"), paths.root());

		List<String> arguments = new ArrayList<>();
		for (String arg : args) {
			if (arg.startsWith(ARG_HANDOVER_TOKEN)) {
				handoverToken = arg.substring(ARG_HANDOVER_TOKEN.length());
			} else if (arg.startsWith(ARG_HANDOVER_FROM)) {
				handoverFrom = Path.of(arg.substring(ARG_HANDOVER_FROM.length()));
			} else {
				arguments.add(arg);
			}
		}

		if (handoverToken == null && !arguments.contains(ARG_NO_HANDOVER)) {
			Optional<SelfUpdater.StagedUpdate> staged = SelfUpdater.findStagedUpdate(paths);
			if (staged.isPresent()) {
				Handover result = handOver(paths, staged.get(), arguments, logger);
				if (result == Handover.STARTED) {
					return;
				}
				if (result == Handover.FAILED) {
					SelfUpdater.reject(paths, staged.get());
					failedUpdate = staged.get().version();
				}
			}
		}
		// The launcher runs JavaFX from the class path on purpose (one runnable jar); hide JavaFX's warning about that.
		JAVAFX_LOGGER.setLevel(java.util.logging.Level.SEVERE);
		Application.launch(LauncherApp.class, arguments.toArray(String[]::new));
	}

	/** Version of an update that could not be started during this launch, if any (shown to the user once). */
	public static Optional<String> failedUpdate() {
		return Optional.ofNullable(failedUpdate);
	}

	/**
	 * Called by the UI once the main window is visible. Confirms a hand-over to the waiting old version, installs
	 * this version in place of the old jar and cleans up the updates folder in the background.
	 */
	public static void onWindowShown(LauncherPaths paths) {
		if (handoverToken != null) {
			try {
				Files.writeString(paths.updates().resolve("handover-" + handoverToken + ".ok"), VantageInfo.VERSION);
			} catch (IOException e) {
				LoggerFactory.getLogger(Main.class).error("Could not confirm the update hand-over", e);
			}
		}
		Thread finish = new Thread(() -> {
			if (handoverToken != null && handoverFrom != null) {
				SelfUpdater.replaceOldJar(paths, handoverFrom);
			}
			SelfUpdater.cleanUp(paths);
		}, "vantage-update-finish");
		finish.setDaemon(true);
		finish.start();
	}

	private static Handover handOver(LauncherPaths paths, SelfUpdater.StagedUpdate update, List<String> args, Logger logger) {
		Path java = javaExecutable();
		if (java == null) {
			logger.warn("Cannot start update {}: no java executable in {}", update.version(), System.getProperty("java.home"));
			return Handover.NOT_POSSIBLE;
		}
		String token = UUID.randomUUID().toString();
		Path confirmation = paths.updates().resolve("handover-" + token + ".ok");
		List<String> command = new ArrayList<>();
		command.add(java.toString());
		// Keep options such as -Dvantage.dataDir (portable installs) for the new version.
		System.getProperties().stringPropertyNames().stream().filter(key -> key.startsWith("vantage.")).sorted()
			.forEach(key -> command.add("-D" + key + "=" + System.getProperty(key)));
		command.addAll(List.of("-jar", update.jar().toString(), ARG_HANDOVER_TOKEN + token));
		Path self = SelfUpdater.runningJar();
		if (self != null) {
			command.add(ARG_HANDOVER_FROM + self);
		}
		command.addAll(args);
		logger.info("Starting updated launcher {}", update.version());
		Process process;
		try {
			process = new ProcessBuilder(command).inheritIO().start();
		} catch (IOException e) {
			logger.error("Could not start updated launcher {}", update.jar(), e);
			return Handover.FAILED;
		}
		long deadline = System.currentTimeMillis() + HANDOVER_TIMEOUT_MS;
		try {
			while (System.currentTimeMillis() < deadline) {
				if (Files.exists(confirmation)) {
					Files.deleteIfExists(confirmation);
					return Handover.STARTED;
				}
				if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
					logger.error("Updated launcher {} exited with code {} before its window appeared", update.version(), process.exitValue());
					return Handover.FAILED;
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			logger.warn("Could not remove hand-over confirmation", e);
			return Handover.STARTED;
		}
		logger.error("Updated launcher {} did not open its window within {} s; stopping it", update.version(),
			HANDOVER_TIMEOUT_MS / 1000);
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
		return Handover.FAILED;
	}

	private static Path javaExecutable() {
		Path bin = Path.of(System.getProperty("java.home"), "bin");
		// Not javaw.exe: it reports a broken jar in a modal dialog and keeps running, which would hide the failure.
		// java.exe opens no console window when started from here (the JDK starts it with CREATE_NO_WINDOW).
		for (String name : List.of("java.exe", "java")) {
			Path candidate = bin.resolve(name);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Logs go to {@code logs/launcher.log}; the logs of the four runs before are kept as {@code launcher.1.log}
	 * (newest) to {@code launcher.4.log}, so {@value #KEPT_LOG_FILES} files in total. If the current file is still in use by another
	 * launcher process (Windows locks open files), this run logs to {@code launcher-<pid>.log} instead.
	 */
	static void configureLogging(LauncherPaths paths) {
		Path dir = paths.logs();
		Path log = dir.resolve("launcher.log");
		try {
			Files.deleteIfExists(dir.resolve("launcher.previous.log"));
			deleteOldFallbackLogs(dir);
			Files.deleteIfExists(dir.resolve("launcher." + (KEPT_LOG_FILES - 1) + ".log"));
			for (int i = KEPT_LOG_FILES - 2; i >= 1; i--) {
				Path from = dir.resolve("launcher." + i + ".log");
				if (Files.exists(from)) {
					Files.move(from, dir.resolve("launcher." + (i + 1) + ".log"), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			if (Files.exists(log)) {
				Files.move(log, dir.resolve("launcher.1.log"), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log = dir.resolve("launcher-" + ProcessHandle.current().pid() + ".log");
		}
		System.setProperty("org.slf4j.simpleLogger.logFile", log.toString());
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
	}

	/** Removes {@code launcher-<pid>.log} files (written while another launcher held the main log) after a week. */
	private static void deleteOldFallbackLogs(Path dir) throws IOException {
		long weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
		try (var files = Files.list(dir)) {
			for (Path file : files.filter(p -> p.getFileName().toString().matches("launcher-\\d+\\.log")).toList()) {
				if (Files.getLastModifiedTime(file).toMillis() < weekAgo) {
					Files.deleteIfExists(file);
				}
			}
		}
	}
}
