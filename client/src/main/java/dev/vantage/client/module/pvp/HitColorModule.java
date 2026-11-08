package dev.vantage.client.module.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vantage.client.mixin.OverlayTextureAccessor;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.ColorSetting;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Changes the red tint entities flash when they are hurt. Minecraft draws that tint from a tiny "overlay"
 * texture; this module repaints its hurt rows with the chosen colour (alpha = intensity).
 */
public final class HitColorModule extends Module {
	private static final int VANILLA = 0xB2FF0000;
	public final ColorSetting color = add(new ColorSetting("color", "Hit colour", "Tint of damaged entities; alpha controls intensity.", 0x99A48BFF, false));

	public HitColorModule() {
		super("hitcolor", "Hit Color", "Custom colour for the damage flash on entities.", Category.PVP, false);
		color.onChange(c -> apply());
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		paint(VANILLA);
	}

	/** Re-applies the current colour (also called once the renderer exists). */
	public void apply() {
		paint(isEnabled() ? color.get() : VANILLA);
	}

	private static void paint(int argb) {
		if (MC.gameRenderer == null) {
			return;
		}
		DynamicTexture texture = ((OverlayTextureAccessor) MC.gameRenderer.overlayTexture()).vantage$texture();
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				pixels.setPixel(x, y, argb);
			}
		}
		texture.upload();
	}
}
