package dev.vantage.client.module.camera;

import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;

/**
 * Look around without turning your character. While active, mouse movement rotates only the camera
 * ({@code EntityMixin} redirects {@code turn}) and {@code CameraMixin} renders from the free angles in third
 * person. Your character's facing and the direction you move are never changed.
 */
public final class FreelookModule extends Module {
	public final BooleanSetting toggleMode = add(new BooleanSetting("toggleMode", "Toggle mode", "Press once to start, again to stop.", false));
	public final BooleanSetting invertPitch = add(new BooleanSetting("invertPitch", "Invert vertical", "Invert up/down while free looking.", false));
	private final Keybind key;
	private boolean active;
	private boolean toggled;
	private float yaw;
	private float pitch;
	private CameraType previousCamera;

	public FreelookModule() {
		super("freelook", "Freelook", "Look around your character without changing where you face.", Category.CAMERA, true);
		key = keybind("freelook", "Freelook", InputKey.key(GLFW.GLFW_KEY_LEFT_ALT)).onPress(() -> {
			if (toggleMode.enabled()) {
				toggled = !toggled;
			}
		});
	}

	public boolean isActive() {
		return active;
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	/** Mouse delta while active; same scale vanilla uses for {@code Entity.turn}. */
	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch += (float) dy * 0.15f * (invertPitch.enabled() ? -1 : 1);
		pitch = Math.max(-90f, Math.min(90f, pitch));
	}

	@Override
	public void onTick() {
		boolean want = MC.player != null && MC.screen == null && (toggleMode.enabled() ? toggled : key.isDown());
		if (want && !active) {
			start();
		} else if (!want && active && MC.screen == null) {
			stop();
		}
	}

	private void start() {
		active = true;
		yaw = MC.player.getYRot();
		pitch = MC.player.getXRot();
		previousCamera = MC.options.getCameraType();
		if (previousCamera.isFirstPerson()) {
			MC.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	private void stop() {
		active = false;
		if (previousCamera != null) {
			MC.options.setCameraType(previousCamera);
		}
	}

	@Override
	protected void onDisable() {
		toggled = false;
		if (active) {
			stop();
		}
	}
}
