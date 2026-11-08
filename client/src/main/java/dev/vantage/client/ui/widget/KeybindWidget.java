package dev.vantage.client.ui.widget;

import dev.vantage.client.VantageClient;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Shows the keys bound to a keybind. Left click: add a key or mouse button (press Escape to cancel).
 * Right click: remove all bindings. Conflicts are highlighted in red.
 */
public final class KeybindWidget extends Widget {
	private final Keybind keybind;
	private boolean listening;

	public KeybindWidget(Keybind keybind) {
		super(0, 0, 120, 16);
		this.keybind = keybind;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		boolean hovered = isHovered(mouseX, mouseY);
		boolean conflict = !listening && VantageClient.get().keybinds().hasConflict(keybind);
		int border = listening ? Theme.accent() : conflict ? Theme.DANGER : hovered ? 0x60FFFFFF : Theme.border();
		Render2D.rounded(g, x, y, width, height, 4, hovered ? Theme.surfaceHover() : Theme.surface());
		Render2D.roundedBorder(g, x, y, width, height, 4, 1, border);
		String label = listening ? "Press a key or button…" : keybind.describe();
		int color = listening ? Theme.accent() : !keybind.isBound() ? Theme.FAINT : conflict ? Theme.DANGER : Theme.TEXT;
		label = Text.ellipsize(label, width - 10);
		Text.draw(g, label, x + (width - Text.width(label)) / 2, y + (height - 8) / 2, color);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (listening) {
			return true; // the capture listener receives this press via the mouse mixin
		}
		if (!isHovered(mx, my)) {
			return false;
		}
		if (button == 1) {
			keybind.clear();
			return true;
		}
		if (button == 0) {
			listening = true;
			VantageClient.get().keybinds().capture(key -> {
				listening = false;
				if (!(key.type() == dev.vantage.client.keybind.InputKey.Type.KEYBOARD && key.code() == GLFW.GLFW_KEY_ESCAPE)) {
					keybind.add(key);
				}
			});
			return true;
		}
		return false;
	}

	@Override
	public boolean capturesKeyboard() {
		return listening;
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		return listening;
	}
}
