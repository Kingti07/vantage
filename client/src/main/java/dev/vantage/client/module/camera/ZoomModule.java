package dev.vantage.client.module.camera;

import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.NumberSetting;
import dev.vantage.client.util.Anim;
import org.lwjgl.glfw.GLFW;

/**
 * Hold-to-zoom (or toggle) with scroll-wheel adjustment and smooth transitions. Implemented by dividing the
 * world FOV in {@code GameRendererMixin}; mouse sensitivity is scaled down while zoomed.
 */
public final class ZoomModule extends Module {
	public final NumberSetting factor = add(new NumberSetting("factor", "Zoom factor", "How far to zoom in.", 4, 1.5, 30, 0.5, "x"));
	public final BooleanSetting smooth = add(new BooleanSetting("smooth", "Smooth zoom", "Animate zooming in and out.", true));
	public final BooleanSetting scroll = add(new BooleanSetting("scroll", "Scroll to adjust", "Use the mouse wheel to change zoom while zooming.", true));
	public final BooleanSetting toggleMode = add(new BooleanSetting("toggleMode", "Toggle mode", "Press once to zoom, press again to stop.", false));
	public final BooleanSetting reduceSensitivity = add(new BooleanSetting("reduceSensitivity", "Reduce sensitivity", "Lower mouse sensitivity while zoomed.", true));
	public final BooleanSetting cinematic = add(new BooleanSetting("cinematic", "Cinematic camera", "Use smooth camera movement while zoomed.", false));
	private final Keybind key;
	private final Anim anim = new Anim(1, 14);
	private boolean toggled;
	private double scrollFactor = 1;
	private boolean wasZooming;
	private boolean previousSmoothCamera;

	public ZoomModule() {
		super("zoom", "Zoom", "Zoom in like a spyglass while holding a key.", Category.CAMERA, true);
		key = keybind("zoom", "Zoom", InputKey.key(GLFW.GLFW_KEY_C)).onPress(() -> {
			if (toggleMode.enabled()) {
				toggled = !toggled;
			}
		});
	}

	public boolean isZooming() {
		return isEnabled() && MC.screen == null && (toggleMode.enabled() ? toggled : key.isDown());
	}

	/** Multiplier applied to the FOV (1 = no zoom). */
	public float fovMultiplier() {
		float target = isZooming() ? (float) (1.0 / (factor.get() * scrollFactor)) : 1f;
		if (!smooth.enabled()) {
			anim.snap(target);
			return target;
		}
		return anim.target(target).get();
	}

	/** Returns true if the scroll was consumed for zoom adjustment. */
	public boolean onScroll(double amount) {
		if (!isZooming() || !scroll.enabled() || amount == 0) {
			return false;
		}
		scrollFactor = Math.max(0.25, Math.min(8, scrollFactor * (amount > 0 ? 1.15 : 1 / 1.15)));
		return true;
	}

	public double sensitivityMultiplier() {
		if (!isZooming() || !reduceSensitivity.enabled()) {
			return 1;
		}
		return Math.max(0.1, 1.0 / (factor.get() * scrollFactor));
	}

	@Override
	public void onTick() {
		boolean zooming = isZooming();
		if (zooming != wasZooming) {
			if (zooming) {
				previousSmoothCamera = MC.options.smoothCamera;
				if (cinematic.enabled()) {
					MC.options.smoothCamera = true;
				}
			} else {
				MC.options.smoothCamera = previousSmoothCamera;
				scrollFactor = 1;
			}
			wasZooming = zooming;
		}
	}

	@Override
	protected void onDisable() {
		toggled = false;
		if (wasZooming) {
			MC.options.smoothCamera = previousSmoothCamera;
			wasZooming = false;
		}
	}
}
