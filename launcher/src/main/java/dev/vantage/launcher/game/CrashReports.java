package dev.vantage.launcher.game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Finds the crash report a game session left behind and extracts the part worth showing. */
public final class CrashReports {
	/** Tolerance for file timestamps written just before the session start was recorded. */
	private static final long CLOCK_SLACK_MS = 5_000;

	private CrashReports() {
	}

	/**
	 * The newest Minecraft crash report ({@code crash-reports/crash-*.txt}) or JVM crash log ({@code hs_err_pid*.log})
	 * written since {@code sinceMillis}.
	 */
	public static Optional<Path> newestSince(Path gameDir, long sinceMillis) {
		List<Path> candidates = new ArrayList<>();
		collect(gameDir.resolve("crash-reports"), "crash-", ".txt", candidates);
		collect(gameDir, "hs_err_pid", ".log", candidates);
		return candidates.stream()
			.filter(p -> modified(p) >= sinceMillis - CLOCK_SLACK_MS)
			.max(Comparator.comparingLong(CrashReports::modified));
	}

	/** The first {@code maxLines} lines (header, description and the top of the stack trace). */
	public static String excerpt(Path report, int maxLines) throws IOException {
		List<String> lines = new ArrayList<>();
		try (Stream<String> stream = Files.lines(report, StandardCharsets.UTF_8)) {
			stream.limit(maxLines).forEach(lines::add);
		} catch (java.io.UncheckedIOException e) {
			// Not UTF-8 (JVM crash logs may use the system encoding): fall back to a lenient read.
			lines.clear();
			String text = new String(Files.readAllBytes(report), StandardCharsets.ISO_8859_1);
			text.lines().limit(maxLines).forEach(lines::add);
		}
		return String.join("\n", lines);
	}

	private static void collect(Path dir, String prefix, String suffix, List<Path> out) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(p -> {
				String name = p.getFileName().toString();
				return name.startsWith(prefix) && name.endsWith(suffix) && Files.isRegularFile(p);
			}).forEach(out::add);
		} catch (IOException ignored) {
			// nothing to show
		}
	}

	private static long modified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}
}
