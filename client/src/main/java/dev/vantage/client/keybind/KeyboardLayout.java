package dev.vantage.client.keybind;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Positions of the keys of a full-size ISO keyboard (the layout used in Germany and most of Europe) in key units,
 * plus a row of mouse buttons. Keys are identified by their GLFW key code, which names the physical position;
 * the printed label comes from the operating system's keyboard layout at runtime, so a German keyboard shows "Z"
 * where a US keyboard shows "Y".
 */
public final class KeyboardLayout {
	/** One key cap: position and size in key units; {@code label} is null when the system name should be used. */
	public record Cap(InputKey key, float x, float y, float w, float h, String label) {
	}

	/** Width of the layout without and with the number pad, and its height, in key units. */
	public static final float WIDTH_COMPACT = 18.5f;
	public static final float WIDTH_FULL = 23f;
	public static final float HEIGHT = 8f;

	private static final List<Cap> CAPS = build();

	private KeyboardLayout() {
	}

	/** All caps; with {@code numpad} false the number pad is left out. */
	public static List<Cap> caps(boolean numpad) {
		if (numpad) {
			return CAPS;
		}
		return CAPS.stream().filter(c -> c.key().type() == InputKey.Type.MOUSE || c.x() < WIDTH_COMPACT).toList();
	}

	private static List<Cap> build() {
		List<Cap> caps = new ArrayList<>();
		// Function row
		key(caps, GLFW.GLFW_KEY_ESCAPE, 0, 0, 1, "Esc");
		for (int i = 0; i < 12; i++) {
			float x = 2 + i + (i / 4) * 0.5f;
			key(caps, GLFW.GLFW_KEY_F1 + i, x, 0, 1, "F" + (i + 1));
		}
		key(caps, GLFW.GLFW_KEY_PRINT_SCREEN, 15.5f, 0, 1, "Prt");
		key(caps, GLFW.GLFW_KEY_SCROLL_LOCK, 16.5f, 0, 1, "Scr");
		key(caps, GLFW.GLFW_KEY_PAUSE, 17.5f, 0, 1, "Pau");

		// Number row
		float y = 1.5f;
		key(caps, GLFW.GLFW_KEY_GRAVE_ACCENT, 0, y, 1, null);
		int[] digits = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6,
			GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL};
		row(caps, digits, 1, y);
		key(caps, GLFW.GLFW_KEY_BACKSPACE, 13, y, 2, "Back");
		key(caps, GLFW.GLFW_KEY_INSERT, 15.5f, y, 1, "Ins");
		key(caps, GLFW.GLFW_KEY_HOME, 16.5f, y, 1, "Home");
		key(caps, GLFW.GLFW_KEY_PAGE_UP, 17.5f, y, 1, "PgUp");
		key(caps, GLFW.GLFW_KEY_NUM_LOCK, 19, y, 1, "Num");
		key(caps, GLFW.GLFW_KEY_KP_DIVIDE, 20, y, 1, "/");
		key(caps, GLFW.GLFW_KEY_KP_MULTIPLY, 21, y, 1, "*");
		key(caps, GLFW.GLFW_KEY_KP_SUBTRACT, 22, y, 1, "-");

		// Top letter row
		y = 2.5f;
		key(caps, GLFW.GLFW_KEY_TAB, 0, y, 1.5f, "Tab");
		int[] top = {GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_Y,
			GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET};
		row(caps, top, 1.5f, y);
		caps.add(new Cap(InputKey.key(GLFW.GLFW_KEY_ENTER), 13.75f, y, 1.25f, 2, "Enter"));
		key(caps, GLFW.GLFW_KEY_DELETE, 15.5f, y, 1, "Del");
		key(caps, GLFW.GLFW_KEY_END, 16.5f, y, 1, "End");
		key(caps, GLFW.GLFW_KEY_PAGE_DOWN, 17.5f, y, 1, "PgDn");
		key(caps, GLFW.GLFW_KEY_KP_7, 19, y, 1, "7");
		key(caps, GLFW.GLFW_KEY_KP_8, 20, y, 1, "8");
		key(caps, GLFW.GLFW_KEY_KP_9, 21, y, 1, "9");
		caps.add(new Cap(InputKey.key(GLFW.GLFW_KEY_KP_ADD), 22, y, 1, 2, "+"));

