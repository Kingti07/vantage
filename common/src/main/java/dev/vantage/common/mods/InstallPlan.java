package dev.vantage.common.mods;

import dev.vantage.common.modrinth.model.ModrinthModels.Project;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;

import java.util.List;

/**
 * Result of resolving an install request: what will be downloaded, what is already present and what blocks
 * the installation. Nothing is written to disk until {@link ModManager#execute} is called with a plan that
 * has no errors.
 *
 * @param root         the version the user asked for
 * @param rootProject  project metadata of the requested mod (for display / index)
 * @param toInstall    root + every required dependency that is not installed yet, in install order
 * @param replaces     installed mods that will be removed because a newer version of the same project is installed
 * @param satisfied    names of required dependencies that are already installed
 * @param problems     blocking errors and non-blocking warnings
 */
public record InstallPlan(
	Version root,
	Project rootProject,
	List<PlannedFile> toInstall,
	List<InstalledMod> replaces,
	List<String> satisfied,
	List<ModProblem> problems
) {
	public record PlannedFile(Version version, String title, String iconUrl, boolean dependency) {
	}

	public boolean canInstall() {
		return root != null && problems.stream().noneMatch(ModProblem::isError);
	}

	public long downloadSize() {
		return toInstall.stream().mapToLong(f -> f.version().primaryFile() != null ? f.version().primaryFile().size() : 0).sum();
	}
}
