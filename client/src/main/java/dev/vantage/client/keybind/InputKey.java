package dev.vantage.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** A single keyboard key or mouse button (GLFW codes). */
public record InputKey(Type type, int code) {
	public enum Type {KEYBOARD, MOUSE}

	public static InputKey key(int glfwKey) {
		return new InputKey(Type.KEYBOARD, glfwKey);
	}

	public static InputKey mouse(int glfwButton) {
		return new InputKey(Type.MOUSE, glfwButton);
	}

	/** Whether the key is physically held right now. */
	public boolean isDown() {
		long window = Minecraft.getInstance().getWindow().handle();
		return type == Type.KEYBOARD
			? code > 0 && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS
			: GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
	}

	public InputConstants.Key toVanilla() {
		return type == Type.KEYBOARD ? InputConstants.Type.KEYSYM.getOrCreate(code) : InputConstants.Type.MOUSE.getOrCreate(code);
	}

	public String displayName() {
		if (type == Type.MOUSE) {
			return switch (code) {
				case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "Left Click";
				case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "Right Click";
				case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "Middle Click";
				default -> "Mouse " + (code + 1);
			};
		}
		return toVanilla().getDisplayName().getString();
	}

	/** Compact form for storage: "key:71" / "mouse:3". */
	public String serialize() {
		return (type == Type.KEYBOARD ? "key:" : "mouse:") + code;
	}

	public static InputKey parse(String text) {
		try {
			if (text.startsWith("key:")) {
				return key(Integer.parseInt(text.substring(4)));
			}
			if (text.startsWith("mouse:")) {
				return mouse(Integer.parseInt(text.substring(6)));
			}
		} catch (NumberFormatException ignored) {
			// fall through
		}
		return null;
	}
}
