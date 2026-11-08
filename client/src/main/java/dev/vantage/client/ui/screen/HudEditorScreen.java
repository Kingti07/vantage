package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.config.ConfigManager;
import dev.vantage.client.hud.HudModule;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Backgrounds;
import dev.vantage.client.ui.SettingsPanel;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.SettingWidgets;
import dev.vantage.client.ui.widget.TextField;
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Drag-and-drop HUD editor. Elements are previewed live with their real settings. Features: snapping to screen
 * edges, centre lines and other elements (hold Shift to place freely), corner handle to resize, arrow keys to
 * nudge, a style panel for the selected element, an element list to show/hide elements, saved layouts and
 * per-element / full reset.
 */
public final class HudEditorScreen extends VScreen {
	private static final int SNAP = 4;
	private static final int HANDLE = 6;

	private HudModule selected;
	private HudModule dragging;
	private boolean resizing;
	private float dragOffsetX;
	private float dragOffsetY;
	private float resizeStartScale;
	private double resizeStartX;
	private final List<float[]> guides = new ArrayList<>(); // {x1,y1,x2,y2}
	private ScrollPanel stylePanel;
	private boolean showList;
	private boolean showGrid;
	private final long openedAt = System.currentTimeMillis();

	public HudEditorScreen(Screen parent) {
		super("HUD Editor", parent);
	}

	private List<HudModule> huds() {
		return VantageClient.get().modules().hudModules();
	}

	@Override
	protected void build() {
		int barWidth = 420;
		int bx = (width - barWidth) / 2;
		int y = 6;
		add(new Button(bx, y, 76, 16, () -> showList ? "Hide list" : "Elements", Button.Style.SECONDARY, () -> {
			showList = !showList;
			rebuildWidgets();
		}));
		add(new Button(bx + 80, y, 66, 16, () -> "Layouts", Button.Style.SECONDARY, this::openLayouts));
		add(new Button(bx + 150, y, 60, 16, () -> showGrid ? "Grid ✔" : "Grid", Button.Style.SECONDARY, () -> showGrid = !showGrid));
		add(new Button(bx + 214, y, 84, 16, () -> "Reset all", Button.Style.DANGER, () -> {
			huds().forEach(h -> {
				h.resetLayout();
				h.resetSettings();
			});
			selected = null;
			rebuildWidgets();
		}));
		add(new Button(bx + 302, y, 56, 16, () -> "Settings", Button.Style.GHOST, () -> minecraft.setScreen(new ModulesScreen(this, dev.vantage.client.module.Category.HUD))));
		add(new Button(bx + 362, y, 58, 16, () -> "Done", Button.Style.PRIMARY, this::onClose));

		if (showList) {
			ScrollPanel list = add(new ScrollPanel(8, 30, 150, height - 40));
			int ly = 0;
			for (HudModule hud : huds()) {
				int rowY = ly;
				list.decorate((g, off) -> {
					Render2D.rounded(g, 0, rowY, 144, 20, 4, hud == selected ? Colors.withAlpha(Theme.accent(), 120) : Theme.surface());
					Text.draw(g, Text.ellipsize(hud.name(), 100), 6, rowY + 6, hud.isEnabled() ? Theme.TEXT : Theme.FAINT);
				});
				SettingWidgets.Toggle toggle = new SettingWidgets.Toggle(hud::isEnabled, value -> {
					hud.setEnabled(value);
					if (value) {
						selected = hud;
					}
				});
				toggle.bounds(112, rowY + 3, 28, 14);
				list.add(toggle);
				ly += 22;
			}
			list.setContentHeight(ly);
		}

		if (selected != null) {
			int pw = 210;
			stylePanel = add(new ScrollPanel(width - pw - 8, 52, pw, height - 92));
			SettingsPanel.build(stylePanel, selected, this, this::rebuildStyle);
			add(new Button(width - pw - 8, height - 34, pw / 2 - 3, 18, () -> "Reset element", Button.Style.GHOST, () -> {
				selected.resetLayout();
				selected.resetSettings();
				rebuildStyle();
			}));
			add(new Button(width - pw / 2 - 5, height - 34, pw / 2 - 3, 18, () -> "Hide", Button.Style.DANGER, () -> {
				selected.setEnabled(false);
				selected = null;
				rebuildWidgets();
			}));
		} else {
			stylePanel = null;
		}
	}

