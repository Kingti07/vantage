package dev.vantage.client.module.camera;

import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.EnumSetting;
import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;

/** Hold a key to peek in another perspective; releasing returns to the previous one. */
public final class PerspectiveModule extends Module {
	public enum View {
		BACK("Third person (back)", CameraType.THIRD_PERSON_BACK),
		FRONT("Third person (front)", CameraType.THIRD_PERSON_FRONT);

		private final String label;
		private final CameraType type;

		View(String label, CameraType type) {
			this.label = label;
			this.type = type;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<View> view = add(new EnumSetting<>("view", "Perspective", "Which view to show while the key is held.", View.FRONT));
	private final Keybind key;
	private CameraType previous;

	public PerspectiveModule() {
		super("perspective", "Perspective", "Hold a key to temporarily switch camera perspective.", Category.CAMERA, true);
		key = keybind("hold", "Hold perspective", InputKey.key(GLFW.GLFW_KEY_V));
		key.onPress(() -> {
			if (isEnabled() && previous == null) {
				previous = MC.options.getCameraType();
				MC.options.setCameraType(view.get().type);
			}
		}).onRelease(this::restore);
	}

	private void restore() {
		if (previous != null) {
			MC.options.setCameraType(previous);
			previous = null;
		}
	}

	@Override
	public void onTick() {
		if (previous != null && !key.isDown()) {
			restore();
		}
	}

	@Override
	protected void onDisable() {
		restore();
	}
}