		// Home row
		y = 3.5f;
		key(caps, GLFW.GLFW_KEY_CAPS_LOCK, 0, y, 1.75f, "Caps");
		int[] home = {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H,
			GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_BACKSLASH};
		row(caps, home, 1.75f, y);
		key(caps, GLFW.GLFW_KEY_KP_4, 19, y, 1, "4");
		key(caps, GLFW.GLFW_KEY_KP_5, 20, y, 1, "5");
		key(caps, GLFW.GLFW_KEY_KP_6, 21, y, 1, "6");

		// Bottom letter row
		y = 4.5f;
		key(caps, GLFW.GLFW_KEY_LEFT_SHIFT, 0, y, 1.25f, "Shift");
		int[] bottom = {GLFW.GLFW_KEY_WORLD_2, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B,
			GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH};
		row(caps, bottom, 1.25f, y);
		key(caps, GLFW.GLFW_KEY_RIGHT_SHIFT, 12.25f, y, 2.75f, "Shift");
		key(caps, GLFW.GLFW_KEY_UP, 16.5f, y, 1, "↑");
		key(caps, GLFW.GLFW_KEY_KP_1, 19, y, 1, "1");
		key(caps, GLFW.GLFW_KEY_KP_2, 20, y, 1, "2");
		key(caps, GLFW.GLFW_KEY_KP_3, 21, y, 1, "3");
		caps.add(new Cap(InputKey.key(GLFW.GLFW_KEY_KP_ENTER), 22, y, 1, 2, "Ent"));

		// Space row
		y = 5.5f;
		key(caps, GLFW.GLFW_KEY_LEFT_CONTROL, 0, y, 1.25f, "Ctrl");
		key(caps, GLFW.GLFW_KEY_LEFT_SUPER, 1.25f, y, 1.25f, "Win");
		key(caps, GLFW.GLFW_KEY_LEFT_ALT, 2.5f, y, 1.25f, "Alt");
		key(caps, GLFW.GLFW_KEY_SPACE, 3.75f, y, 6.25f, "Space");
		key(caps, GLFW.GLFW_KEY_RIGHT_ALT, 10, y, 1.25f, "AltGr");
		key(caps, GLFW.GLFW_KEY_RIGHT_SUPER, 11.25f, y, 1.25f, "Win");
		key(caps, GLFW.GLFW_KEY_MENU, 12.5f, y, 1.25f, "Menu");
		key(caps, GLFW.GLFW_KEY_RIGHT_CONTROL, 13.75f, y, 1.25f, "Ctrl");
		key(caps, GLFW.GLFW_KEY_LEFT, 15.5f, y, 1, "←");
		key(caps, GLFW.GLFW_KEY_DOWN, 16.5f, y, 1, "↓");
		key(caps, GLFW.GLFW_KEY_RIGHT, 17.5f, y, 1, "→");
		key(caps, GLFW.GLFW_KEY_KP_0, 19, y, 2, "0");
		key(caps, GLFW.GLFW_KEY_KP_DECIMAL, 21, y, 1, ",");

		// Mouse buttons
		y = 7;
		String[] mouse = {"Left click", "Right click", "Middle click", "Mouse 4", "Mouse 5"};
		for (int i = 0; i < mouse.length; i++) {
			caps.add(new Cap(InputKey.mouse(i == 1 ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : i == 2 ? GLFW.GLFW_MOUSE_BUTTON_MIDDLE : i == 0
				? GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_4 + (i - 3)), i * 3.0f, y, 2.8f, 1, mouse[i]));
		}
		return Collections.unmodifiableList(caps);
	}

	private static void row(List<Cap> caps, int[] codes, float startX, float y) {
		for (int i = 0; i < codes.length; i++) {
			key(caps, codes[i], startX + i, y, 1, null);
		}
	}

	private static void key(List<Cap> caps, int code, float x, float y, float w, String label) {
		caps.add(new Cap(InputKey.key(code), x, y, w, 1, label));
	}
}
