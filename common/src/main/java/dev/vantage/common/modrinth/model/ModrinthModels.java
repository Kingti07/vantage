package dev.vantage.common.modrinth.model;

import java.util.List;
import java.util.Map;

/**
 * Data model for the Modrinth v2 API (https://docs.modrinth.com/api/). Field names are camelCase here and
 * mapped to snake_case by {@link dev.vantage.common.util.Json#SNAKE}. Any field may be {@code null} when the
 * API omits it.
 */
public final class ModrinthModels {
	private ModrinthModels() {
	}

	public record SearchResponse(List<SearchHit> hits, int offset, int limit, int totalHits) {
	}

	public record SearchHit(
		String projectId,
		String projectType,
		String slug,
		String author,
		String title,
		String description,
		List<String> categories,
		List<String> displayCategories,
		List<String> versions,
		long downloads,
		long follows,
		String iconUrl,
		String dateCreated,
		String dateModified,
		String latestVersion,
		String license,
		String clientSide,
		String serverSide,
		List<String> gallery,
		String featuredGallery,
		Integer color
	) {
	}

	public record License(String id, String name, String url) {
	}

	public record GalleryImage(String url, String rawUrl, boolean featured, String title, String description, String created, int ordering) {
	}

	public record Project(
		String id,
		String slug,
		String projectType,
		String team,
		String title,
		String description,
		String body,
		List<String> categories,
		List<String> additionalCategories,
		String clientSide,
		String serverSide,
		long downloads,
		long followers,
		String iconUrl,
		String issuesUrl,
		String sourceUrl,
		String wikiUrl,
		String discordUrl,
		String published,
		String updated,
		License license,
		List<String> versions,
		List<String> gameVersions,
		List<String> loaders,
		List<GalleryImage> gallery
	) {
	}

	public record Dependency(String versionId, String projectId, String fileName, String dependencyType) {
		public boolean isRequired() {
			return "required".equals(dependencyType);
		}

		public boolean isIncompatible() {
			return "incompatible".equals(dependencyType);
		}
	}

	public record Hashes(String sha1, String sha512) {
	}

	public record VersionFile(Hashes hashes, String url, String filename, boolean primary, long size, String fileType) {
	}

	public record Version(
		String id,
		String projectId,
		String authorId,
		String name,
		String versionNumber,
		String changelog,
		List<Dependency> dependencies,
		List<String> gameVersions,
		String versionType,
		List<String> loaders,
		boolean featured,
		String status,
		String datePublished,
		long downloads,
		List<VersionFile> files
	) {
		/** The file Modrinth marks as primary, or the first file. */
		public VersionFile primaryFile() {
			if (files == null || files.isEmpty()) {
				return null;
			}
			return files.stream().filter(VersionFile::primary).findFirst().orElse(files.getFirst());
		}

		public boolean supports(String gameVersion, String loader) {
			return gameVersions != null && gameVersions.contains(gameVersion)
				&& loaders != null && loaders.contains(loader);
		}
	}

	public record User(String id, String username, String name, String avatarUrl) {
	}

	public record TeamMember(String teamId, User user, String role, int ordering) {
	}

	public record Category(String icon, String name, String projectType, String header) {
	}

	public record GameVersionTag(String version, String versionType, String date, boolean major) {
	}

	public record LoaderTag(String icon, String name, List<String> supportedProjectTypes) {
	}

	/** Body of POST /version_files and /version_files/update. */
	public record HashLookup(List<String> hashes, String algorithm, List<String> loaders, List<String> gameVersions) {
	}

	/** Response of the hash lookup endpoints: hash -> version. */
	public interface HashResult extends Map<String, Version> {
	}
}
