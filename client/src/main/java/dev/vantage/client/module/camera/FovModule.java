package dev.vantage.client.module.camera;

import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.NumberSetting;

/**
 * Custom field of view beyond the vanilla slider range, and control over dynamic FOV (the FOV change caused by
 * sprinting, speed effects and bows).
 */
public final class FovModule extends Module {
	public final BooleanSetting customFov = add(new BooleanSetting("custom", "Custom FOV", "Override the base field of view.", false));
	public final NumberSetting fov = add(new NumberSetting("fov", "Field of view", "Base FOV in degrees.", 90, 30, 140, 1, "°"));
	public final BooleanSetting dynamicFov = add(new BooleanSetting("dynamic", "Dynamic FOV", "Let sprinting, speed and bows change your FOV.", true));
	public final NumberSetting dynamicStrength = add(new NumberSetting("dynamicStrength", "Dynamic FOV strength", "How strongly dynamic FOV applies.", 1.0, 0, 1, 0.05));

	public FovModule() {
		super("fov", "Custom FOV", "Custom field of view and dynamic FOV control.", Category.CAMERA, true);
		fov.visibleWhen(customFov::enabled);
		dynamicStrength.visibleWhen(dynamicFov::enabled);
	}

	/** Base FOV to use instead of the vanilla option, or -1 to keep vanilla. */
	public float baseFov() {
		return isEnabled() && customFov.enabled() ? fov.floatValue() : -1;
	}

	/** Adjusts the vanilla FOV modifier (1 = unchanged). */
	public float adjustModifier(float modifier) {
		if (!isEnabled()) {
			return modifier;
		}
		if (!dynamicFov.enabled()) {
			return 1f;
		}
		return 1f + (modifier - 1f) * dynamicStrength.floatValue();
	}
}
