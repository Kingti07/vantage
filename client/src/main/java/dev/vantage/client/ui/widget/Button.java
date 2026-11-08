package dev.vantage.client.ui.widget;

import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.util.Anim;
import dev.vantage.client.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

/** Rounded button with hover animation and several visual styles. */
public class Button extends Widget {
	public enum Style {PRIMARY, SECONDARY, GHOST, DANGER, MENU}

	private Supplier<String> label;
	private final Runnable action;
	private Style style;
	private final Anim hover = new Anim(0, 16);

	public Button(String label, Style style, Runnable action) {
		this(0, 0, Math.max(60, Text.width(label) + 20), 20, () -> label, style, action);
	}

	public Button(int x, int y, int width, int height, Supplier<String> label, Style style, Runnable action) {
		super(x, y, width, height);
		this.label = label;
		this.style = style;
		this.action = action;
	}

	public Button label(String text) {
		this.label = () -> text;
		return this;
	}

	public Button style(Style style) {
		this.style = style;
		return this;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (!visible) {
			return;
		}
		boolean hovered = enabled && isHovered(mouseX, mouseY);
		float h = Theme.animations() ? hover.target(hovered ? 1 : 0).get() : (hovered ? 1 : 0);
		int bg;
		int fg = Theme.TEXT;
		int border = 0;
		switch (style) {
			case PRIMARY -> bg = Colors.shade(Theme.accent(), 0.12f * h);
			case DANGER -> {
				bg = Colors.lerp(0x40FF5C7A, 0x66FF5C7A, h);
				fg = Theme.DANGER;
			}
			case GHOST -> {
				bg = Colors.withAlpha(0xFFFFFF, Math.round(18 * h));
				border = Theme.border();
			}
			case MENU -> {
				bg = Colors.lerp(Colors.withAlpha(Theme.panel(), 150), Colors.withAlpha(Theme.accent(), 200), h);
				border = Colors.lerp(Theme.border(), Theme.accent(), h);
			}
			default -> bg = Colors.lerp(Theme.surface(), Theme.surfaceHover(), h);
		}
		if (!enabled) {
			bg = Colors.fade(bg, 0.5f);
			fg = Theme.FAINT;
		}
		Render2D.rounded(g, x, y, width, height, Theme.RADIUS, bg);
		if (border != 0) {
			Render2D.roundedBorder(g, x, y, width, height, Theme.RADIUS, 1, border);
		}
		if (style == Style.MENU) {
			int barWidth = Math.round(3 * h);
			if (barWidth > 0) {
				Render2D.rounded(g, x + 4, y + 5, barWidth, height - 10, 1, Theme.TEXT);
			}
			String text = label.get();
			Text.draw(g, text, x + 14 + Math.round(4 * h), y + (height - 8) / 2, fg);
		} else {
			String text = Text.ellipsize(label.get(), width - 8);
			Text.draw(g, text, x + (width - Text.width(text)) / 2, y + (height - 8) / 2, fg);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (enabled && button == 0 && isHovered(mx, my)) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			action.run();
			return true;
		}
		return false;
	}
}
