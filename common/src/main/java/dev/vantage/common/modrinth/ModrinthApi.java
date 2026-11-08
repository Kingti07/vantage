package dev.vantage.common.modrinth;

import com.google.gson.reflect.TypeToken;
import dev.vantage.common.modrinth.model.ModrinthModels.Category;
import dev.vantage.common.modrinth.model.ModrinthModels.GameVersionTag;
import dev.vantage.common.modrinth.model.ModrinthModels.HashLookup;
import dev.vantage.common.modrinth.model.ModrinthModels.LoaderTag;
import dev.vantage.common.modrinth.model.ModrinthModels.Project;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchResponse;
import dev.vantage.common.modrinth.model.ModrinthModels.TeamMember;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.HttpException;
import dev.vantage.common.util.Json;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for the official Modrinth v2 REST API. Read-only endpoints are cached briefly in memory
 * to keep the UI snappy and stay well below Modrinth's rate limit (300 requests / minute).
 */
public final class ModrinthApi {
	public static final String DEFAULT_BASE = "https://api.modrinth.com/v2";
	private static final Duration CACHE_TTL = Duration.ofMinutes(5);

	private final String base;
	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

	public ModrinthApi() {
		this(DEFAULT_BASE);
	}

	public ModrinthApi(String base) {
		this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	public SearchResponse search(SearchQuery query) throws IOException {
		StringBuilder url = new StringBuilder(base).append("/search?limit=").append(query.limit())
			.append("&offset=").append(query.offset())
			.append("&index=").append(query.sort().apiName);
		if (!query.query().isEmpty()) {
			url.append("&query=").append(Http.encode(query.query()));
		}
		List<List<String>> facets = query.facets();
		if (!facets.isEmpty()) {
			url.append("&facets=").append(Http.encode(Json.SNAKE.toJson(facets)));
		}
		return cachedGet(url.toString(), SearchResponse.class);
	}

	/** @param idOrSlug project id or slug */
	public Project project(String idOrSlug) throws IOException {
		return cachedGet(base + "/project/" + Http.encode(idOrSlug), Project.class);
	}

	public List<Project> projects(Collection<String> ids) throws IOException {
		if (ids.isEmpty()) {
			return List.of();
		}
		String url = base + "/projects?ids=" + Http.encode(Json.SNAKE.toJson(ids));
		return cachedGet(url, new TypeToken<List<Project>>() {
		}.getType());
	}

	/** All versions of a project, newest first, optionally filtered by loader and game version. */
	public List<Version> projectVersions(String idOrSlug, String loader, String gameVersion) throws IOException {
		StringBuilder url = new StringBuilder(base).append("/project/").append(Http.encode(idOrSlug)).append("/version");
		String separator = "?";
		if (loader != null) {
			url.append(separator).append("loaders=").append(Http.encode(Json.SNAKE.toJson(List.of(loader))));
			separator = "&";
		}
		if (gameVersion != null) {
			url.append(separator).append("game_versions=").append(Http.encode(Json.SNAKE.toJson(List.of(gameVersion))));
		}
		return cachedGet(url.toString(), new TypeToken<List<Version>>() {
		}.getType());
	}

	public Version version(String versionId) throws IOException {
		return cachedGet(base + "/version/" + Http.encode(versionId), Version.class);
	}

	public List<TeamMember> members(String idOrSlug) throws IOException {
		return cachedGet(base + "/project/" + Http.encode(idOrSlug) + "/members", new TypeToken<List<TeamMember>>() {
		}.getType());
	}

	public List<Category> categories() throws IOException {
		return cachedGet(base + "/tag/category", new TypeToken<List<Category>>() {
		}.getType());
	}

	public List<GameVersionTag> gameVersions() throws IOException {
		return cachedGet(base + "/tag/game_version", new TypeToken<List<GameVersionTag>>() {
		}.getType());
	}

	public List<LoaderTag> loaders() throws IOException {
		return cachedGet(base + "/tag/loader", new TypeToken<List<LoaderTag>>() {
		}.getType());
	}

	/** Identifies installed files by hash. Returns hash -> version for every file Modrinth knows. */
	public Map<String, Version> versionsByHash(List<String> sha1Hashes) throws IOException {
		if (sha1Hashes.isEmpty()) {
			return Map.of();
		}
		Map<String, Version> result = Http.postJson(URI.create(base + "/version_files"),
			new HashLookup(sha1Hashes, "sha1", null, null), new TypeToken<Map<String, Version>>() {
			}.getType(), Json.SNAKE);
		return result == null ? Map.of() : result;
	}

	/** For each installed file hash, the newest version compatible with the given loader and game version. */
	public Map<String, Version> latestVersionsByHash(List<String> sha1Hashes, String loader, String gameVersion) throws IOException {
		if (sha1Hashes.isEmpty()) {
			return Map.of();
		}
		Map<String, Version> result = Http.postJson(URI.create(base + "/version_files/update"),
			new HashLookup(sha1Hashes, "sha1", List.of(loader), List.of(gameVersion)), new TypeToken<Map<String, Version>>() {
			}.getType(), Json.SNAKE);
		return result == null ? new HashMap<>() : result;
	}

	public void clearCache() {
		cache.clear();
	}

	/** True if the exception means "this project/version does not exist". */
	public static boolean isNotFound(IOException e) {
		return e instanceof HttpException http && http.status() == 404;
	}

	@SuppressWarnings("unchecked")
	private <T> T cachedGet(String url, java.lang.reflect.Type type) throws IOException {
		CacheEntry entry = cache.get(url);
		Instant now = Instant.now();
		if (entry != null && entry.expires.isAfter(now)) {
			return (T) entry.value;
		}
		T value = Http.getJson(URI.create(url), type, Json.SNAKE);
		if (cache.size() > 500) {
			cache.entrySet().removeIf(e -> e.getValue().expires.isBefore(now));
		}
		cache.put(url, new CacheEntry(value, now.plus(CACHE_TTL)));
		return value;
	}

	private record CacheEntry(Object value, Instant expires) {
	}
}
