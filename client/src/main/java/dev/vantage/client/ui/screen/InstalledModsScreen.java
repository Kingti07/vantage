package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.modrinth.ModService;
import dev.vantage.client.modrinth.RemoteTextures;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.SettingWidgets;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.mods.InstalledMod;
import dev.vantage.common.mods.ModProblem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Installed mods of this instance: versions, source, enable/disable, removal, update checks and compatibility
 * problems. Changes to loaded jars are queued and applied by the launcher on the next start.
 */
public final class InstalledModsScreen extends VScreen {
	private List<InstalledMod> mods = List.of();
	private List<ModProblem> problems = List.of();
	private Map<InstalledMod, Version> updates = new HashMap<>();
	private String status = "Scanning mods folder…";
	private ScrollPanel list;

	public InstalledModsScreen(Screen parent) {
		super("Installed mods", parent);
		reload();
	}

	private ModService service() {
		return VantageClient.get().mods();
	}

	private void reload() {
		ModService service = service();
		service.run(() -> {
			List<InstalledMod> found = service.manager().installed();
			if (service.manager().identify(found) > 0) {
				found = service.manager().installed();
			}
			return found;
		}, found -> {
			mods = found;
			problems = service.manager().analyze(found);
			status = found.size() + " mods";
			rebuildWidgets();
		}, error -> status = "Could not read mods: " + ModService.message(error));
	}

	@Override
	protected void build() {
		int top = 44 + Math.min(4, problems.size()) * 11 + (problems.isEmpty() ? 0 : 6);
		list = add(new ScrollPanel(20, top, width - 40, height - top - 36));
		int y = 0;
		int w = list.width - 8;
		for (InstalledMod mod : mods) {
			int rowY = y;
			boolean bundled = "vantage-client.jar".equals(mod.baseFileName());
			Version update = updates.get(mod);
			list.decorate((g, off) -> {
				Render2D.rounded(g, 0, rowY, w, 30, 6, Theme.surface());
				String icon = mod.indexEntry() != null ? mod.indexEntry().iconUrl() : null;
				if (icon == null && mod.metadata() != null && mod.metadata().iconPath() != null) {
					RemoteTextures.drawJarIcon(g, mod.file(), mod.metadata().iconPath(), 6, rowY + 5, 20, 20, 64);
				} else {
					RemoteTextures.draw(g, icon, 6, rowY + 5, 20, 20, 64);
				}
				int color = mod.effectiveEnabled() ? Theme.TEXT : Theme.FAINT;
				Text.draw(g, Text.ellipsize(mod.displayName(), w / 2), 32, rowY + 5, color);
				String source = bundled ? "Vantage client" : mod.indexEntry() != null ? "Modrinth" : "Manual";
				String pending = mod.pending() == null ? "" : switch (mod.pending()) {
					case DELETE -> " · removed on restart";
					case ENABLE -> " · enabled on restart";
					case DISABLE -> " · disabled on restart";
				};
				Text.draw(g, Text.ellipsize(mod.displayVersion() + " · " + source + pending, w / 2), 32, rowY + 16,
					mod.pending() != null ? Theme.WARNING : Theme.FAINT);
				if (!"fabric".equals(mod.loader())) {
					Text.draw(g, mod.loader().toUpperCase(), w / 2 + 40, rowY + 11, Theme.DANGER);
				}
			});
			if (!bundled) {
				int ax = w - 30;
				Button remove = new Button(ax - 34, rowY + 6, 60, 18, () -> "Remove", Button.Style.DANGER, () -> service().run(() -> {
					service().manager().remove(mod);
					return null;
				}, ok -> {
					service().markRestartRequired();
					reload();
				}, error -> toast(ModService.message(error))));
				list.add(remove);
				SettingWidgets.Toggle toggle = new SettingWidgets.Toggle(mod::effectiveEnabled, value -> service().run(() -> {
					service().manager().setEnabled(mod, value);
					return null;
				}, ok -> {
					service().markRestartRequired();
					reload();
				}, error -> toast(ModService.message(error))));
				toggle.bounds(ax - 70, rowY + 8, 28, 14);
				list.add(toggle);
				if (update != null) {
					list.add(new Button(ax - 160, rowY + 6, 84, 18, () -> "Update", Button.Style.PRIMARY,
						() -> InstallPopup.open(this, update.projectId(), update.id(), mod.displayName(), this::reload)));
				}
			}
			y += 34;
		}
		if (mods.isEmpty() && status.endsWith("mods")) {
			list.decorate((g, off) -> {
				Text.draw(g, "No mods in this instance's mods folder yet.", 4, 6, Theme.MUTED);
				Text.draw(g, "Browse Modrinth to add some – dependencies are installed automatically.", 4, 18, Theme.FAINT);
			});
		}
		list.setContentHeight(y);
		// Footer sized to fit the smallest window (427 GUI px wide).
		add(new Button(20, height - 28, 110, 18, () -> "Check for updates", Button.Style.GHOST, this::checkUpdates));
		add(new Button(136, height - 28, 80, 18, () -> "Open folder", Button.Style.GHOST, () -> Util.getPlatform().openPath(service().manager().modsDir())));
		add(new Button(222, height - 28, 64, 18, () -> "Browse", Button.Style.GHOST, () -> minecraft.setScreen(new ModBrowserScreen(this))));
		add(new Button(width - 100, height - 28, 80, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
	}

	private void checkUpdates() {
		status = "Checking for updates…";
		service().run(() -> service().manager().findUpdates(mods), found -> {
			updates = new HashMap<>(found);
			status = found.isEmpty() ? "All mods are up to date." : found.size() + " update(s) available.";
			rebuildWidgets();
		}, error -> status = "Update check failed: " + ModService.message(error));
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, 10, "Installed mods", status);
		int y = 44;
		for (int i = 0; i < Math.min(4, problems.size()); i++) {
			ModProblem problem = problems.get(i);
			String text = (problem.fileName() != null ? problem.fileName() + ": " : "") + problem.message();
			Text.draw(g, Text.ellipsize(text, width - 40), 20, y, problem.isError() ? Theme.DANGER : Theme.WARNING);
			y += 11;
		}
		if (problems.size() > 4) {
			Text.draw(g, "+" + (problems.size() - 4) + " more problems", width - 140, 44, Theme.DANGER);
		}
		if (service().restartRequired()) {
			String text = "Restart Minecraft to apply changes.";
			Text.draw(g, text, (width - Text.width(text)) / 2, height - 22, Theme.WARNING);
		}
	}
}
