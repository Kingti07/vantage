package dev.vantage.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Text drawing with a selectable Minecraft font and arbitrary scale. The UI and every HUD element can pick
 * between the default font and the built-in Unifont ("uniform") variant.
 */
public final class Text {
	public enum Face {
		DEFAULT("Minecraft", FontDescription.DEFAULT),
		UNIFORM("Unifont", new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")));

		public final String label;
		public final FontDescription description;

		Face(String label, FontDescription description) {
			this.label = label;
			this.description = description;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static Face uiFace = Face.DEFAULT;

	private Text() {
	}

	public static void setUiFace(Face face) {
		uiFace = face == null ? Face.DEFAULT : face;
	}

	public static Face uiFace() {
		return uiFace;
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static Component styled(String text, Face face) {
		MutableComponent component = Component.literal(text);
		return face == Face.DEFAULT ? component : component.withStyle(style -> style.withFont(face.description));
	}

	public static int width(String text, Face face) {
		return face == Face.DEFAULT ? font().width(text) : font().width(styled(text, face));
	}

	public static int width(String text) {
		return width(text, uiFace);
	}

	public static int height() {
		return font().lineHeight;
	}

	public static void draw(GuiGraphics g, String text, int x, int y, int color, boolean shadow, Face face) {
		if (face == Face.DEFAULT) {
			g.drawString(font(), text, x, y, color, shadow);
		} else {
			g.drawString(font(), styled(text, face), x, y, color, shadow);
		}
	}

	public static void draw(GuiGraphics g, String text, int x, int y, int color) {
		draw(g, text, x, y, color, false, uiFace);
	}

	public static void drawShadow(GuiGraphics g, String text, int x, int y, int color) {
		draw(g, text, x, y, color, true, uiFace);
	}

	public static void drawCentered(GuiGraphics g, String text, int cx, int y, int color) {
		draw(g, text, cx - width(text) / 2, y, color, false, uiFace);
	}

	/** Draws text scaled around its top-left corner. */
	public static void drawScaled(GuiGraphics g, String text, float x, float y, float scale, int color, boolean shadow, Face face) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		draw(g, text, 0, 0, color, shadow, face);
		g.pose().popMatrix();
	}

	/** Truncates with an ellipsis so the text fits in {@code maxWidth} pixels. */
	public static String ellipsize(String text, int maxWidth) {
		if (width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		int available = maxWidth - width(ellipsis);
		StringBuilder builder = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (width(builder.toString() + c) > available) {
				break;
			}
			builder.append(c);
		}
		return builder + ellipsis;
	}

	/** Greedy word wrap into lines no wider than {@code maxWidth}. */
	public static java.util.List<String> wrap(String text, int maxWidth) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		for (String paragraph : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				String candidate = line.isEmpty() ? word : line + " " + word;
				if (width(candidate) > maxWidth && !line.isEmpty()) {
					lines.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(candidate);
				}
			}
			lines.add(line.toString());
		}
		return lines;
	}
}
