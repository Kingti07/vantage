package dev.vantage.client.ui.screen;

import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.common.VantageInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.nio.file.Path;

/**
 * Launcher-related information and shortcuts. Launcher preferences (memory, Java, versions, update channel)
 * are edited in the launcher itself, because they only take effect when the game is started.
 */
public final class LauncherInfoScreen extends VScreen {
	public LauncherInfoScreen(Screen parent) {
		super("Launcher", parent);
	}

	private static Path launcherDir() {
		String dir = System.getProperty(VantageInfo.PROP_LAUNCHER_DIR);
		return dir == null || dir.isBlank() ? null : Path.of(dir);
	}

	@Override
	protected void build() {
		int cx = width / 2;
		int y = 140;
		Path gameDir = FabricLoader.getInstance().getGameDir();
		add(new Button(cx - 150, y, 145, 18, () -> "Open game folder", Button.Style.GHOST, () -> Util.getPlatform().openPath(gameDir)));
		add(new Button(cx + 5, y, 145, 18, () -> "Open mods folder", Button.Style.GHOST, () -> Util.getPlatform().openPath(gameDir.resolve("mods"))));
		y += 24;
		add(new Button(cx - 150, y, 145, 18, () -> "Open screenshots", Button.Style.GHOST, () -> Util.getPlatform().openPath(gameDir.resolve("screenshots"))));
		add(new Button(cx + 5, y, 145, 18, () -> "Open logs", Button.Style.GHOST, () -> Util.getPlatform().openPath(gameDir.resolve("logs"))));
		y += 24;
		Path launcher = launcherDir();
		Button launcherButton = add(new Button(cx - 150, y, 300, 18, () -> "Open launcher data folder", Button.Style.GHOST,
			() -> Util.getPlatform().openPath(launcher)));
		launcherButton.enabled = launcher != null;
		add(new Button(cx - 50, height - 30, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int cx = width / 2;
		header(g, cx - 150, 16, "Launcher", null);
		Path launcher = launcherDir();
		String[] lines = {
			"Launcher: " + (launcher != null ? VantageInfo.NAME + " Launcher" : "not detected (started another way)"),
			"Game directory: " + FabricLoader.getInstance().getGameDir(),
			"Minecraft " + FabricLoader.getInstance().getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?")
				+ " · Fabric Loader " + FabricLoader.getInstance().getModContainer("fabricloader").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"),
			"Java " + Runtime.version() + " · " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB max memory",
			"Memory, Java runtime, versions and updates are configured in the launcher."
		};
		int y = 46;
		for (String line : lines) {
			Text.draw(g, Text.ellipsize(line, 300), cx - 150, y, line.startsWith("Memory") ? Theme.MUTED : Theme.TEXT);
			y += 14;
		}
	}
}
