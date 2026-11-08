package dev.vantage.common.modrinth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builder for Modrinth's /search endpoint. Facets are AND-ed between groups and OR-ed within a group,
 * exactly as the API defines them.
 */
public final class SearchQuery {
	public enum Sort {
		RELEVANCE("relevance", "Relevance"),
		DOWNLOADS("downloads", "Downloads"),
		FOLLOWS("follows", "Popularity"),
		NEWEST("newest", "Newest"),
		UPDATED("updated", "Recently updated");

		public final String apiName;
		public final String label;

		Sort(String apiName, String label) {
			this.apiName = apiName;
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private String query = "";
	private String projectType = "mod";
	private final Set<String> loaders = new LinkedHashSet<>();
	private final Set<String> gameVersions = new LinkedHashSet<>();
	private final Set<String> categories = new LinkedHashSet<>();
	private boolean clientSideOnly;
	private Sort sort = Sort.RELEVANCE;
	private int offset;
	private int limit = 20;

	public SearchQuery query(String query) {
		this.query = query == null ? "" : query.trim();
		return this;
	}

	public SearchQuery projectType(String projectType) {
		this.projectType = projectType;
		return this;
	}

	public SearchQuery loader(String loader) {
		if (loader != null && !loader.isBlank()) {
			loaders.add(loader);
		}
		return this;
	}

	public SearchQuery gameVersion(String version) {
		if (version != null && !version.isBlank()) {
			gameVersions.add(version);
		}
		return this;
	}

	public SearchQuery category(String category) {
		if (category != null && !category.isBlank()) {
			categories.add(category);
		}
		return this;
	}

	/** Restrict to projects that work on the client (client_side != unsupported). */
	public SearchQuery clientSideOnly(boolean value) {
		this.clientSideOnly = value;
		return this;
	}

	public SearchQuery sort(Sort sort) {
		this.sort = sort;
		return this;
	}

	public SearchQuery page(int offset, int limit) {
		this.offset = Math.max(0, offset);
		this.limit = Math.max(1, Math.min(100, limit));
		return this;
	}

	public String query() {
		return query;
	}

	public Sort sort() {
		return sort;
	}

	public int offset() {
		return offset;
	}

	public int limit() {
		return limit;
	}

	/** Facets in Modrinth's nested-array format, e.g. [["project_type:mod"],["categories:fabric"]]. */
	public List<List<String>> facets() {
		List<List<String>> facets = new ArrayList<>();
		if (projectType != null) {
			facets.add(List.of("project_type:" + projectType));
		}
		if (!loaders.isEmpty()) {
			// Loaders are stored as categories in the search index.
			facets.add(loaders.stream().map(l -> "categories:" + l).toList());
		}
		if (!gameVersions.isEmpty()) {
			facets.add(gameVersions.stream().map(v -> "versions:" + v).toList());
		}
		for (String category : categories) {
			facets.add(List.of("categories:" + category));
		}
		if (clientSideOnly) {
			facets.add(List.of("client_side:required", "client_side:optional"));
		}
		return facets;
	}
}
