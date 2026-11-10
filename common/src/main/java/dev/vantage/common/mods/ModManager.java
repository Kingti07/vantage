package dev.vantage.common.mods;

import dev.vantage.common.modrinth.ModrinthApi;
import dev.vantage.common.modrinth.model.ModrinthModels.Dependency;
import dev.vantage.common.modrinth.model.ModrinthModels.Project;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.modrinth.model.ModrinthModels.VersionFile;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.version.VersionPredicate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages one mods folder for one Minecraft version + loader combination:
 * scanning, compatibility analysis, Modrinth installs with dependency resolution, updates, removal and
 * enable/disable. Shared by the launcher and the in-game mod browser.
 * <p>
 * Safety rule: only versions that Modrinth lists for <em>both</em> the instance's loader and game version are
 * ever installed, and a plan with errors cannot be executed.
 */
public final class ModManager {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ModManager.class);
	/** Ids the loader itself provides; they never need to be installed. */
	private static final Set<String> BUILTIN_IDS = Set.of("minecraft", "java", "fabricloader", "fabric-loader", "mixinextras");

	private final Path modsDir;
	private final ModrinthApi api;
	private final String gameVersion;
	private final String loader;
	private final ModIndex index;
	private final PendingOperations pending;

	public ModManager(Path modsDir, ModrinthApi api, String gameVersion, String loader) {
		this.modsDir = modsDir;
		this.api = api;
		this.gameVersion = gameVersion;
		this.loader = loader;
		this.index = new ModIndex(modsDir);
		this.pending = new PendingOperations(modsDir);
	}

	public Path modsDir() {
		return modsDir;
	}

	public String gameVersion() {
		return gameVersion;
	}

	public String loader() {
		return loader;
	}

	public ModrinthApi api() {
		return api;
	}

	/** Scans the mods folder. Cheap enough to call on every UI refresh (hashing is the main cost). */
	public synchronized List<InstalledMod> installed() throws IOException {
		Files.createDirectories(modsDir);
		index.load();
		pending.load();
		List<InstalledMod> mods = new ArrayList<>();
		for (InstalledMod mod : ModScanner.scan(modsDir, index)) {
			mods.add(mod.withPending(pending.get(mod.baseFileName())));
		}
		return mods;
	}

	// ------------------------------------------------------------------------------------------------ analysis

	/** Finds everything that would stop the game from loading, or that is likely to cause trouble. */
	public List<ModProblem> analyze(List<InstalledMod> mods) {
		List<ModProblem> problems = new ArrayList<>();
		Map<String, List<InstalledMod>> byId = new HashMap<>();
		Map<String, String> versions = new HashMap<>();
		Set<String> disabledIds = new HashSet<>();
		for (InstalledMod mod : mods) {
			if (mod.metadata() == null || mod.metadata().id() == null) {
				continue;
			}
			if (!mod.effectiveEnabled()) {
				disabledIds.add(mod.metadata().id());
				continue;
			}
			byId.computeIfAbsent(mod.metadata().id(), k -> new ArrayList<>()).add(mod);
			versions.put(mod.metadata().id(), mod.metadata().version());
			for (String provided : mod.metadata().provides()) {
				versions.putIfAbsent(provided, mod.metadata().version());
			}
			for (String provided : mod.providedIds()) {
				versions.putIfAbsent(provided, null);
			}
		}

		for (InstalledMod mod : mods) {
			if (!mod.effectiveEnabled()) {
				continue;
			}
			String file = mod.file().getFileName().toString();
			switch (mod.loader()) {
				case "forge" -> problems.add(ModProblem.error(ModProblem.Kind.WRONG_LOADER, file, "Forge mod – Fabric cannot load it."));
				case "neoforge" -> problems.add(ModProblem.error(ModProblem.Kind.WRONG_LOADER, file, "NeoForge mod – Fabric cannot load it."));
				case "quilt" -> problems.add(ModProblem.error(ModProblem.Kind.WRONG_LOADER, file, "Quilt-only mod – Fabric cannot load it."));
				case "unknown" -> problems.add(ModProblem.warning(ModProblem.Kind.UNREADABLE, file, "Not a recognisable Fabric mod."));
				default -> {
				}
			}
			ModMetadata meta = mod.metadata();
			if (meta == null || meta.id() == null) {
				continue;
			}
			List<InstalledMod> sameId = byId.getOrDefault(meta.id(), List.of());
			if (sameId.size() > 1 && sameId.getFirst() == mod) {
				problems.add(ModProblem.error(ModProblem.Kind.DUPLICATE, file,
					"Mod id '" + meta.id() + "' is installed " + sameId.size() + " times: "
						+ String.join(", ", sameId.stream().map(m -> m.file().getFileName().toString()).toList())));
			}
			for (Map.Entry<String, List<String>> dep : meta.depends().entrySet()) {
				String depId = dep.getKey();
				if (depId.equals("minecraft")) {
					if (VersionPredicate.anyOf(dep.getValue(), gameVersion) == VersionPredicate.Result.UNSATISFIED) {
						problems.add(ModProblem.error(ModProblem.Kind.INCOMPATIBLE_GAME_VERSION, file,
							meta.name() + " requires Minecraft " + String.join(" or ", dep.getValue()) + " (instance: " + gameVersion + ")."));
					}
					continue;
				}
				if (BUILTIN_IDS.contains(depId)) {
					continue;
				}
				if (!versions.containsKey(depId)) {
					String hint = disabledIds.contains(depId) ? " It is installed but disabled." : "";
					problems.add(ModProblem.error(ModProblem.Kind.MISSING_DEPENDENCY, file,
						meta.name() + " requires '" + depId + "' " + String.join(" or ", dep.getValue()) + "." + hint));
					continue;
				}
				String present = versions.get(depId);
				if (present != null && VersionPredicate.anyOf(dep.getValue(), present) == VersionPredicate.Result.UNSATISFIED) {
					problems.add(ModProblem.error(ModProblem.Kind.DEPENDENCY_VERSION, file,
						meta.name() + " requires " + depId + " " + String.join(" or ", dep.getValue()) + " but " + present + " is installed."));
				}
			}
			for (Map.Entry<String, List<String>> broken : meta.breaks().entrySet()) {
				String present = versions.get(broken.getKey());
				if (versions.containsKey(broken.getKey()) && (present == null
					|| VersionPredicate.anyOf(broken.getValue(), present) == VersionPredicate.Result.SATISFIED)) {
					problems.add(ModProblem.error(ModProblem.Kind.BREAKS, file,
						meta.name() + " is incompatible with " + broken.getKey() + (present != null ? " " + present : "") + "."));
				}
			}
		}
		return problems;
	}

	/** Mods whose fabric.mod.json requires the given mod (used to warn before removal). */
	public List<InstalledMod> dependents(InstalledMod target, List<InstalledMod> mods) {
		if (target.metadata() == null) {
			return List.of();
		}
		Set<String> ids = new HashSet<>(target.metadata().provides());
		ids.add(target.metadata().id());
		ids.addAll(target.providedIds());
		List<InstalledMod> result = new ArrayList<>();
		for (InstalledMod mod : mods) {
			if (mod == target || mod.metadata() == null || !mod.effectiveEnabled()) {
				continue;
			}
			if (mod.metadata().depends().keySet().stream().anyMatch(ids::contains)) {
				result.add(mod);
			}
		}
		return result;
	}

	// ------------------------------------------------------------------------------------------------ planning

	/** Newest compatible version of a project, preferring releases over betas/alphas; null if none exists. */
	public Version bestVersion(String projectIdOrSlug) throws IOException {
		List<Version> versions = api.projectVersions(projectIdOrSlug, loader, gameVersion);
		Version fallback = null;
		for (Version version : versions) {
			if (!version.supports(gameVersion, loader) || version.primaryFile() == null) {
				continue;
			}
			if ("release".equals(version.versionType())) {
				return version;
			}
			if (fallback == null) {
				fallback = version;
			}
		}
		return fallback;
	}

	/**
	 * Resolves what installing a project (optionally a specific version) involves. Never touches the disk.
	 */
	public InstallPlan plan(String projectIdOrSlug, String versionId) throws IOException {
		List<ModProblem> problems = new ArrayList<>();
		Project project = api.project(projectIdOrSlug);
		if (!"mod".equals(project.projectType())) {
			problems.add(ModProblem.error(ModProblem.Kind.NO_COMPATIBLE_VERSION, null,
				project.title() + " is a " + project.projectType() + ", not a mod."));
			return new InstallPlan(null, project, List.of(), List.of(), List.of(), problems);
		}
		if ("unsupported".equals(project.clientSide())) {
			problems.add(ModProblem.error(ModProblem.Kind.NO_COMPATIBLE_VERSION, null,
				project.title() + " is a server-only mod and does nothing on the client."));
			return new InstallPlan(null, project, List.of(), List.of(), List.of(), problems);
		}
		Version root = versionId != null ? api.version(versionId) : bestVersion(project.id());
		if (root == null || !root.supports(gameVersion, loader)) {
			String what = root == null ? "No version of " + project.title() : project.title() + " " + root.versionNumber();
			problems.add(ModProblem.error(ModProblem.Kind.NO_COMPATIBLE_VERSION, null,
				what + " supports " + capitalize(loader) + " on Minecraft " + gameVersion + "."));
			return new InstallPlan(root, project, List.of(), List.of(), List.of(), problems);
		}

		List<InstalledMod> installed = installed();
		identify(installed);
		installed = installed();
		Map<String, InstalledMod> byProject = new HashMap<>();
		for (InstalledMod mod : installed) {
			if (mod.modrinthProjectId() != null && mod.pending() != PendingOperations.Action.DELETE) {
				byProject.put(mod.modrinthProjectId(), mod);
			}
		}

		List<InstallPlan.PlannedFile> toInstall = new ArrayList<>();
		List<InstalledMod> replaces = new ArrayList<>();
		List<String> satisfied = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		resolve(root, project.title(), project.iconUrl(), false, byProject, visited, toInstall, replaces, satisfied, problems);
		return new InstallPlan(root, project, toInstall, replaces, satisfied, problems);
	}

	private void resolve(Version version, String title, String iconUrl, boolean dependency, Map<String, InstalledMod> byProject,
		Set<String> visited, List<InstallPlan.PlannedFile> toInstall, List<InstalledMod> replaces, List<String> satisfied,
		List<ModProblem> problems) throws IOException {
		visited.add(version.projectId());
		InstalledMod existing = byProject.get(version.projectId());
		if (existing != null) {
			if (existing.indexEntry() != null && version.id().equals(existing.indexEntry().versionId())) {
				if (!dependency) {
					problems.add(ModProblem.warning(ModProblem.Kind.DUPLICATE, existing.file().getFileName().toString(),
						title + " " + version.versionNumber() + " is already installed."));
				}
				return;
			}
			replaces.add(existing);
		}
		if (version.primaryFile() == null) {
			problems.add(ModProblem.error(ModProblem.Kind.DOWNLOAD_FAILED, null, title + " " + version.versionNumber() + " has no downloadable file."));
			return;
		}
		toInstall.add(new InstallPlan.PlannedFile(version, title, iconUrl, dependency));

		if (version.dependencies() == null) {
			return;
		}
		for (Dependency dep : version.dependencies()) {
			String projectId = dep.projectId();
			Version pinned = null;
			if (projectId == null && dep.versionId() != null) {
				pinned = api.version(dep.versionId());
				projectId = pinned.projectId();
			}
			if (dep.isIncompatible()) {
				InstalledMod clash = projectId != null ? byProject.get(projectId) : null;
				if (clash != null && clash.effectiveEnabled()) {
					problems.add(ModProblem.error(ModProblem.Kind.INCOMPATIBLE_INSTALLED, clash.file().getFileName().toString(),
						title + " is incompatible with the installed mod " + clash.displayName() + "."));
				}
				continue;
			}
			if (!dep.isRequired()) {
				continue;
			}
			if (projectId == null) {
				problems.add(ModProblem.warning(ModProblem.Kind.EXTERNAL_DEPENDENCY, dep.fileName(),
					title + " requires a file that is not hosted on Modrinth" + (dep.fileName() != null ? ": " + dep.fileName() : ".")));
				continue;
			}
			if (visited.contains(projectId)) {
				continue;
			}
			InstalledMod present = byProject.get(projectId);
			if (present != null) {
				visited.add(projectId);
				satisfied.add(present.displayName());
				if (!present.effectiveEnabled()) {
					problems.add(ModProblem.warning(ModProblem.Kind.MISSING_DEPENDENCY, present.file().getFileName().toString(),
						present.displayName() + " is required by " + title + " but is disabled. Enable it before playing."));
				}
				continue;
			}
			if (dep.versionId() != null && pinned == null) {
				pinned = api.version(dep.versionId());
			}
			Version candidate = pinned != null && pinned.supports(gameVersion, loader) ? pinned : bestVersion(projectId);
			Project depProject = api.project(projectId);
			if (candidate == null) {
				problems.add(ModProblem.error(ModProblem.Kind.NO_COMPATIBLE_VERSION, null,
					"Required dependency " + depProject.title() + " has no version for " + capitalize(loader) + " " + gameVersion + "."));
				visited.add(projectId);
				continue;
			}
			resolve(candidate, depProject.title(), depProject.iconUrl(), true, byProject, visited, toInstall, replaces, satisfied, problems);
		}
	}

	// ------------------------------------------------------------------------------------------------ execution

	/**
	 * Downloads and installs every file of a plan. On failure, files written by this call are removed again so
	 * the mods folder is never left half-installed.
	 */
	public synchronized List<Path> execute(InstallPlan plan, dev.vantage.common.util.ProgressListener progress) throws IOException {
		if (!plan.canInstall()) {
			throw new IllegalStateException("Install plan has blocking problems");
		}
		Files.createDirectories(modsDir);
		index.load();
		long total = Math.max(1, plan.downloadSize());
		AtomicLong done = new AtomicLong();
		List<Path> written = new ArrayList<>();
		Map<Path, InstallPlan.PlannedFile> installedFiles = new LinkedHashMap<>();
		try {
			for (InstallPlan.PlannedFile planned : plan.toInstall()) {
				VersionFile file = planned.version().primaryFile();
				Path target = modsDir.resolve(FileUtil.sanitizeFileName(file.filename()));
				progress.status("Downloading " + planned.title() + " " + planned.version().versionNumber());
				boolean existed = Files.exists(target);
				String algorithm = file.hashes() != null && file.hashes().sha512() != null ? "SHA-512" : "SHA-1";
				String hash = file.hashes() == null ? null : "SHA-512".equals(algorithm) ? file.hashes().sha512() : file.hashes().sha1();
				Http.download(URI.create(file.url()), target, algorithm, hash, bytes -> progress.progress(done.addAndGet(bytes) / (double) total),
					progress::isCancelled);
				if (!existed) {
					written.add(target);
				}
				installedFiles.put(target, planned);
			}
		} catch (IOException | RuntimeException e) {
			for (Path path : written) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException cleanup) {
					LOGGER.warn("Could not clean up {} after failed install", path, cleanup);
				}
			}
			// Partial downloads must not stay in the mods folder.
			for (InstallPlan.PlannedFile planned : plan.toInstall()) {
				Path part = Http.partFile(modsDir.resolve(FileUtil.sanitizeFileName(planned.version().primaryFile().filename())));
				try {
					Files.deleteIfExists(part);
				} catch (IOException cleanup) {
					LOGGER.warn("Could not clean up {} after failed install", part, cleanup);
				}
			}
			throw e;
		}

		progress.status("Finishing installation");
		Set<Path> newFiles = installedFiles.keySet();
		for (InstalledMod old : plan.replaces()) {
			if (!newFiles.contains(old.file()) && !newFiles.contains(modsDir.resolve(old.baseFileName()))) {
				deleteOrQueue(old);
				index.remove(old.baseFileName());
			}
		}
		for (Map.Entry<Path, InstallPlan.PlannedFile> entry : installedFiles.entrySet()) {
			InstallPlan.PlannedFile planned = entry.getValue();
			VersionFile file = planned.version().primaryFile();
			String base = entry.getKey().getFileName().toString();
			index.put(base, new ModIndex.Entry(planned.version().projectId(), planned.version().id(), planned.version().versionNumber(),
				planned.title(), planned.iconUrl(), file.hashes() != null ? file.hashes().sha1() : null, planned.dependency()));
			pending.put(base, null);
		}
		index.save();
		progress.progress(1);
		progress.status("Installed " + plan.toInstall().size() + " file(s)");
		return List.copyOf(newFiles);
	}

	/** Removes a mod. If the running game has the jar locked, removal is queued for the next launch. */
	public synchronized void remove(InstalledMod mod) throws IOException {
		index.load();
		deleteOrQueue(mod);
		index.remove(mod.baseFileName());
		index.save();
	}

	/** Enables or disables a mod by toggling the {@code .disabled} suffix (queued if the jar is locked). */
	public synchronized void setEnabled(InstalledMod mod, boolean enabled) throws IOException {
		pending.load();
		if (mod.enabled() == enabled) {
			pending.put(mod.baseFileName(), null);
			return;
		}
		Path target = modsDir.resolve(enabled ? mod.baseFileName() : mod.baseFileName() + InstalledMod.DISABLED_SUFFIX);
		try {
			FileUtil.moveReplacing(mod.file(), target);
			pending.put(mod.baseFileName(), null);
		} catch (IOException e) {
			LOGGER.info("{} is locked, queueing {} for next launch", mod.file().getFileName(), enabled ? "enable" : "disable");
			pending.put(mod.baseFileName(), enabled ? PendingOperations.Action.ENABLE : PendingOperations.Action.DISABLE);
		}
	}

	/** Applies changes queued while the game was running. Call before launching. */
	public synchronized int applyPendingOperations() {
		pending.load();
		return pending.applyAll();
	}

	private void deleteOrQueue(InstalledMod mod) throws IOException {
		pending.load();
		try {
			Files.deleteIfExists(mod.file());
			pending.put(mod.baseFileName(), null);
		} catch (IOException e) {
			LOGGER.info("{} is locked, queueing removal for next launch", mod.file().getFileName());
			pending.put(mod.baseFileName(), PendingOperations.Action.DELETE);
		}
	}

	// ------------------------------------------------------------------------------------------------ updates

	/**
	 * Looks up unindexed jars on Modrinth by SHA-1 and records their origin, so manually added mods also get
	 * version information and updates. Returns the number of newly identified mods.
	 */
	public synchronized int identify(List<InstalledMod> mods) {
		Map<String, InstalledMod> unknown = new LinkedHashMap<>();
		for (InstalledMod mod : mods) {
			if (mod.indexEntry() == null && mod.sha1() != null) {
				unknown.put(mod.sha1(), mod);
			}
		}
		if (unknown.isEmpty()) {
			return 0;
		}
		try {
			Map<String, Version> found = api.versionsByHash(new ArrayList<>(unknown.keySet()));
			if (found.isEmpty()) {
				return 0;
			}
			Map<String, Project> projects = new HashMap<>();
			for (Project project : api.projects(found.values().stream().map(Version::projectId).distinct().toList())) {
				projects.put(project.id(), project);
			}
			index.load();
			for (Map.Entry<String, Version> entry : found.entrySet()) {
				InstalledMod mod = unknown.get(entry.getKey());
				Version version = entry.getValue();
				Project project = projects.get(version.projectId());
				index.put(mod.baseFileName(), new ModIndex.Entry(version.projectId(), version.id(), version.versionNumber(),
					project != null ? project.title() : null, project != null ? project.iconUrl() : null, mod.sha1(), false));
			}
			index.save();
			return found.size();
		} catch (IOException e) {
			LOGGER.warn("Could not identify mods on Modrinth: {}", e.getMessage());
			return 0;
		}
	}

	/** Returns installed mod -> newer compatible Modrinth version, for every mod that has an update. */
	public Map<InstalledMod, Version> findUpdates(List<InstalledMod> mods) throws IOException {
		Map<String, InstalledMod> byHash = new LinkedHashMap<>();
		for (InstalledMod mod : mods) {
			if (mod.sha1() != null && "fabric".equals(mod.loader()) && mod.pending() != PendingOperations.Action.DELETE) {
				byHash.put(mod.sha1(), mod);
			}
		}
		Map<String, Version> latest = api.latestVersionsByHash(new ArrayList<>(byHash.keySet()), loader, gameVersion);
		Map<InstalledMod, Version> updates = new LinkedHashMap<>();
		for (Map.Entry<String, Version> entry : latest.entrySet()) {
			InstalledMod mod = byHash.get(entry.getKey());
			Version version = entry.getValue();
			if (mod == null || version == null || !version.supports(gameVersion, loader)) {
				continue;
			}
			VersionFile primary = version.primaryFile();
			boolean sameFile = primary != null && primary.hashes() != null && entry.getKey().equalsIgnoreCase(primary.hashes().sha1());
			boolean sameVersion = mod.indexEntry() != null && version.id().equals(mod.indexEntry().versionId());
			if (!sameFile && !sameVersion) {
				updates.put(mod, version);
			}
		}
		return updates;
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
