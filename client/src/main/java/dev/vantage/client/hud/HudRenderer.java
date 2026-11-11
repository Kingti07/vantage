package dev.vantage.client.hud;

import dev.vantage.client.VantageClient;
import dev.vantage.client.ui.screen.HudEditorScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws every enabled HUD element; registered as one layer in Fabric's HUD element registry. */
public final class HudRenderer {
	private static int lastDrawn;

	private HudRenderer() {
	}

	/** How many HUD elements the last HUD pass drew (used by the in-game smoke test). */
	public static int lastDrawn() {
		return lastDrawn;
	}

	public static void render(GuiGraphics g, DeltaTracker tracker) {
		dev.vantage.client.dev.DevBenchmark.onFrame();
		Minecraft mc = Minecraft.getInstance();
		lastDrawn = 0;
		if (mc.options.hideGui || mc.player == null || mc.screen instanceof HudEditorScreen) {
			return;
		}
		// Only the real F3 overlay counts. showDebugScreen() is also true while a single debug entry is set to
		// "always on" (for example hitboxes via F3+B), which used to hide the whole HUD on that player's PC.
		if (mc.debugEntries.isOverlayVisible() && VantageClient.get().interfaceModule().hideHudInDebug.enabled()) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		for (HudModule module : VantageClient.get().modules().hudModules()) {
			if (module.isEnabled() && module.hasContent()) {
				module.render(g, width, height, false);
				lastDrawn++;
			}
		}
	}
}
