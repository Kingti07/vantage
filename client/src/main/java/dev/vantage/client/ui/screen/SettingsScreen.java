package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.performance.PerformanceModules;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.client.util.Colors;
import dev.vantage.common.VantageInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Central settings hub. Vantage categories open the module manager filtered to that category; Minecraft
 * categories open the vanilla options screens. Everything saves automatically.
 */
public final class SettingsScreen extends VScreen {
	private record Tile(String title, String description, Runnable action) {
	}

	private final List<Tile> tiles = new ArrayList<>();

	public SettingsScreen(Screen parent) {
		super("Settings", parent);
	}

	@Override
	protected void build() {
		tiles.clear();
		tiles.add(new Tile("General", "Interface, theme, main menu and HUD visibility.", () -> minecraft.setScreen(new ModulesScreen(this, Category.INTERFACE))));
		tiles.add(new Tile("Video", "Minecraft graphics, render distance, GUI scale.", () -> minecraft.setScreen(new VideoSettingsScreen(this, minecraft, minecraft.options))));
		tiles.add(new Tile("Performance", "Dynamic FPS, culling, particles and the FPS preset.", () -> minecraft.setScreen(new ModulesScreen(this, Category.PERFORMANCE))));
		tiles.add(new Tile("PvP", "Crosshair, hit colour, damage indicators, animations.", () -> minecraft.setScreen(new ModulesScreen(this, Category.PVP))));
		tiles.add(new Tile("HUD", "Enable elements and open the HUD editor.", () -> minecraft.setScreen(new ModulesScreen(this, Category.HUD))));
		tiles.add(new Tile("Camera", "Zoom, freelook, perspective, FOV, toggle sprint.", () -> minecraft.setScreen(new ModulesScreen(this, Category.CAMERA))));
		tiles.add(new Tile("Visual", "Fullbright, sky, weather, time, motion blur…", () -> minecraft.setScreen(new ModulesScreen(this, Category.VISUAL))));
		tiles.add(new Tile("Keybinds", "Vantage keybinds with conflict detection.", () -> minecraft.setScreen(new KeybindsScreen(this))));
		tiles.add(new Tile("Minecraft controls", "Vanilla key bindings.", () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))));
		tiles.add(new Tile("Mods", "Browse Modrinth and manage installed mods.", () -> minecraft.setScreen(new ModBrowserScreen(this))));
		tiles.add(new Tile("Audio", "Volume and sound devices.", () -> minecraft.setScreen(new SoundOptionsScreen(this, minecraft.options))));
		tiles.add(new Tile("Launcher", "Instance folders and launcher information.", () -> minecraft.setScreen(new LauncherInfoScreen(this))));
		tiles.add(new Tile("Account", "Current account and switching.", () -> minecraft.setScreen(new AccountsScreen(this))));
		tiles.add(new Tile("Profiles & cosmetics", "Themes, backgrounds, fonts and profiles.", () -> minecraft.setScreen(new CosmeticsScreen(this))));
		tiles.add(new Tile("All Minecraft options", "The full vanilla options menu.", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));

		int columns = Math.max(2, Math.min(4, (width - 40) / 170));
		int tileWidth = (width - 46 - (columns - 1) * 8) / columns;
		int tileHeight = height < 320 ? 34 : 46;
		ScrollPanel grid = add(new ScrollPanel(20, 50, width - 40, height - 50 - 36));
		for (int i = 0; i < tiles.size(); i++) {
			Tile tile = tiles.get(i);
			int x = (i % columns) * (tileWidth + 8);
			int y = (i / columns) * (tileHeight + 8);
			grid.add(new Widget(x, y, tileWidth, tileHeight) {
				@Override
				public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
					boolean hovered = isHovered(mouseX, mouseY);
					Render2D.panel(g, x, y, width, height, 8, hovered ? Theme.surfaceHover() : Colors.withAlpha(Theme.panel(), 220),
						hovered ? Theme.accent() : Theme.border());
					Text.draw(g, tile.title(), x + 10, y + (height < 40 ? 6 : 9), Theme.TEXT);
					Text.draw(g, Text.ellipsize(tile.description(), width - 20), x + 10, y + (height < 40 ? 19 : 24), Theme.FAINT);
				}

				@Override
				public boolean mouseClicked(double mx, double my, int button) {
					if (button == 0 && isHovered(mx, my)) {
						tile.action().run();
						return true;
					}
					return false;
				}
			});
		}
		add(new Button(width - 120, height - 28, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
		add(new Button(20, height - 28, 150, 18, () -> "Apply FPS boost preset", Button.Style.GHOST, () -> {
			PerformanceModules.FpsBoost.applyPreset();
			toast("Applied the high-FPS video preset.");
		}));
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, 14, "Settings", "Every change is saved automatically · profile: " + VantageClient.get().config().activeProfile());
		String version = VantageInfo.NAME + " " + VantageInfo.VERSION;
		Text.draw(g, version, width / 2 - Text.width(version) / 2, height - 22, Theme.FAINT);
	}
}
