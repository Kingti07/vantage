package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.config.ConfigManager;
import dev.vantage.client.hud.HudModule;
import dev.vantage.client.module.InterfaceModule;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Backgrounds;
import dev.vantage.client.ui.SettingsPanel;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.TextField;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.io.IOException;
import java.util.List;

/**
 * Client-side cosmetics and customisation: theme colours, menu backgrounds, fonts, GUI scale, HUD style
 * presets and configuration profiles. Capes, hats and pets have their own {@link PlayerCosmeticsScreen}.
 */
public final class CosmeticsScreen extends VScreen {
	private static final int[][] THEMES = {
		{0xFF7C5CFF, 0xFF22C3A6}, {0xFF4F8CFF, 0xFF7CE0FF}, {0xFFFF5C7A, 0xFFFFB547}, {0xFF3DDC97, 0xFF4F8CFF},
		{0xFFE86BFF, 0xFF7C5CFF}, {0xFFFF7A45, 0xFFFF5C7A}, {0xFFEDEDED, 0xFF8A93A6}, {0xFF22C3A6, 0xFF3DDC97}
	};
	private static final String[] THEME_NAMES = {"Vantage", "Ocean", "Sunset", "Mint", "Neon", "Ember", "Mono", "Aqua"};

	private ScrollPanel interfaceSettings;
	private TextField profileName;

	public CosmeticsScreen(Screen parent) {
		super("Cosmetics", parent);
	}

	private InterfaceModule ui() {
		return VantageClient.get().interfaceModule();
	}

