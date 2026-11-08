package dev.vantage.client.module.pvp;

import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

/** Fully customisable crosshair replacing the vanilla one. */
public final class CrosshairModule extends Module {
	public enum Style {CROSS, CROSS_DOT, DOT, CIRCLE, T_SHAPE, SQUARE}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", "Crosshair shape.", Style.CROSS));
	public final ColorSetting color = add(new ColorSetting("color", "Colour", "Crosshair colour.", 0xFFFFFFFF));
	public final BooleanSetting invert = add(new BooleanSetting("invert", "Invert colours", "Blend like the vanilla crosshair (visible on any background).", false));
	public final NumberSetting length = add(new NumberSetting("length", "Length", "Arm length.", 5, 1, 15, 1, "px"));
	public final NumberSetting gap = add(new NumberSetting("gap", "Gap", "Space in the centre.", 2, 0, 10, 1, "px"));
	public final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", "Line thickness.", 1, 1, 4, 1, "px"));
	public final BooleanSetting outline = add(new BooleanSetting("outline", "Outline", "Dark outline for contrast.", true));
	public final ColorSetting outlineColor = add(new ColorSetting("outlineColor", "Outline colour", "Outline colour.", 0xC0000000, false));
	public final BooleanSetting targetColorEnabled = add(new BooleanSetting("targetColor", "Colour on target", "Change colour when aiming at an entity.", true));
	public final ColorSetting targetColor = add(new ColorSetting("targetColorValue", "Target colour", "Colour while aiming at an entity.", 0xFFFF5C7A));
	public final BooleanSetting attackIndicator = add(new BooleanSetting("attackIndicator", "Attack cooldown bar", "Show the attack cooldown under the crosshair.", true));
	public final BooleanSetting thirdPerson = add(new BooleanSetting("thirdPerson", "Show in third person", "Also draw the crosshair in third person.", false));

	public CrosshairModule() {
		super("crosshair", "Custom Crosshair", "Design your own crosshair: shape, size, gap, colour and outline.", Category.PVP, false);
		outlineColor.visibleWhen(outline::enabled);
		targetColor.visibleWhen(targetColorEnabled::enabled);
		color.visibleWhen(() -> !invert.enabled());
	}

	public boolean shouldRender() {
		if (MC.player == null || MC.gameMode == null) {
			return false;
		}
		if (!thirdPerson.enabled() && !MC.options.getCameraType().isFirstPerson()) {
			return false;
		}
		return MC.gameMode.getPlayerMode() != GameType.SPECTATOR || MC.crosshairPickEntity != null;
	}

	public void render(GuiGraphics g) {
		if (!shouldRender()) {
			return;
		}
		g.nextStratum();
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		boolean onTarget = MC.crosshairPickEntity instanceof LivingEntity living && living.isAlive();
		int c = invert.enabled() ? 0xFFFFFFFF : targetColorEnabled.enabled() && onTarget ? targetColor.color() : color.color();
		draw(g, cx, cy, c);
		if (attackIndicator.enabled() && MC.player != null) {
			float progress = MC.player.getAttackStrengthScale(0f);
			if (progress < 1f) {
				int w = 16;
				int y = cy + length.intValue() + gap.intValue() + 4;
				Render2D.rect(g, cx - w / 2, y, w, 2, 0x80000000);
				Render2D.rect(g, cx - w / 2, y, Math.round(w * progress), 2, Colors.withAlpha(c, 230));
			}
		}
	}

	/** Draws the crosshair centred on (cx, cy); also used by the settings preview. */
	public void draw(GuiGraphics g, int cx, int cy, int c) {
		int len = length.intValue();
		int gp = gap.intValue();
		int t = thickness.intValue();
		int half = t / 2;
		boolean out = outline.enabled() && !invert.enabled();
		int oc = outlineColor.color();
		switch (style.get()) {
			case CROSS, CROSS_DOT, T_SHAPE -> {
				if (style.get() != Style.T_SHAPE) {
					bar(g, cx - half, cy - gp - len, t, len, c, out, oc);
				}
				bar(g, cx - half, cy + gp + (t - half) - (t - 1), t, len, c, out, oc);
				bar(g, cx - gp - len, cy - half, len, t, c, out, oc);
				bar(g, cx + gp + (t - half) - (t - 1), cy - half, len, t, c, out, oc);
				if (style.get() == Style.CROSS_DOT) {
					bar(g, cx - half, cy - half, t, t, c, out, oc);
				}
			}
			case DOT -> {
				int r = Math.max(1, t);
				if (out) {
					Render2D.circle(g, cx, cy, r + 1, oc);
				}
				Render2D.circle(g, cx, cy, r, c);
			}
			case CIRCLE -> {
				int r = gp + len;
				if (out) {
					Render2D.roundedBorder(g, cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, r + 1, t + 2, oc);
				}
				Render2D.roundedBorder(g, cx - r, cy - r, r * 2, r * 2, r, t, c);
			}
			case SQUARE -> {
				int r = gp + len / 2;
				if (out) {
					Render2D.roundedBorder(g, cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, 0, t + 2, oc);
				}
				Render2D.roundedBorder(g, cx - r, cy - r, r * 2, r * 2, 0, t, c);
			}
		}
	}

	private void bar(GuiGraphics g, int x, int y, int w, int h, int c, boolean out, int oc) {
		if (out) {
			g.fill(x - 1, y - 1, x + w + 1, y + h + 1, oc);
		}
		if (invert.enabled()) {
			g.fill(RenderPipelines.CROSSHAIR, x, y, x + w, y + h, c);
		} else {
			g.fill(x, y, x + w, y + h, c);
		}
	}
}
