package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.modrinth.ModService;
import dev.vantage.client.modrinth.RemoteTextures;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.ModProblem;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.ProgressListener;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Install confirmation: resolves dependencies first, then shows every file that will be downloaded, already
 * satisfied dependencies, replaced files and any problems. Installing is impossible while errors exist.
 */
public final class InstallPopup extends Widget {
	private final VScreen screen;
	private final String title;
	private final Runnable onInstalled;
	private InstallPlan plan;
	private String status = "Resolving dependencies…";
	private boolean busy = true;
	private volatile double progress = -1;
	private final List<Button> buttons = new ArrayList<>();

	private InstallPopup(VScreen screen, String title, Runnable onInstalled) {
		super(0, 0, 300, 120);
		this.screen = screen;
		this.title = title;
		this.onInstalled = onInstalled;
		layout();
	}

	public static void open(VScreen screen, String projectId, String versionId, String title, Runnable onInstalled) {
		InstallPopup popup = new InstallPopup(screen, title, onInstalled);
		screen.openPopup(popup);
		ModService mods = VantageClient.get().mods();
		mods.run(() -> mods.manager().plan(projectId, versionId), plan -> {
			popup.plan = plan;
			popup.busy = false;
			popup.status = null;
			popup.layout();
		}, error -> {
			popup.busy = false;
			popup.status = "Could not resolve: " + ModService.message(error);
			popup.layout();
		});
	}

	private int contentHeight() {
		int h = 34;
		if (plan != null) {
			h += plan.toInstall().size() * 24;
			if (!plan.satisfied().isEmpty()) {
				h += 14;
			}
			h += plan.problems().size() * 12;
			h += 14;
		}
		if (status != null) {
			h += 16;
		}
		return h + 30;
	}

	private void layout() {
		width = Math.min(340, screen.width - 20);
		height = Math.min(contentHeight(), screen.height - 20);
		x = (screen.width - width) / 2;
		y = (screen.height - height) / 2;
		buttons.clear();
		boolean canInstall = plan != null && plan.canInstall() && !plan.toInstall().isEmpty() && !busy;
		Button cancel = new Button(x + width - 160, y + height - 26, 74, 18, () -> canInstall ? "Cancel" : "Close", Button.Style.GHOST, screen::closePopup);
		Button install = new Button(x + width - 82, y + height - 26, 74, 18, () -> "Install", Button.Style.PRIMARY, this::install);
		install.enabled = canInstall;
		buttons.add(cancel);
		buttons.add(install);
	}

	private void install() {
		busy = true;
		status = "Downloading…";
		progress = 0;
		layout();
		ModService mods = VantageClient.get().mods();
		InstallPlan current = plan;
		ProgressListener listener = new ProgressListener() {
			@Override
			public void status(String message) {
				status = message;
			}

			@Override
			public void progress(double fraction) {
				progress = fraction;
			}
		};
		mods.run(() -> mods.manager().execute(current, listener), files -> {
			mods.markRestartRequired();
			screen.closePopup();
			screen.toast("Installed " + title + " – restart Minecraft to load it.");
			if (onInstalled != null) {
				onInstalled.run();
			}
		}, error -> {
			busy = false;
			status = "Install failed: " + ModService.message(error);
			progress = -1;
			layout();
		});
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Render2D.shadow(g, x, y, width, height, 10, 12, 0xFF000000);
		Render2D.panel(g, x, y, width, height, 10, 0xF5141820, Theme.border());
		Text.draw(g, Text.ellipsize("Install " + title, width - 16), x + 10, y + 10, Theme.TEXT);
		int cy = y + 26;
		g.enableScissor(x, y, x + width, y + height - 32);
		if (plan != null) {
			if (plan.root() != null) {
				Text.draw(g, Text.ellipsize("Version " + plan.root().versionNumber() + " · Fabric " + VantageClient.get().mods().gameVersion(), width - 20),
					x + 10, cy - 2, Theme.FAINT);
				cy += 10;
			}
			for (InstallPlan.PlannedFile file : plan.toInstall()) {
				Render2D.rounded(g, x + 8, cy, width - 16, 22, 5, Theme.surface());
				RemoteTextures.draw(g, file.iconUrl(), x + 11, cy + 3, 16, 16, 64);
				Text.draw(g, Text.ellipsize(file.title(), width - 120), x + 32, cy + 3, Theme.TEXT);
				Text.draw(g, Text.ellipsize(file.version().versionNumber(), width - 120), x + 32, cy + 12, Theme.FAINT);
				String tag = file.dependency() ? "dependency" : "requested";
				Text.draw(g, tag, x + width - 14 - Text.width(tag), cy + 7, file.dependency() ? Theme.MUTED : Theme.accent());
				cy += 24;
			}
			if (!plan.satisfied().isEmpty()) {
				Text.draw(g, Text.ellipsize("Already installed: " + String.join(", ", plan.satisfied()), width - 20), x + 10, cy, Theme.SUCCESS);
				cy += 14;
			}
			for (ModProblem problem : plan.problems()) {
				Text.draw(g, Text.ellipsize((problem.isError() ? "✖ " : "! ") + problem.message(), width - 20), x + 10, cy,
					problem.isError() ? Theme.DANGER : Theme.WARNING);
				cy += 12;
			}
			if (plan.canInstall() && !plan.toInstall().isEmpty()) {
				Text.draw(g, "Download size: " + FileUtil.humanSize(plan.downloadSize()), x + 10, cy + 2, Theme.FAINT);
			}
		}
		g.disableScissor();
		if (status != null) {
			Text.draw(g, Text.ellipsize(status, width - 20), x + 10, y + height - 44, busy ? Theme.MUTED : Theme.DANGER);
		}
		if (busy && progress >= 0) {
			Render2D.rounded(g, x + 10, y + height - 32, width - 20, 3, 1, 0x30FFFFFF);
			Render2D.rounded(g, x + 10, y + height - 32, (int) ((width - 20) * Math.min(1, progress)), 3, 1, Theme.accent());
		}
		buttons.forEach(b -> b.render(g, mouseX, mouseY, delta));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!isHovered(mx, my)) {
			return busy; // keep the popup open while working
		}
		for (Button b : buttons) {
			if (b.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		return true;
	}
}