	@Override
	protected void build() {
		int left = 20;
		int columnWidth = Math.min(300, (width - 60) / 2);
		int right = left + columnWidth + 20;

		// Theme presets
		int y = 70;
		for (int i = 0; i < THEMES.length; i++) {
			int[] theme = THEMES[i];
			int index = i;
			int x = left + (i % 4) * ((columnWidth + 6) / 4);
			int ty = y + (i / 4) * 26;
			add(new Button(x, ty, (columnWidth + 6) / 4 - 6, 20, () -> THEME_NAMES[index], Button.Style.GHOST, () -> {
				ui().accent.set(theme[0]);
				ui().accentSecondary.set(theme[1]);
			}));
		}

		// Interface settings (accent colours, background, font, animations)
		interfaceSettings = add(new ScrollPanel(left, y + 64, columnWidth, height - (y + 64) - 40));
		SettingsPanel.build(interfaceSettings, ui(), this, () -> SettingsPanel.build(interfaceSettings, ui(), this, this::rebuildWidgets));

		// Right column: backgrounds, GUI scale, HUD styles, profiles
		int ry = 70;
		add(new Button(right, ry, columnWidth / 2 - 3, 18, () -> "Open backgrounds folder", Button.Style.GHOST,
			() -> Util.getPlatform().openPath(Backgrounds.folder())));
		add(new Button(right + columnWidth / 2 + 3, ry, columnWidth / 2 - 3, 18, () -> "Next image", Button.Style.GHOST, () -> {
			ui().background.set(InterfaceModule.Background.IMAGE);
			ui().backgroundImage.set(ui().backgroundImage.get() + 1 >= Math.max(1, Backgrounds.images().size()) ? 0 : ui().backgroundImage.get() + 1);
		}));
		ry += 40;
		add(new Button(right, ry, columnWidth, 18, () -> "GUI scale: " + guiScaleLabel(), Button.Style.SECONDARY, this::cycleGuiScale));
		ry += 40;
		String[] styles = {"Minimal", "Panels", "Outlined", "Glass"};
		for (int i = 0; i < styles.length; i++) {
			int index = i;
			add(new Button(right + i * ((columnWidth + 6) / 4), ry, (columnWidth + 6) / 4 - 6, 18, () -> styles[index], Button.Style.GHOST, () -> {
				applyHudStyle(index);
				toast("Applied the " + styles[index] + " HUD style to every element.");
			}));
		}
		ry += 40;
		profileName = add(new TextField("New profile name"));
		profileName.bounds(right, ry, columnWidth - 86, 18);
		add(new Button(right + columnWidth - 80, ry, 80, 18, () -> "Create", Button.Style.PRIMARY, this::createProfile));
		ry += 24;
		ConfigManager config = VantageClient.get().config();
		List<String> profiles = config.profiles();
		for (String profile : profiles) {
			boolean active = profile.equals(config.activeProfile());
			add(new Button(right, ry, columnWidth - 60, 18, () -> profile + (active ? "  (active)" : ""), active ? Button.Style.PRIMARY : Button.Style.SECONDARY, () -> {
				config.switchProfile(profile);
				toast("Switched to profile " + profile + ".");
				rebuildWidgets();
			}));
			Button delete = add(new Button(right + columnWidth - 56, ry, 56, 18, () -> "Delete", Button.Style.DANGER, () -> {
				try {
					config.deleteProfile(profile);
					rebuildWidgets();
				} catch (IOException e) {
					toast(e.getMessage());
				}
			}));
			delete.enabled = !active;
			ry += 22;
			if (ry > height - 50) {
				break;
			}
		}
		add(new Button(width - 200, 16, 180, 20, () -> "Capes, hats & pets", Button.Style.PRIMARY,
			() -> minecraft.setScreen(new PlayerCosmeticsScreen(this))));
		add(new Button(width - 120, height - 28, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
	}

	private String guiScaleLabel() {
		int scale = minecraft.options.guiScale().get();
		return scale == 0 ? "Auto" : Integer.toString(scale);
	}

	private void cycleGuiScale() {
		int max = minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
		int next = (minecraft.options.guiScale().get() + 1) % (max + 1);
		minecraft.options.guiScale().set(next);
		minecraft.options.save();
		minecraft.resizeDisplay();
	}

	private void applyHudStyle(int style) {
		for (HudModule hud : VantageClient.get().modules().hudModules()) {
			switch (style) {
				case 0 -> {
					hud.background.set(false);
					hud.border.set(false);
					hud.textShadow.set(true);
				}
				case 1 -> {
					hud.background.set(true);
					hud.backgroundColor.set(0x8C0B0D12);
					hud.border.set(false);
					hud.textShadow.set(false);
					hud.cornerRadius.set(3d);
				}
				case 2 -> {
					hud.background.set(true);
					hud.backgroundColor.set(0x600B0D12);
					hud.border.set(true);
					hud.borderColor.set(Colors.withAlpha(Theme.accent(), 200));
					hud.textShadow.set(false);
					hud.cornerRadius.set(4d);
				}
				default -> {
					hud.background.set(true);
					hud.backgroundColor.set(0x40FFFFFF);
					hud.border.set(true);
					hud.borderColor.set(0x50FFFFFF);
					hud.textShadow.set(true);
					hud.cornerRadius.set(6d);
				}
			}
		}
	}

	private void createProfile() {
		String name = profileName.text().trim();
		if (name.isEmpty()) {
			toast("Enter a profile name first.");
			return;
		}
		VantageClient.get().config().createProfile(name);
		toast("Created profile " + ConfigManager.sanitize(name) + " from your current settings.");
		rebuildWidgets();
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, 14, "Cosmetics", "Client-side customisation · only you can see these changes");
		int columnWidth = Math.min(300, (width - 60) / 2);
		int right = 20 + columnWidth + 20;
		Text.draw(g, "THEME PRESETS", 20, 58, Theme.FAINT);
		Text.draw(g, "INTERFACE", 20, 124, Theme.FAINT);
		Text.draw(g, "MENU BACKGROUND (" + Backgrounds.images().size() + " images in folder)", right, 58, Theme.FAINT);
		Text.draw(g, "GUI SCALE", right, 98, Theme.FAINT);
		Text.draw(g, "HUD STYLE PRESETS", right, 138, Theme.FAINT);
		Text.draw(g, "PROFILES", right, 178, Theme.FAINT);
		// Theme preview strip
		Render2D.horizontalGradient(g, 20, 50, columnWidth, 3, Theme.accent(), Theme.secondary());
	}
}
