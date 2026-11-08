package dev.vantage.client.module.camera;

import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Toggle sprint and toggle sneak. Works exactly like holding the vanilla keys: the module only presses the
 * vanilla sprint/sneak key mappings for you, so movement rules are unchanged.
 */
public final class ToggleSprintModule extends Module {
	public final BooleanSetting sprintEnabled = add(new BooleanSetting("sprint", "Toggle sprint", "The sprint key toggles sprinting.", true));
	public final BooleanSetting sneakEnabled = add(new BooleanSetting("sneak", "Toggle sneak", "The sneak key toggles sneaking.", false));
	public final BooleanSetting rememberSprint = add(new BooleanSetting("remember", "Remember sprint", "Keep sprint toggled after dying or changing worlds.", true));
	private boolean sprintToggled;
	private boolean sneakToggled;

	public ToggleSprintModule() {
		super("togglesprint", "Toggle Sprint", "Toggle sprinting and sneaking instead of holding the keys.", Category.CAMERA, true);
		keybind("sprint", "Toggle sprint", InputKey.key(GLFW.GLFW_KEY_LEFT_CONTROL)).onPress(() -> {
			if (isEnabled() && sprintEnabled.enabled()) {
				sprintToggled = !sprintToggled;
			}
		});
		keybind("sneak", "Toggle sneak", InputKey.key(GLFW.GLFW_KEY_LEFT_SHIFT)).onPress(() -> {
			if (isEnabled() && sneakEnabled.enabled()) {
				sneakToggled = !sneakToggled;
			}
		});
	}

	@Override
	public void onTick() {
		if (MC.player == null) {
			if (!rememberSprint.enabled()) {
				sprintToggled = false;
			}
			sneakToggled = false;
			return;
		}
		if (MC.screen != null) {
			return;
		}
		if (sprintEnabled.enabled() && sprintToggled) {
			press(MC.options.keySprint, true);
		}
		if (sneakEnabled.enabled() && sneakToggled) {
			press(MC.options.keyShift, true);
		}
	}

	private static void press(KeyMapping mapping, boolean down) {
		mapping.setDown(down);
	}

	/** Called when the module or a sub option is switched off: release the keys we were holding. */
	@Override
	protected void onDisable() {
		if (sprintToggled) {
			press(MC.options.keySprint, false);
		}
		if (sneakToggled) {
			press(MC.options.keyShift, false);
		}
		sprintToggled = false;
		sneakToggled = false;
	}

	public String status() {
		if (!isEnabled() || MC.player == null) {
			return "";
		}
		if (sneakEnabled.enabled() && sneakToggled) {
			return "Sneaking (Toggled)";
		}
		if (sprintEnabled.enabled() && sprintToggled) {
			return MC.player.isSprinting() ? "Sprinting (Toggled)" : "Sprint (Toggled)";
		}
		return "";
	}
}
