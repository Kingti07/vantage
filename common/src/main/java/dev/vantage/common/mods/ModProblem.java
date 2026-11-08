package dev.vantage.common.mods;

/**
 * A problem found while analysing a mods folder or planning an installation.
 *
 * @param severity ERROR problems prevent the game from starting (or an install from proceeding)
 * @param fileName the jar the problem belongs to, may be null for plan-level problems
 */
public record ModProblem(Severity severity, Kind kind, String fileName, String message) {
	public enum Severity {WARNING, ERROR}

	public enum Kind {
		WRONG_LOADER,
		INCOMPATIBLE_GAME_VERSION,
		MISSING_DEPENDENCY,
		DEPENDENCY_VERSION,
		BREAKS,
		DUPLICATE,
		UNREADABLE,
		NO_COMPATIBLE_VERSION,
		INCOMPATIBLE_INSTALLED,
		EXTERNAL_DEPENDENCY,
		DOWNLOAD_FAILED
	}

	public static ModProblem error(Kind kind, String fileName, String message) {
		return new ModProblem(Severity.ERROR, kind, fileName, message);
	}

	public static ModProblem warning(Kind kind, String fileName, String message) {
		return new ModProblem(Severity.WARNING, kind, fileName, message);
	}

	public boolean isError() {
		return severity == Severity.ERROR;
	}
}
