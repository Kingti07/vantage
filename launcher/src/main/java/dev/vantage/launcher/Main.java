package dev.vantage.launcher;

import dev.vantage.launcher.ui.LauncherApp;
import dev.vantage.launcher.update.SelfUpdater;
import javafx.application.Application;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entry point. Deliberately not a JavaFX {@code Application} subclass so the launcher can run from a plain
 * class-path fat jar. Before starting the UI it configures file logging and, if a newer launcher version has
 * been downloaded by the self-updater, hands over to that jar.
 */
public final class Main {
	private Main() {
	}

	public static void main(String[] args) throws IOException {
		LauncherPaths paths = LauncherPaths.resolve();
		paths.createDirectories();
		configureLogging(paths);

		boolean noHandover = List.of(args).contains("--no-update-handover");
		if (!noHandover) {
			Optional<Path> staged = SelfUpdater.findStagedUpdate(paths);
			if (staged.isPresent() && handOver(staged.get(), args)) {
				return;
			}
		}
		Application.launch(LauncherApp.class, args);
	}

	private static void configureLogging(LauncherPaths paths) throws IOException {
		Path log = paths.logs().resolve("launcher.log");
		Path previous = paths.logs().resolve("launcher.previous.log");
		if (Files.exists(log)) {
			Files.move(log, previous, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		System.setProperty("org.slf4j.simpleLogger.logFile", log.toString());
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
	}

	/** Starts the updated launcher jar with the same Java runtime. Returns false if that failed. */
	private static boolean handOver(Path jar, String[] args) {
		try {
			String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
			List<String> command = new ArrayList<>(List.of(java, "-jar", jar.toString(), "--no-update-handover"));
			command.addAll(List.of(args));
			new ProcessBuilder(command).inheritIO().start();
			return true;
		} catch (IOException e) {
			System.err.println("Could not start updated launcher " + jar + File.separator + ": " + e.getMessage());
			return false;
		}
	}
}
