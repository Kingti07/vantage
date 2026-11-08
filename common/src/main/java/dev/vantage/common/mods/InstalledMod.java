package dev.vantage.common.mods;

import java.nio.file.Path;
import java.util.List;

/**
 * A jar found in an instance's mods folder.
 *
 * @param file        current path (ends with {@code .jar} or {@code .jar.disabled})
 * @param enabled     false when the file carries the {@code .disabled} suffix
 * @param loader      which loader the jar was built for ("fabric", "quilt", "forge", "neoforge" or "unknown")
 * @param metadata    parsed fabric.mod.json, or null for non-Fabric jars
 * @param providedIds ids contributed by jar-in-jar modules (e.g. the individual Fabric API modules)
 * @param sha1        SHA-1 of the file, used to identify it on Modrinth
 * @param indexEntry  Modrinth origin recorded at install time (or identified by hash), null if unknown
 * @param pending     change queued for the next launch because the file is locked by the running game, or null
 */
public record InstalledMod(
	Path file,
	boolean enabled,
	String loader,
	ModMetadata metadata,
	List<String> providedIds,
	String sha1,
	long size,
	ModIndex.Entry indexEntry,
	PendingOperations.Action pending
) {
	public static final String DISABLED_SUFFIX = ".disabled";

	/** File name without the {@code .disabled} suffix; stable across enable/disable. */
	public String baseFileName() {
		String name = file.getFileName().toString();
		return name.endsWith(DISABLED_SUFFIX) ? name.substring(0, name.length() - DISABLED_SUFFIX.length()) : name;
	}

	public String modId() {
		return metadata != null ? metadata.id() : null;
	}

	public String displayName() {
		if (indexEntry != null && indexEntry.title() != null) {
			return indexEntry.title();
		}
		if (metadata != null && metadata.name() != null) {
			return metadata.name();
		}
		return baseFileName();
	}

	public String displayVersion() {
		if (metadata != null && metadata.version() != null) {
			return metadata.version();
		}
		return indexEntry != null ? indexEntry.versionNumber() : "?";
	}

	public String modrinthProjectId() {
		return indexEntry != null ? indexEntry.projectId() : null;
	}

	/** Whether the mod will be active on the next launch, taking queued changes into account. */
	public boolean effectiveEnabled() {
		if (pending == null) {
			return enabled;
		}
		return switch (pending) {
			case DELETE, DISABLE -> false;
			case ENABLE -> true;
		};
	}

	public InstalledMod withPending(PendingOperations.Action action) {
		return new InstalledMod(file, enabled, loader, metadata, providedIds, sha1, size, indexEntry, action);
	}

	public InstalledMod withIndexEntry(ModIndex.Entry entry) {
		return new InstalledMod(file, enabled, loader, metadata, providedIds, sha1, size, entry, pending);
	}
}
