package dev.vantage.launcher.game;

import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.InstalledMod;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.mods.ModProblem;
import dev.vantage.common.mods.RecommendedMods;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.settings.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Brings an installation's mods in line with its performance settings before each launch.
 * <ul>
 *   <li><b>Performance mods on</b> (default for new installations): Sodium, Lithium, ImmediatelyFast, FerriteCore and
 *       EntityCulling are installed from Modrinth once and enabled. Removing one by hand afterwards is respected.</li>
 *   <li><b>Performance mods off</b>: those mods are disabled (not deleted), so turning the option on again is quick.</li>
 *   <li><b>Vulkan renderer</b> (experimental): VulkanMod is installed and enabled; mods that hook the OpenGL renderer
 *       (Sodium, Iris, ...) are disabled. Switching back re-enables exactly the mods Vulkan mode disabled.</li>
 * </ul>
 * The mods are never bundled: they are downloaded from Modrinth under their own licenses. Missing internet only
 * postpones the installation to the next launch, it never blocks playing.
 */
public final class PerformanceMods {
	private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceMods.class);
	public static final String VULKANMOD_SLUG = "vulkanmod";
	public static final String VULKANMOD_ID = "vulkanmod";
	/** Fabric mod ids that hook the OpenGL renderer and cannot run together with VulkanMod. */
	static final Set<String> OPENGL_ONLY = Set.of("sodium", "iris", "immediatelyfast", "sodium-extra", "nvidium", "indium", "moreculling");

	private PerformanceMods() {
	}

	/**
	 * @return notes for the console (what was installed or could not be installed)
	 */
	public static List<String> apply(Instance instance, ModManager mods, ProgressListener progress) throws IOException {
		List<String> notes = new ArrayList<>();
		boolean vulkan = instance.renderer == Instance.Renderer.VULKAN;
		List<InstalledMod> installed = current(mods);

		// 1. Renderer: VulkanMod on/off and the OpenGL-only mods the other way round.
		for (InstalledMod mod : installed) {
			String id = mod.modId();
			if (id == null) {
				continue;
			}
			if (VULKANMOD_ID.equals(id) && mod.enabled() != vulkan) {
				mods.setEnabled(mod, vulkan);
			} else if (OPENGL_ONLY.contains(id) && vulkan && mod.enabled()) {
				mods.setEnabled(mod, false);
				if (!instance.disabledForVulkan.contains(mod.baseFileName())) {
					instance.disabledForVulkan.add(mod.baseFileName());
				}
			} else if (!vulkan && !mod.enabled() && instance.disabledForVulkan.contains(mod.baseFileName())) {
				mods.setEnabled(mod, true);
			}
		}
		if (!vulkan) {
			instance.disabledForVulkan.clear();
		}

		// 2. Performance mods on/off.
		List<String> packIds = RecommendedMods.PERFORMANCE.stream().filter(RecommendedMods.Recommendation::defaultSelected)
			.map(RecommendedMods.Recommendation::modId).toList();
		if (!instance.performanceMods && instance.performanceModsInstalled) {
			for (InstalledMod mod : current(mods)) {
				if (packIds.contains(mod.modId()) && mod.enabled()) {
					mods.setEnabled(mod, false);
				}
			}
			instance.performanceModsInstalled = false;
		}

		// 3. Install what is missing.
		List<String> wanted = new ArrayList<>();
		if (vulkan && find(current(mods), VULKANMOD_ID) == null) {
			wanted.add(VULKANMOD_SLUG);
		}
		boolean installPack = instance.performanceMods && !instance.performanceModsInstalled && !vulkan;
		if (installPack) {
			for (RecommendedMods.Recommendation recommendation : RecommendedMods.PERFORMANCE) {
				if (!recommendation.defaultSelected()) {
					continue;
				}
				InstalledMod existing = find(current(mods), recommendation.modId());
				if (existing == null) {
					wanted.add(recommendation.slug());
				} else if (!existing.enabled()) {
					mods.setEnabled(existing, true);
				}
			}
		}
		boolean allInstalled = true;
		for (String slug : wanted) {
			progress.checkCancelled();
			try {
				InstallPlan plan = mods.plan(slug, null);
				if (!plan.canInstall()) {
					String reason = plan.problems().stream().filter(ModProblem::isError).map(ModProblem::message).findFirst().orElse("not available");
					notes.add("Skipped " + slug + ": " + reason);
					continue;
				}
				progress.status("Installing " + plan.rootProject().title());
				mods.execute(plan, progress);
				notes.add("Installed " + plan.rootProject().title());
			} catch (IOException e) {
				if (e instanceof dev.vantage.common.util.CancelledException) {
					throw e;
				}
				allInstalled = false;
				notes.add("Could not install " + slug + (Http.isNetworkUnavailable(e) ? " (offline, will retry next launch)" : ": " + e.getMessage()));
				LOGGER.warn("Could not install {}", slug, e);
			}
		}
		if (installPack && allInstalled) {
			instance.performanceModsInstalled = true;
		}
		return notes;
	}

	private static List<InstalledMod> current(ModManager mods) throws IOException {
		List<InstalledMod> installed = mods.installed();
		mods.identify(installed);
		return mods.installed();
	}

	private static InstalledMod find(List<InstalledMod> mods, String modId) {
		return mods.stream().filter(m -> modId.equals(m.modId())).findFirst().orElse(null);
	}
}
