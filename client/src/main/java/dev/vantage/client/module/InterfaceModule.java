package dev.vantage.client.module;

import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.render.Text;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Client chrome and cosmetics: theme colours, menu background, UI font and the custom main menu. Always on.
 * All cosmetics are client-side only.
 */
public final class InterfaceModule extends Module {
	public enum Background {
		GRADIENT("Animated gradient"), PANORAMA("Minecraft panorama"), IMAGE("Custom image");

		private final String label;

		Background(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public final BooleanSetting customMenu = add(new BooleanSetting("customMenu", "Vantage main menu", "Replace the title screen with the Vantage menu.", true));
	public final ColorSetting accent = add(new ColorSetting("accent", "Accent colour", "Primary UI colour.", 0xFF7C5CFF, false));
	public final ColorSetting accentSecondary = add(new ColorSetting("accent2", "Secondary colour", "Second gradient colour.", 0xFF22C3A6, false));
	public final ColorSetting panelColor = add(new ColorSetting("panel", "Panel colour", "Background of UI panels; alpha controls transparency.", 0xE6101319, false));
	public final EnumSetting<Background> background = add(new EnumSetting<>("background", "Menu background", "Background of Vantage menus.", Background.GRADIENT));
	public final NumberSetting backgroundImage = add(new NumberSetting("backgroundImage", "Background image", "Which image from config/vantage/backgrounds to use.", 0, 0, 99, 1));
	public final EnumSetting<Text.Face> uiFont = add(new EnumSetting<>("uiFont", "Interface font", "Font used by Vantage menus.", Text.Face.DEFAULT));
	public final BooleanSetting animations = add(new BooleanSetting("animations", "Animations", "Menu transitions and hover animations.", true));
	public final BooleanSetting hideHudInDebug = add(new BooleanSetting("hideHudInDebug", "Hide HUD with F3", "Hide Vantage HUD elements while the debug screen is open.", true));
	public final Keybind hudEditorKey;
	public final Keybind menuKey;

	public InterfaceModule() {
		super("interface", "Interface", "Theme, menu background, fonts and the Vantage main menu.", Category.INTERFACE, true);
		setAlwaysEnabled();
		backgroundImage.visibleWhen(() -> background.get() == Background.IMAGE);
		uiFont.onChange(Text::setUiFace);
		hudEditorKey = keybind("hudeditor", "Open HUD editor", InputKey.key(GLFW.GLFW_KEY_RIGHT_SHIFT));
		menuKey = keybind("menu", "Open Vantage menu", InputKey.key(GLFW.GLFW_KEY_RIGHT_CONTROL));
	}

	public int accentColor() {
		return accent.get();
	}

	public int secondaryColor() {
		return accentSecondary.get();
	}
}
