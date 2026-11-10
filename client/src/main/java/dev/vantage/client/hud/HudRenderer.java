package dev.vantage.client.hud;

import dev.vantage.client.VantageClient;
import dev.vantage.client.ui.screen.HudEditorScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws every enabled HUD element; registered as one layer in Fabric's HUD element registry. */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void render(GuiGraphics g, DeltaTracker tracker) {
		dev.vantage.client.dev.DevBenchmark.onFrame();
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null || mc.screen instanceof HudEditorScreen) {
			return;
		}
		if (mc.getDebugOverlay().showDebugScreen() && VantageClient.get().interfaceModule().hideHudInDebug.enabled()) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		for (HudModule module : VantageClient.get().modules().hudModules()) {
			if (module.isEnabled() && module.hasContent()) {
				module.render(g, width, height, false);
			}
		}
	}
}
