package dev.vantage.client.ui;

import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.client.util.Anim;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for Vantage screens: themed background, widget list, a single popup layer (colour pickers,
 * dialogs) and an open animation. Subclasses build widgets in {@link #init()} via {@link #add}.
 */
public abstract class VScreen extends Screen {
	protected final Screen parent;
	protected final List<Widget> widgets = new ArrayList<>();
	protected Widget popup;
	private final Anim openAnim = new Anim(0, 12);
	private String toast;
	private long toastUntil;

	protected VScreen(String title, Screen parent) {
		super(Component.literal(title));
		this.parent = parent;
	}

	protected <W extends Widget> W add(W widget) {
		widgets.add(widget);
		return widget;
	}

	@Override
	protected void init() {
		widgets.clear();
		popup = null;
		build();
	}

	/** Creates widgets for the current window size. Called on open and on resize. */
	protected abstract void build();

	public void openPopup(Widget widget) {
		if (widget instanceof dev.vantage.client.ui.widget.ColorPicker picker) {
			picker.clamp(width, height);
		}
		popup = widget;
	}

	public void closePopup() {
		popup = null;
	}

	/** Short status message shown at the bottom of the screen. */
	public void toast(String message) {
		toast = message;
		toastUntil = System.currentTimeMillis() + 3500;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float open = Theme.animations() ? openAnim.target(1).get() : 1;
		renderBackdrop(g, mouseX, mouseY, delta);
		g.pose().pushMatrix();
		if (open < 0.999f) {
			float offset = (1 - open) * 8;
			g.pose().translate(0, offset);
		}
		int mx = popup != null && popup.isHovered(mouseX, mouseY) ? -10_000 : mouseX;
		int my = popup != null && popup.isHovered(mouseX, mouseY) ? -10_000 : mouseY;
		renderContent(g, mx, my, delta);
		for (Widget widget : widgets) {
			if (widget.visible) {
				widget.render(g, mx, my, delta);
			}
		}
		renderOverlay(g, mx, my, delta);
		g.pose().popMatrix();
		if (popup != null) {
			g.nextStratum();
			popup.render(g, mouseX, mouseY, delta);
		}
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			g.nextStratum();
			int w = Text.width(toast) + 20;
			int x = (width - w) / 2;
			int y = height - 34;
			Render2D.panel(g, x, y, w, 20, 6, 0xF0181C25, Theme.accent());
			Text.draw(g, toast, x + 10, y + 6, Theme.TEXT);
		}
	}

	/** Background behind everything; defaults to the themed menu background. */
	protected void renderBackdrop(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Backgrounds.render(this, g, delta);
	}

	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
	}

	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float delta) {
	}

	/** Exposed so backgrounds can draw the vanilla panorama. */
	public void drawPanorama(GuiGraphics g, float delta) {
		renderPanorama(g, delta);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Drawn in render() instead, so the vanilla blur/dirt background is not applied.
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		if (popup != null) {
			if (popup.mouseClicked(mx, my, button)) {
				return true;
			}
			popup = null;
			return true;
		}
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget widget = widgets.get(i);
			if (widget.visible && widget.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		return onClick(mx, my, button) || super.mouseClicked(event, doubleClick);
	}

	/** Clicks that did not hit a widget. */
	protected boolean onClick(double mx, double my, int button) {
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (popup != null && popup.mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		boolean handled = false;
		for (Widget widget : widgets) {
			handled |= widget.mouseReleased(event.x(), event.y(), event.button());
		}
		return handled || super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (popup != null && popup.mouseDragged(event.x(), event.y(), event.button(), dx, dy)) {
			return true;
		}
		for (Widget widget : widgets) {
			if (widget.mouseDragged(event.x(), event.y(), event.button(), dx, dy)) {
				return true;
			}
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (popup != null) {
			return popup.mouseScrolled(mx, my, vertical);
		}
		for (int i = widgets.size() - 1; i >= 0; i--) {
			if (widgets.get(i).visible && widgets.get(i).mouseScrolled(mx, my, vertical)) {
				return true;
			}
		}
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (Widget widget : widgets) {
			if (widget.capturesKeyboard() && widget.keyPressed(event.key(), event.scancode(), event.modifiers())) {
				return true;
			}
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && popup != null) {
			popup = null;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String text = event.codepointAsString();
		for (Widget widget : widgets) {
			if (widget.capturesKeyboard() && widget.charTyped(text)) {
				return true;
			}
		}
		return super.charTyped(event);
	}

	@Override
	public void tick() {
		widgets.forEach(Widget::tick);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	// ------------------------------------------------------------------------------------------------ helpers

	protected void header(GuiGraphics g, int x, int y, String title, String subtitle) {
		Text.drawScaled(g, title, x, y, 1.6f, Theme.TEXT, false, Text.uiFace());
		if (subtitle != null) {
			Text.draw(g, subtitle, x, y + 17, Theme.MUTED);
		}
	}
}
