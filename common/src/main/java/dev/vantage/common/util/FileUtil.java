package dev.vantage.common.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class FileUtil {
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/**
	 * Moves a damaged file out of the way so it is never overwritten, e.g. {@code instances.json} becomes
	 * {@code instances.broken-20261110-142301.json}. Returns the new location.
	 */
	public static Path moveAside(Path file) throws IOException {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		String stamp = LocalDateTime.now().format(BACKUP_STAMP);
		Path target = file.resolveSibling(base + ".broken-" + stamp + extension);
		for (int i = 2; Files.exists(target); i++) {
			target = file.resolveSibling(base + ".broken-" + stamp + "-" + i + extension);
		}
		Files.move(file, target);
		return target;
	}

	private FileUtil() {
	}

	public static void writeAtomically(Path target, byte[] data) throws IOException {
		Path parent = target.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
		try {
			Files.write(temp, data);
			moveReplacing(temp, target);
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	public static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Restricts a file to its owner where the file system supports POSIX permissions (no-op on Windows). */
	public static void restrictToOwner(Path file) {
		try {
			Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException | IOException ignored) {
			// Windows: files under the user profile are already private to the user.
		}
	}

	public static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** Removes characters that are illegal in file names on any common OS. */
	public static String sanitizeFileName(String name) {
		String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
		return cleaned.isEmpty() ? "unnamed" : cleaned;
	}

	public static String humanSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		String[] units = {"KB", "MB", "GB", "TB"};
		double value = bytes;
		int unit = -1;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
	}
}
