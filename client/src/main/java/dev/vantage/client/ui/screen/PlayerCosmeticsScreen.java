package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.cosmetics.PlayerCosmeticsModule;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.SettingsPanel;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Choose cape, hat and shoulder pet, with a slowly turning preview of your player while you are in a world. */
public final class PlayerCosmeticsScreen extends VScreen {
	private ScrollPanel settings;
	private final long openedAt = System.currentTimeMillis();

	public PlayerCosmeticsScreen(Screen parent) {
		super("Capes, hats & pets", parent);
	}

	private PlayerCosmeticsModule cosmetics() {
		return VantageClient.get().modules().get(PlayerCosmeticsModule.class);
	}

	@Override
	protected void build() {
		int columnWidth = Math.min(320, (width - 60) / 2);
		settings = add(new ScrollPanel(20, 58, columnWidth, height - 58 - 40));
		SettingsPanel.build(settings, cosmetics(), this, () -> SettingsPanel.build(settings, cosmetics(), this, this::rebuildWidgets));
		add(new Button(20, height - 28, 150, 18, () -> cosmetics().isEnabled() ? "Cosmetics: on" : "Cosmetics: off",
			Button.Style.SECONDARY, () -> cosmetics().toggle()));
		add(new Button(width - 120, height - 28, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, 14, "Capes, hats & pets", "Only you can see them · nothing is sent to the server");
		int columnWidth = Math.min(320, (width - 60) / 2);
		int x = 20 + columnWidth + 20;
		int w = width - x - 20;
		int h = height - 58 - 40;
		Render2D.panel(g, x, 58, w, h, Theme.RADIUS, Theme.panel(), Theme.border());
		LocalPlayer player = minecraft.player;
		if (player == null) {
			Text.drawCentered(g, "Join a world to see the preview.", x + w / 2, 58 + h / 2 - 4, Theme.MUTED);
			return;
		}
		float spin = ((System.currentTimeMillis() - openedAt) % 12_000L) / 12_000f * 360f;
		int size = Math.max(30, Math.min(w, h) / 3);
		renderTurningPlayer(g, x + 10, 68, x + w - 10, 58 + h - 10, size, spin, player);
		Text.drawCentered(g, "Preview turns slowly to show every side", x + w / 2, 58 + h - 14, Theme.FAINT);
	}

	/** Like the inventory preview, but the body turns by {@code spin} degrees instead of following the mouse. */
	private static void renderTurningPlayer(GuiGraphics g, int x0, int y0, int x1, int y1, int size, float spin, LivingEntity entity) {
		EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, 1.0F);
		state.lightCoords = 15728880;
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + spin;
			living.yRot = 0;
			living.xRot = 0;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(-8.0F * (float) (Math.PI / 180.0));
		rotation.mul(camera);
		Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		g.submitEntityRenderState(state, size, translation, rotation, camera, x0, y0, x1, y1);
	}
}
