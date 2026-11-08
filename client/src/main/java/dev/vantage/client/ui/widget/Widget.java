package dev.vantage.client.ui.widget;

import dev.vantage.client.render.Render2D;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Minimal retained-mode widget used by all Vantage screens. Coordinates are GUI-scaled pixels; events use
 * plain numbers so widgets do not depend on Minecraft's input record types.
 */
public abstract class Widget {
	public int x;
	public int y;
	public int width;
	public int height;
	public boolean visible = true;
	public boolean enabled = true;
	public String tooltip;

	protected Widget(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public Widget bounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		return this;
	}

	public abstract void render(GuiGraphics g, int mouseX, int mouseY, float delta);

	public boolean isHovered(double mx, double my) {
		return visible && Render2D.inside(mx, my, x, y, width, height);
	}

	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return false;
	}

	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		return false;
	}

	public boolean keyPressed(int key, int scancode, int modifiers) {
		return false;
	}

	public boolean charTyped(String text) {
		return false;
	}

	/** Whether this widget currently wants keyboard input (e.g. a focused text field or keybind capture). */
	public boolean capturesKeyboard() {
		return false;
	}

	public void tick() {
	}
}
