package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.modrinth.ModService;
import dev.vantage.client.modrinth.RemoteTextures;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.common.modrinth.model.ModrinthModels.Project;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.mods.InstalledMod;
import dev.vantage.common.mods.RecommendedMods;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sodium, Lithium, ImmediatelyFast, FerriteCore, EntityCulling and more: each is offered only if Modrinth has
 * a version for this Minecraft version and Fabric, and installed through the normal dependency-checked flow.
 */
public final class PerformancePackScreen extends VScreen {
	private record Status(Project project, Version version, boolean installed) {
	}

	private final Map<RecommendedMods.Recommendation, Status> statuses = new LinkedHashMap<>();
	private String status = "Checking compatibility…";

	public PerformancePackScreen(Screen parent) {
		super("Performance pack", parent);
		load();
	}

	private void load() {
		ModService service = VantageClient.get().mods();
		service.run(() -> {
			List<InstalledMod> installed = service.manager().installed();
			Map<RecommendedMods.Recommendation, Status> result = new LinkedHashMap<>();
			for (RecommendedMods.Recommendation rec : RecommendedMods.PERFORMANCE) {
				try {
					Project project = service.api().project(rec.slug());
					Version version = service.manager().bestVersion(project.id());
					boolean present = installed.stream().anyMatch(m -> project.id().equals(m.modrinthProjectId()) && m.effectiveEnabled());
					result.put(rec, new Status(project, version, present));
				} catch (Exception e) {
					result.put(rec, new Status(null, null, false));
				}
			}
			return result;
		}, result -> {
			statuses.clear();
			statuses.putAll(result);
			status = "Mods are downloaded from Modrinth under their own licenses.";
			rebuildWidgets();
		}, error -> status = "Could not reach Modrinth: " + ModService.message(error));
	}

	@Override
	protected void build() {
		int y = 50;
		int x = width / 2 - 200;
		for (Map.Entry<RecommendedMods.Recommendation, Status> entry : statuses.entrySet()) {
			Status s = entry.getValue();
			if (s.project() != null && s.version() != null && !s.installed()) {
				Project project = s.project();
				add(new Button(x + 330, y + 7, 64, 18, () -> "Install", Button.Style.PRIMARY,
					() -> InstallPopup.open(this, project.id(), null, project.title(), this::load)));
			}
			y += 36;
		}
		add(new Button(width / 2 - 50, height - 28, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int x = width / 2 - 200;
		header(g, x, 10, "Performance pack", status);
		int y = 50;
		for (Map.Entry<RecommendedMods.Recommendation, Status> entry : statuses.entrySet()) {
			RecommendedMods.Recommendation rec = entry.getKey();
			Status s = entry.getValue();
			Render2D.rounded(g, x, y, 400, 32, 6, Theme.surface());
			RemoteTextures.draw(g, s.project() != null ? s.project().iconUrl() : null, x + 6, y + 6, 20, 20, 64);
			Text.draw(g, rec.title(), x + 32, y + 6, Theme.TEXT);
			Text.draw(g, Text.ellipsize(rec.purpose(), 280), x + 32, y + 18, Theme.FAINT);
			String state = s.installed() ? "Installed" : s.version() == null ? "Not available" : "";
			if (!state.isEmpty()) {
				Text.draw(g, state, x + 394 - Text.width(state), y + 12, s.installed() ? Theme.SUCCESS : Theme.WARNING);
			}
			y += 36;
		}
	}
}
