package dev.vantage.common.mods;

import java.util.List;

/**
 * Well-known performance mods offered as a one-click "performance pack". They are never bundled: each one is
 * downloaded from Modrinth at the user's request, under its own license, and only if a version exists for the
 * instance's Minecraft version and loader.
 */
public final class RecommendedMods {
	/** @param modId the Fabric mod id (from fabric.mod.json), used to find the installed mod */
	public record Recommendation(String slug, String modId, String title, String purpose, boolean defaultSelected) {
	}

	public static final List<Recommendation> PERFORMANCE = List.of(
		new Recommendation("sodium", "sodium", "Sodium", "Modern rendering engine – large FPS gains", true),
		new Recommendation("lithium", "lithium", "Lithium", "Game logic / tick optimizations", true),
		new Recommendation("immediatelyfast", "immediatelyfast", "ImmediatelyFast", "Faster immediate-mode rendering (HUD, text, entities)", true),
		new Recommendation("ferrite-core", "ferritecore", "FerriteCore", "Reduces memory usage", true),
		new Recommendation("entityculling", "entityculling", "EntityCulling", "Skips rendering of hidden entities and block entities", true),
		new Recommendation("moreculling", "moreculling", "More Culling", "Extra culling for leaves, item frames and more", false),
		new Recommendation("c2me-fabric", "c2me", "C2ME", "Faster, parallel chunk loading and generation", false)
	);

	private RecommendedMods() {
	}
}
