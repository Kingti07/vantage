package dev.vantage.client.render;

import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 2D drawing primitives on top of {@link GuiGraphics}: rounded rectangles, rings, soft shadows and gradients.
 * Rounded shapes are rasterised as one fill per pixel row in the corner regions, which is cheap for the small
 * radii used by the UI and keeps edges crisp at every GUI scale.
 */
public final class Render2D {
	private Render2D() {
	}

	public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
		if (w > 0 && h > 0 && Colors.alpha(color) > 0) {
			g.fill(x, y, x + w, y + h, color);
		}
	}

	public static void rounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		if (r == 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int row = 0; row < r; row++) {
			int inset = inset(r, row);
			g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
			g.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
		}
	}

	/** Rounded outline of the given thickness; safe with translucent colours (no overdraw). */
	public static void roundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int thickness, int color) {
		if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		int t = Math.max(1, thickness);
		for (int row = 0; row < h; row++) {
			int outer = row < r ? inset(r, row) : row >= h - r ? inset(r, h - row - 1) : 0;
			boolean edgeRow = row < t || row >= h - t;
			if (edgeRow) {
				g.fill(x + outer, y + row, x + w - outer, y + row + 1, color);
				continue;
			}
			int innerRow = row - t;
			int innerH = h - 2 * t;
			int ir = Math.max(0, r - t);
			int inner = innerRow < ir ? inset(ir, innerRow) : innerRow >= innerH - ir ? inset(ir, innerH - innerRow - 1) : 0;
			int leftEnd = x + t + inner;
			int rightStart = x + w - t - inner;
			g.fill(x + outer, y + row, Math.max(x + outer, leftEnd), y + row + 1, color);
			g.fill(Math.min(x + w - outer, rightStart), y + row, x + w - outer, y + row + 1, color);
		}
	}

	/** Fill plus border in one call. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius, int fill, int border) {
		rounded(g, x, y, w, h, radius, fill);
		roundedBorder(g, x, y, w, h, radius, 1, border);
	}

	/** Soft drop shadow made of a few expanding translucent rounded layers. */
	public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius, int size, int color) {
		int baseAlpha = Colors.alpha(color);
		for (int i = size; i > 0; i -= 2) {
			int alpha = Math.round(baseAlpha * (1 - i / (float) (size + 1)) * 0.35f);
			rounded(g, x - i, y - i + size / 3, w + i * 2, h + i * 2, radius + i, Colors.withAlpha(color, alpha));
		}
	}

	public static void verticalGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
		g.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	public static void horizontalGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
		int steps = Math.max(1, Math.min(w, 64));
		for (int i = 0; i < steps; i++) {
			int x0 = x + w * i / steps;
			int x1 = x + w * (i + 1) / steps;
			g.fill(x0, y, x1, y + h, Colors.lerp(left, right, steps == 1 ? 0 : i / (float) (steps - 1)));
		}
	}

	/** Filled circle (used for knobs and decorative blobs). */
	public static void circle(GuiGraphics g, int cx, int cy, int radius, int color) {
		rounded(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	private static int inset(int r, int row) {
		double dy = r - row - 0.5;
		double dx = Math.sqrt(Math.max(0, r * r - dy * dy));
		return (int) Math.round(r - dx);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
