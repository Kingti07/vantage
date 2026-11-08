package dev.vantage.client.ui.widget;

import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.util.Anim;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Controls bound directly to settings. */
public final class SettingWidgets {
	private SettingWidgets() {
	}

	/** Animated on/off switch. Can be bound to a setting or to arbitrary getter/setter. */
	public static final class Toggle extends Widget {
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;
		private final Anim anim = new Anim(0, 18);
		private boolean initialized;

		public Toggle(BooleanSetting setting) {
			this(setting::enabled, setting::set);
		}

		public Toggle(BooleanSupplier getter, Consumer<Boolean> setter) {
			super(0, 0, 28, 14);
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean on = getter.getAsBoolean();
			if (!initialized) {
				anim.snap(on ? 1 : 0);
				initialized = true;
			}
			float t = Theme.animations() ? anim.target(on ? 1 : 0).get() : (on ? 1 : 0);
			int track = Colors.lerp(0xFF2A303C, Theme.accent(), t);
			if (!enabled) {
				track = Colors.fade(track, 0.4f);
			}
			Render2D.rounded(g, x, y, width, height, height / 2, track);
			int knob = height - 4;
			int kx = x + 2 + Math.round((width - knob - 4) * t);
			Render2D.circle(g, kx + knob / 2, y + height / 2, knob / 2, 0xFFFFFFFF);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (enabled && button == 0 && isHovered(mx, my)) {
				setter.accept(!getter.getAsBoolean());
				return true;
			}
			return false;
		}
	}

	public static final class Slider extends Widget {
		private final NumberSetting setting;
		private boolean dragging;

		public Slider(NumberSetting setting) {
			super(0, 0, 120, 14);
			this.setting = setting;
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			double fraction = (setting.get() - setting.min()) / (setting.max() - setting.min());
			int trackY = y + height / 2 - 2;
			int valueWidth = 38;
			int trackWidth = width - valueWidth - 6;
			Render2D.rounded(g, x, trackY, trackWidth, 4, 2, 0xFF2A303C);
			Render2D.rounded(g, x, trackY, Math.max(4, (int) Math.round(trackWidth * fraction)), 4, 2, Theme.accent());
			int knobX = x + (int) Math.round((trackWidth - 8) * fraction);
			boolean hovered = isHovered(mouseX, mouseY) || dragging;
			Render2D.circle(g, knobX + 4, y + height / 2, hovered ? 5 : 4, 0xFFFFFFFF);
			String value = setting.format();
			Text.draw(g, value, x + width - Text.width(value), y + (height - 8) / 2, Theme.MUTED);
		}

		private void update(double mx) {
			int trackWidth = width - 38 - 6;
			double fraction = Math.max(0, Math.min(1, (mx - x) / (double) trackWidth));
			setting.set(setting.min() + fraction * (setting.max() - setting.min()));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button == 0 && isHovered(mx, my) && mx < x + width - 40) {
				dragging = true;
				update(mx);
				return true;
			}
			return false;
		}

		@Override
		public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
			if (dragging) {
				update(mx);
				return true;
			}
			return false;
		}

		@Override
		public boolean mouseReleased(double mx, double my, int button) {
			boolean was = dragging;
			dragging = false;
			return was;
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double amount) {
			if (isHovered(mx, my)) {
				setting.set(setting.get() + Math.signum(amount) * Math.max(setting.step(), (setting.max() - setting.min()) / 100));
				return true;
			}
			return false;
		}
	}

	/** Shows the current choice; left/right click (or the arrows) cycle through the options. */
	public static class Cycle<E extends Enum<E>> extends Widget {
		private final EnumSetting<E> setting;

		public Cycle(EnumSetting<E> setting) {
			super(0, 0, 120, 16);
			this.setting = setting;
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean hovered = isHovered(mouseX, mouseY);
			Render2D.rounded(g, x, y, width, height, 4, hovered ? Theme.surfaceHover() : Theme.surface());
			Text.draw(g, "‹", x + 5, y + (height - 8) / 2, Theme.MUTED);
			Text.draw(g, "›", x + width - 9, y + (height - 8) / 2, Theme.MUTED);
			String label = Text.ellipsize(setting.get().toString(), width - 24);
			Text.draw(g, label, x + (width - Text.width(label)) / 2, y + (height - 8) / 2, Theme.TEXT);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my)) {
				return false;
			}
			boolean backwards = button == 1 || mx < x + 14;
			setting.cycle(backwards ? -1 : 1);
			return true;
		}
	}

	/** Colour swatch; clicking opens a {@link ColorPicker} popup. */
	public static final class ColorSwatch extends Widget {
		private final ColorSetting setting;
		private final Consumer<Widget> popupOpener;

		public ColorSwatch(ColorSetting setting, Consumer<Widget> popupOpener) {
			super(0, 0, 40, 14);
			this.setting = setting;
			this.popupOpener = popupOpener;
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			// Checkerboard to visualise transparency.
			for (int i = 0; i < width; i += 4) {
				for (int j = 0; j < height; j += 4) {
					g.fill(x + i, y + j, Math.min(x + i + 4, x + width), Math.min(y + j + 4, y + height), ((i + j) / 4) % 2 == 0 ? 0xFF888888 : 0xFFCCCCCC);
				}
			}
			g.fill(x, y, x + width, y + height, setting.color());
			Render2D.roundedBorder(g, x, y, width, height, 2, 1, isHovered(mouseX, mouseY) ? Theme.accent() : Theme.border());
			if (setting.chroma()) {
				Text.draw(g, "RGB", x + (width - Text.width("RGB")) / 2, y + 3, 0xFF000000);
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (isHovered(mx, my)) {
				popupOpener.accept(new ColorPicker(setting, x, y + height + 4));
				return true;
			}
			return false;
		}
	}
}
