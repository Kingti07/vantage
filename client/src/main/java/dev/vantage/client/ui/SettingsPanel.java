package dev.vantage.client.ui;

import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.module.Module;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import dev.vantage.client.setting.Setting;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.KeybindWidget;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.SettingWidgets;
import dev.vantage.client.ui.widget.Widget;

import java.util.List;

/**
 * Generates the settings UI for a module from its {@link Setting}s and keybinds: one row per option with the
 * name, description and a matching control. Rows hidden by {@code visibleWhen} are re-evaluated on rebuild.
 */
public final class SettingsPanel {
	private static final int ROW = 30;
	private static final int CONTROL_WIDTH = 120;

	private SettingsPanel() {
	}

	/** Fills {@code panel} with controls for {@code module}; {@code screen} hosts popups. Returns content height. */
	public static int build(ScrollPanel panel, Module module, VScreen screen, Runnable rebuild) {
		panel.clear();
		int width = panel.width - 8;
		int y = 4;
		for (Setting<?> setting : module.settings()) {
			if (!setting.isVisible()) {
				continue;
			}
			Widget control = control(setting, screen, panel, rebuild);
			if (control == null) {
				continue;
			}
			addRow(panel, y, width, setting.name(), setting.description(), control);
			y += ROW;
		}
		List<Keybind> keybinds = module.keybinds();
		if (!keybinds.isEmpty()) {
			int headerY = y + 4;
			panel.decorate((g, off) -> Text.draw(g, "KEYBINDS", 4, headerY, Theme.FAINT));
			y += 18;
			for (Keybind keybind : keybinds) {
				addRow(panel, y, width, keybind.name(), "Left click to add a key, right click to clear.", new KeybindWidget(keybind));
				y += ROW;
			}
		}
		Button reset = new Button(4, y + 6, 110, 18, () -> "Reset to defaults", Button.Style.GHOST, () -> {
			module.resetSettings();
			rebuild.run();
		});
		panel.add(reset);
		panel.setContentHeight(y + 30);
		return y + 30;
	}

	private static void addRow(ScrollPanel panel, int y, int width, String name, String description, Widget control) {
		int controlWidth = control instanceof SettingWidgets.Toggle ? 28 : control instanceof SettingWidgets.ColorSwatch ? 40 : CONTROL_WIDTH;
		control.bounds(width - controlWidth, y + (ROW - control.height) / 2 - 2, controlWidth, control.height);
		panel.add(control);
		int textWidth = width - controlWidth - 12;
		panel.decorate((g, off) -> {
			Render2D.rect(g, 4, y + ROW - 3, width - 4, 1, 0x14FFFFFF);
			Text.draw(g, Text.ellipsize(name, textWidth), 4, y + 4, Theme.TEXT);
			Text.draw(g, Text.ellipsize(description, textWidth), 4, y + 15, Theme.FAINT);
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Widget control(Setting<?> setting, VScreen screen, ScrollPanel panel, Runnable rebuild) {
		if (setting instanceof BooleanSetting bool) {
			return new SettingWidgets.Toggle(bool::enabled, value -> {
				bool.set(value);
				rebuild.run();
			});
		}
		if (setting instanceof NumberSetting number) {
			return new SettingWidgets.Slider(number);
		}
		if (setting instanceof EnumSetting enumSetting) {
			return new SettingWidgets.Cycle(enumSetting) {
				@Override
				public boolean mouseClicked(double mx, double my, int button) {
					boolean handled = super.mouseClicked(mx, my, button);
					if (handled) {
						rebuild.run();
					}
					return handled;
				}
			};
		}
		if (setting instanceof ColorSetting color) {
			return new SettingWidgets.ColorSwatch(color, picker -> {
				// The swatch reports content coordinates; convert to screen coordinates for the popup layer.
				picker.x += panel.x;
				picker.y += panel.y - panel.scrollOffset();
				screen.openPopup(picker);
			});
		}
		return null;
	}
}