	private void rebuildStyle() {
		if (stylePanel != null && selected != null) {
			SettingsPanel.build(stylePanel, selected, this, this::rebuildStyle);
		}
	}

	private void openLayouts() {
		ConfigManager config = VantageClient.get().config();
		List<String> layouts = config.hudLayouts();
		int w = 220;
		int h = 70 + Math.max(1, layouts.size()) * 22;
		LayoutsPopup popup = new LayoutsPopup((width - w) / 2, 30, w, Math.min(h, height - 40), config, layouts);
		openPopup(popup);
	}

	/** Popup listing saved HUD layouts with save / load / delete. */
	private final class LayoutsPopup extends Widget {
		private final ConfigManager config;
		private final List<String> layouts;
		private final TextField name = new TextField("Layout name");
		private final List<Button> buttons = new ArrayList<>();

		LayoutsPopup(int x, int y, int w, int h, ConfigManager config, List<String> layouts) {
			super(x, y, w, h);
			this.config = config;
			this.layouts = layouts;
			name.bounds(x + 8, y + 24, w - 76, 18);
			buttons.add(new Button(x + w - 64, y + 24, 56, 18, () -> "Save", Button.Style.PRIMARY, () -> {
				if (name.text().isBlank()) {
					toast("Enter a name for the layout.");
					return;
				}
				try {
					config.saveHudLayout(name.text());
					toast("Saved layout " + ConfigManager.sanitize(name.text()) + ".");
					closePopup();
				} catch (IOException e) {
					toast("Could not save: " + e.getMessage());
				}
			}));
			int ry = y + 50;
			for (String layout : layouts) {
				buttons.add(new Button(x + 8, ry, w - 76, 18, () -> layout, Button.Style.SECONDARY, () -> {
					try {
						config.loadHudLayout(layout);
						toast("Loaded layout " + layout + ".");
						closePopup();
						rebuildWidgets();
					} catch (IOException e) {
						toast("Could not load: " + e.getMessage());
					}
				}));
				buttons.add(new Button(x + w - 64, ry, 56, 18, () -> "Delete", Button.Style.DANGER, () -> {
					try {
						config.deleteHudLayout(layout);
						closePopup();
						openLayouts();
					} catch (IOException e) {
						toast(e.getMessage());
					}
				}));
				ry += 22;
			}
			name.focus(true);
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			Render2D.shadow(g, x, y, width, height, 8, 10, 0xFF000000);
			Render2D.panel(g, x, y, width, height, 8, 0xF2141820, Theme.border());
			Text.draw(g, "HUD layouts", x + 8, y + 8, Theme.TEXT);
			name.render(g, mouseX, mouseY, delta);
			if (layouts.isEmpty()) {
				Text.draw(g, "No saved layouts yet.", x + 8, y + 54, Theme.FAINT);
			}
			buttons.forEach(b -> b.render(g, mouseX, mouseY, delta));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my)) {
				return false;
			}
			name.mouseClicked(mx, my, button);
			for (Button b : buttons) {
				if (b.mouseClicked(mx, my, button)) {
					return true;
				}
			}
			return true;
		}

		@Override
		public boolean keyPressed(int key, int scancode, int modifiers) {
			return name.keyPressed(key, scancode, modifiers);
		}

		@Override
		public boolean charTyped(String text) {
			return name.charTyped(text);
		}

		@Override
		public boolean capturesKeyboard() {
			return true;
		}
	}

	// ------------------------------------------------------------------------------------------------ rendering

	@Override
	protected void renderBackdrop(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			Backgrounds.gradient(g, width, height);
		} else {
			g.fill(0, 0, width, height, 0x40000000);
		}
		if (showGrid) {
			for (int x = 0; x < width; x += 10) {
				g.fill(x, 0, x + 1, height, 0x14FFFFFF);
			}
			for (int y = 0; y < height; y += 10) {
				g.fill(0, y, width, y + 1, 0x14FFFFFF);
			}
		}
		g.fill(width / 2, 0, width / 2 + 1, height, 0x10FFFFFF);
		g.fill(0, height / 2, width, height / 2 + 1, 0x10FFFFFF);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		for (HudModule hud : huds()) {
			if (!hud.isEnabled()) {
				continue;
			}
			hud.render(g, width, height, true);
			int x = Math.round(hud.screenX(width, true));
			int y = Math.round(hud.screenY(height, true));
			int w = Math.round(hud.width(true) * hud.scale());
			int h = Math.round(hud.height(true) * hud.scale());
			boolean hovered = Render2D.inside(mouseX, mouseY, x, y, w, h);
			int outline = hud == selected ? Theme.accent() : hovered ? 0xA0FFFFFF : 0x40FFFFFF;
			Render2D.roundedBorder(g, x - 1, y - 1, w + 2, h + 2, 2, 1, outline);
			if (hud == selected || hovered) {
				Render2D.rect(g, x + w - HANDLE + 1, y + h - HANDLE + 1, HANDLE, HANDLE, Theme.accent());
				String label = hud.name() + "  " + Math.round(hud.scale() * 100) + "%";
				int ly = y > 12 ? y - 11 : y + h + 3;
				Text.draw(g, label, x, ly, Theme.TEXT);
			}
		}
		for (float[] guide : guides) {
			g.fill(Math.round(guide[0]), Math.round(guide[1]), Math.round(guide[2]) + 1, Math.round(guide[3]) + 1, Colors.withAlpha(Theme.accent(), 200));
		}
		long shown = System.currentTimeMillis() - openedAt;
		if (selected == null && dragging == null && shown < 6000) {
			// Short usage hint that fades out so it never hides elements for long.
			int alpha = (int) (200 * Math.min(1, (6000 - shown) / 1000f));
			String hint = Text.ellipsize("Drag to move · drag the corner or scroll to resize · Shift: no snapping · Arrows: nudge · Right click: reset", width - 20);
			int hw = Text.width(hint) + 12;
			Render2D.rounded(g, (width - hw) / 2, height / 2 + 30, hw, 16, 5, Colors.withAlpha(0x0B0D12, alpha));
			Text.draw(g, hint, (width - hw) / 2 + 6, height / 2 + 34, Colors.withAlpha(0xFFFFFF, alpha));
		}
		if (selected != null) {
			int pw = 210;
			Render2D.panel(g, width - pw - 14, 28, pw + 12, height - 32, 8, Theme.panel(), Theme.border());
			Text.draw(g, selected.name(), width - pw - 8, 36, Theme.TEXT);
		}
	}

	// ------------------------------------------------------------------------------------------------ interaction

	private HudModule elementAt(double mx, double my) {
		List<HudModule> list = huds();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule hud = list.get(i);
			if (!hud.isEnabled()) {
				continue;
			}
			float x = hud.screenX(width, true);
			float y = hud.screenY(height, true);
			float w = hud.width(true) * hud.scale();
			float h = hud.height(true) * hud.scale();
			if (mx >= x - 1 && my >= y - 1 && mx <= x + w + 1 && my <= y + h + 1) {
				return hud;
			}
		}
		return null;
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		HudModule hit = elementAt(mx, my);
		if (hit == null) {
			if (selected != null) {
				selected = null;
				rebuildWidgets();
			}
			return true;
		}
		if (button == 1) {
			hit.resetLayout();
			toast("Reset position of " + hit.name() + ".");
			return true;
		}
		float x = hit.screenX(width, true);
		float y = hit.screenY(height, true);
		float w = hit.width(true) * hit.scale();
		float h = hit.height(true) * hit.scale();
		resizing = mx >= x + w - HANDLE - 1 && my >= y + h - HANDLE - 1;
		dragging = hit;
		dragOffsetX = (float) (mx - x);
		dragOffsetY = (float) (my - y);
		resizeStartScale = hit.scale();
		resizeStartX = mx - x;
		if (selected != hit) {
			selected = hit;
			rebuildWidgets();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		double mx = event.x();
		double my = event.y();
		guides.clear();
		if (resizing) {
			float baseWidth = dragging.width(true);
			float x = dragging.screenX(width, true);
			float y = dragging.screenY(height, true);
			float newScale = (float) Math.max(0.4, (mx - x) / Math.max(1, baseWidth));
			newScale = Math.round(newScale * 20) / 20f;
			dragging.setScale(newScale);
			dragging.setScreenPosition(x, y, width, height, true);
			return true;
		}
		float w = dragging.width(true) * dragging.scale();
		float h = dragging.height(true) * dragging.scale();
		float x = (float) (mx - dragOffsetX);
		float y = (float) (my - dragOffsetY);
		boolean snap = !minecraft.hasShiftDown();
		if (snap) {
			float[] sx = snapAxis(x, w, width, true);
			float[] sy = snapAxis(y, h, height, false);
			x = sx[0];
			y = sy[0];
		}
		x = Math.max(0, Math.min(width - w, x));
		y = Math.max(0, Math.min(height - h, y));
		dragging.setScreenPosition(x, y, width, height, true);
		return true;
	}

	/** Snaps an element edge or centre to screen edges/centre and other elements; records guide lines. */
	private float[] snapAxis(float pos, float size, int screenSize, boolean horizontal) {
		List<Float> targets = new ArrayList<>();
		targets.add(2f);
		targets.add(screenSize - 2f);
		targets.add(screenSize / 2f);
		for (HudModule other : huds()) {
			if (other == dragging || !other.isEnabled()) {
				continue;
			}
			float o = horizontal ? other.screenX(width, true) : other.screenY(height, true);
			float os = (horizontal ? other.width(true) : other.height(true)) * other.scale();
			targets.add(o);
			targets.add(o + os);
			targets.add(o + os / 2);
		}
		float[] edges = {pos, pos + size, pos + size / 2};
		float best = SNAP + 1;
		float result = pos;
		float line = -1;
		for (float target : targets) {
			for (int i = 0; i < edges.length; i++) {
				float distance = Math.abs(edges[i] - target);
				if (distance < best) {
					best = distance;
					line = target;
					result = i == 0 ? target : i == 1 ? target - size : target - size / 2;
				}
			}
		}
		if (line >= 0) {
			guides.add(horizontal ? new float[]{line, 0, line, height} : new float[]{0, line, width, line});
		}
		return new float[]{result};
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			resizing = false;
			guides.clear();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		HudModule hit = elementAt(mx, my);
		if (hit != null && popup == null && (stylePanel == null || !stylePanel.isHovered(mx, my))) {
			float x = hit.screenX(width, true);
			float y = hit.screenY(height, true);
			hit.setScale(hit.scale() + (float) Math.signum(vertical) * 0.05f);
			hit.setScreenPosition(x, y, width, height, true);
			return true;
		}
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (popup == null && selected != null && widgets.stream().noneMatch(Widget::capturesKeyboard)) {
			int step = event.hasShiftDown() ? 10 : 1;
			float x = selected.screenX(width, true);
			float y = selected.screenY(height, true);
			switch (event.key()) {
				case GLFW.GLFW_KEY_LEFT -> x -= step;
				case GLFW.GLFW_KEY_RIGHT -> x += step;
				case GLFW.GLFW_KEY_UP -> y -= step;
				case GLFW.GLFW_KEY_DOWN -> y += step;
				case GLFW.GLFW_KEY_DELETE -> {
					selected.setEnabled(false);
					selected = null;
					rebuildWidgets();
					return true;
				}
				default -> {
					return super.keyPressed(event);
				}
			}
			selected.setScreenPosition(x, y, width, height, true);
			return true;
		}
		if (popup != null && popup.capturesKeyboard() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
			return popup.keyPressed(event.key(), event.scancode(), event.modifiers());
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (popup != null && popup.capturesKeyboard()) {
			return popup.charTyped(event.codepointAsString());
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
