package dev.vantage.client.ui.widget;

import dev.vantage.client.render.Render2D;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.util.Anim;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Vertically scrolling container. Children are laid out by the owner in "content" coordinates (relative to
 * the panel's top-left, before scrolling); the panel offsets, clips and forwards events.
 */
public class ScrollPanel extends Widget {
	private final List<Widget> children = new ArrayList<>();
	private final List<BiConsumer<GuiGraphics, Integer>> decorations = new ArrayList<>();
	private int contentHeight;
	private final Anim scroll = new Anim(0, 20);
	private float targetScroll;
	private boolean draggingBar;

	public ScrollPanel(int x, int y, int width, int height) {
		super(x, y, width, height);
	}

	public void clear() {
		children.clear();
		decorations.clear();
		contentHeight = 0;
	}

	/** Adds a child positioned in content coordinates. */
	public <W extends Widget> W add(W widget) {
		children.add(widget);
		contentHeight = Math.max(contentHeight, widget.y + widget.height);
		return widget;
	}

	/** Custom drawing (labels, separators) in content coordinates; receives the current scroll offset. */
	public void decorate(BiConsumer<GuiGraphics, Integer> decoration) {
		decorations.add(decoration);
	}

	public void setContentHeight(int height) {
		contentHeight = Math.max(contentHeight, height);
	}

	public List<Widget> children() {
		return children;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - height + 6);
	}

	/** Current scroll offset in pixels. */
	public int scrollOffset() {
		return Math.round(targetScroll);
	}

	public void scrollToTop() {
		targetScroll = 0;
		scroll.snap(0);
	}

	private int offset() {
		targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll));
		return Math.round(scroll.target(targetScroll).get());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int off = offset();
		g.enableScissor(x, y, x + width, y + height);
		g.pose().pushMatrix();
		g.pose().translate(x, y - off);
		int localMouseX = mouseX - x;
		int localMouseY = isHovered(mouseX, mouseY) ? mouseY - y + off : -10_000;
		for (BiConsumer<GuiGraphics, Integer> decoration : decorations) {
			decoration.accept(g, off);
		}
		for (Widget child : children) {
			if (child.visible && child.y + child.height >= off && child.y <= off + height) {
				child.render(g, localMouseX, localMouseY, delta);
			}
		}
		g.pose().popMatrix();
		g.disableScissor();
		if (maxScroll() > 0) {
			int barHeight = Math.max(20, height * height / Math.max(1, contentHeight));
			int barY = y + Math.round((height - barHeight) * (off / (float) maxScroll()));
			Render2D.rounded(g, x + width - 3, barY, 3, barHeight, 1, draggingBar ? Theme.accent() : 0x50FFFFFF);
		}
	}

	private double localX(double mx) {
		return mx - x;
	}

	private double localY(double my) {
		return my - y + Math.round(scroll.get());
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!isHovered(mx, my)) {
			// Let focused children (text fields) lose focus.
			for (Widget child : children) {
				if (child instanceof TextField field) {
					field.focus(false);
				}
			}
			return false;
		}
		if (maxScroll() > 0 && mx >= x + width - 6) {
			draggingBar = true;
			return true;
		}
		boolean handled = false;
		for (int i = children.size() - 1; i >= 0; i--) {
			Widget child = children.get(i);
			if (child.visible && child.mouseClicked(localX(mx), localY(my), button)) {
				handled = true;
				break;
			}
		}
		for (Widget child : children) {
			if (child instanceof TextField field && !field.isHovered(localX(mx), localY(my))) {
				field.focus(false);
			}
		}
		return handled;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		draggingBar = false;
		boolean handled = false;
		for (Widget child : children) {
			handled |= child.mouseReleased(localX(mx), localY(my), button);
		}
		return handled;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (draggingBar) {
			targetScroll += (float) (dy * contentHeight / Math.max(1, height));
			return true;
		}
		for (Widget child : children) {
			if (child.mouseDragged(localX(mx), localY(my), button, dx, dy)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		if (!isHovered(mx, my)) {
			return false;
		}
		for (Widget child : children) {
			if (child.visible && child.mouseScrolled(localX(mx), localY(my), amount)) {
				return true;
			}
		}
		targetScroll -= (float) (amount * 24);
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		for (Widget child : children) {
			if (child.capturesKeyboard() && child.keyPressed(key, scancode, modifiers)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean charTyped(String text) {
		for (Widget child : children) {
			if (child.capturesKeyboard() && child.charTyped(text)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean capturesKeyboard() {
		return children.stream().anyMatch(Widget::capturesKeyboard);
	}

	@Override
	public void tick() {
		children.forEach(Widget::tick);
	}
}
