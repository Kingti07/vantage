package dev.vantage.client.ui.widget;

import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HSV colour picker popup: saturation/value square, hue strip, alpha strip, chroma toggle and a hex readout.
 * Changes apply live so the HUD or UI previews them immediately.
 */
public final class ColorPicker extends Widget {
	private static final int SIZE = 90;
	private final ColorSetting setting;
	private float hue;
	private float saturation;
	private float value;
	private int dragging = -1; // 0 = square, 1 = hue, 2 = alpha

	public ColorPicker(ColorSetting setting, int x, int y) {
		super(x, y, SIZE + 46, SIZE + 34);
		this.setting = setting;
		float[] hsv = Colors.toHsv(setting.get());
		hue = hsv[0];
		saturation = hsv[1];
		value = hsv[2];
	}

	public ColorPicker clamp(int screenWidth, int screenHeight) {
		x = Math.max(4, Math.min(x, screenWidth - width - 4));
		if (y + height > screenHeight - 4) {
			y = Math.max(4, y - height - 22);
		}
		return this;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Render2D.shadow(g, x, y, width, height, 6, 10, 0xFF000000);
		Render2D.panel(g, x, y, width, height, 6, 0xF2141820, Theme.border());
		int sx = x + 6;
		int sy = y + 6;
		// Saturation (x) / value (y) square: horizontal white→hue, then vertical transparent→black.
		Render2D.horizontalGradient(g, sx, sy, SIZE, SIZE, 0xFFFFFFFF, Colors.hsv(hue, 1, 1, 255));
		g.fillGradient(sx, sy, sx + SIZE, sy + SIZE, 0x00000000, 0xFF000000);
		int cx = sx + Math.round(saturation * SIZE);
		int cy = sy + Math.round((1 - value) * SIZE);
		Render2D.roundedBorder(g, cx - 3, cy - 3, 6, 6, 3, 1, 0xFFFFFFFF);

		// Hue strip
		int hx = sx + SIZE + 6;
		for (int i = 0; i < SIZE; i++) {
			g.fill(hx, sy + i, hx + 10, sy + i + 1, Colors.hsv(i / (float) SIZE, 1, 1, 255));
		}
		int hy = sy + Math.round(hue * SIZE);
		g.fill(hx - 1, hy - 1, hx + 11, hy + 1, 0xFFFFFFFF);

		// Alpha strip
		int ax = hx + 16;
		int opaque = Colors.withAlpha(setting.get(), 255);
		for (int i = 0; i < SIZE; i += 4) {
			g.fill(ax, sy + i, ax + 5, Math.min(sy + i + 4, sy + SIZE), (i / 4) % 2 == 0 ? 0xFF888888 : 0xFFCCCCCC);
			g.fill(ax + 5, sy + i, ax + 10, Math.min(sy + i + 4, sy + SIZE), (i / 4) % 2 == 0 ? 0xFFCCCCCC : 0xFF888888);
		}
		g.fillGradient(ax, sy, ax + 10, sy + SIZE, opaque, Colors.withAlpha(opaque, 0));
		int ay = sy + Math.round((1 - Colors.alpha(setting.get()) / 255f) * SIZE);
		g.fill(ax - 1, ay - 1, ax + 11, ay + 1, 0xFFFFFFFF);

		// Footer: hex + chroma toggle
		int fy = sy + SIZE + 6;
		Text.draw(g, Colors.toHex(setting.get()), sx, fy + 4, Theme.MUTED);
		if (setting.allowChroma()) {
			String label = setting.chroma() ? "RGB ✔" : "RGB";
			int w = Text.width(label) + 10;
			int bx = x + width - w - 6;
			boolean hovered = Render2D.inside(mouseX, mouseY, bx, fy, w, 16);
			Render2D.rounded(g, bx, fy, w, 16, 4, setting.chroma() ? Theme.accent() : hovered ? Theme.surfaceHover() : Theme.surface());
			Text.draw(g, label, bx + 5, fy + 4, Theme.TEXT);
		}
	}

	private void apply() {
		setting.set(Colors.hsv(hue, saturation, value, Colors.alpha(setting.get())));
	}

	private void update(double mx, double my) {
		int sx = x + 6;
		int sy = y + 6;
		float fy = (float) Math.max(0, Math.min(1, (my - sy) / SIZE));
		switch (dragging) {
			case 0 -> {
				saturation = (float) Math.max(0, Math.min(1, (mx - sx) / SIZE));
				value = 1 - fy;
				apply();
			}
			case 1 -> {
				hue = Math.min(0.999f, fy);
				apply();
			}
			case 2 -> setting.set(Colors.withAlpha(setting.get(), Math.round((1 - fy) * 255)));
			default -> {
			}
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!isHovered(mx, my)) {
			return false;
		}
		int sx = x + 6;
		int sy = y + 6;
		if (Render2D.inside(mx, my, sx, sy, SIZE, SIZE)) {
			dragging = 0;
		} else if (Render2D.inside(mx, my, sx + SIZE + 6, sy, 10, SIZE)) {
			dragging = 1;
		} else if (Render2D.inside(mx, my, sx + SIZE + 22, sy, 10, SIZE)) {
			dragging = 2;
		} else if (setting.allowChroma() && my > sy + SIZE + 4 && mx > x + width - 50) {
			setting.setChroma(!setting.chroma());
			return true;
		}
		update(mx, my);
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging >= 0) {
			update(mx, my);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = dragging >= 0;
		dragging = -1;
		return was;
	}
}
