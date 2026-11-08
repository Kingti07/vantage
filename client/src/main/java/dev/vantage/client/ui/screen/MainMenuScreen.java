package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.util.Anim;
import dev.vantage.client.util.Colors;
import dev.vantage.common.VantageInfo;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.util.ArrayList;
import java.util.List;

/**
 * The Vantage title screen. Singleplayer and Multiplayer open the vanilla world and server screens, so world
 * creation, loading, deletion and the server list (icons, ping, editing, joining) work exactly as in vanilla.
 */
public final class MainMenuScreen extends VScreen {
	private final List<Anim> slide = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private int logoY;
	private float logoScale;
	private int left;

	public MainMenuScreen() {
		super("Vantage", null);
	}

	@Override
	protected void build() {
		slide.clear();
		String[][] entries = {
			{"Singleplayer"}, {"Multiplayer"}, {"Mods"}, {"Settings"}, {"Cosmetics"}, {"Accounts"}, {"Exit Game"}
		};
		// Responsive layout: everything must fit from the smallest Minecraft window (240 GUI px high) upwards.
		boolean compact = height < 320;
		left = Math.max(16, width / 12);
		logoScale = compact ? 2.0f : 3.0f;
		int logoBlock = compact ? 34 : 50;
		int gap = compact ? 3 : 5;
		int footer = 22;
		int preferredButtons = entries.length * (22 + gap);
		int contentHeight = logoBlock + 12 + preferredButtons;
		logoY = Math.max(compact ? 10 : 20, (height - footer - contentHeight) / 2);
		int y = logoY + logoBlock + (compact ? 8 : 14);
		int buttonHeight = Math.max(14, Math.min(22, (height - footer - y) / entries.length - gap));
		int buttonWidth = compact ? 150 : 168;
		int x = left;
		for (int i = 0; i < entries.length; i++) {
			String label = entries[i][0];
			Runnable action = switch (label) {
				case "Singleplayer" -> () -> minecraft.setScreen(new SelectWorldScreen(this));
				case "Multiplayer" -> () -> minecraft.setScreen(new JoinMultiplayerScreen(this));
				case "Mods" -> () -> minecraft.setScreen(new ModulesScreen(this));
				case "Settings" -> () -> minecraft.setScreen(new SettingsScreen(this));
				case "Cosmetics" -> () -> minecraft.setScreen(new CosmeticsScreen(this));
				case "Accounts" -> () -> minecraft.setScreen(new AccountsScreen(this));
				default -> minecraft::stop;
			};
			Button button = add(new Button(x, y + i * (buttonHeight + gap), buttonWidth, buttonHeight, () -> label,
				label.equals("Exit Game") ? Button.Style.GHOST : Button.Style.MENU, action));
			button.tooltip = label;
			slide.add(new Anim(0, 10));
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Staggered slide-in of the menu buttons.
		long elapsed = System.currentTimeMillis() - openedAt;
		for (int i = 0; i < widgets.size() && i < slide.size(); i++) {
			float target = elapsed > i * 45L ? 1 : 0;
			float t = Theme.animations() ? slide.get(i).target(target).get() : 1;
			widgets.get(i).x = left - Math.round((1 - t) * 30);
		}
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int x = left;
		// Logo: wordmark with an accent underline.
		Text.drawScaled(g, "VANTAGE", x, logoY, logoScale, Theme.TEXT, true, Text.uiFace());
		int logoWidth = Math.round(Text.width("VANTAGE") * logoScale);
		int underline = logoY + Math.round(10 * logoScale);
		Render2D.horizontalGradient(g, x, underline, logoWidth, 2, Theme.accent(), Theme.secondary());
		Text.draw(g, "PVP CLIENT", x, underline + 6, Colors.withAlpha(Theme.TEXT, 170));

		// Footer
		String footerText = VantageInfo.NAME + " " + VantageInfo.VERSION + "  ·  Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Fabric";
		Text.draw(g, footerText, 8, height - 14, 0x99FFFFFF);
		String disclaimer = "Not an official Minecraft product.";
		if (Text.width(footerText) + Text.width(disclaimer) + 30 < width) {
			Text.draw(g, disclaimer, width - Text.width(disclaimer) - 8, height - 14, 0x66FFFFFF);
		}

		// Account card (top right)
		var user = minecraft.getUser();
		String name = user.getName();
		int cardW = Math.max(110, Text.width(name) + 46);
		int cardX = width - cardW - 12;
		int cardY = 12;
		boolean hovered = Render2D.inside(mouseX, mouseY, cardX, cardY, cardW, 28);
		Render2D.panel(g, cardX, cardY, cardW, 28, 8, hovered ? Theme.surfaceHover() : Colors.withAlpha(Theme.panel(), 200), Theme.border());
		var skin = minecraft.player != null ? minecraft.player.getSkin() : DefaultPlayerSkin.get(user.getProfileId());
		PlayerFaceRenderer.draw(g, skin, cardX + 6, cardY + 6, 16);
		Text.draw(g, name, cardX + 28, cardY + 6, Theme.TEXT);
		Text.draw(g, "Account", cardX + 28, cardY + 16, Theme.FAINT);
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		String name = minecraft.getUser().getName();
		int cardW = Math.max(110, Text.width(name) + 46);
		if (Render2D.inside(mx, my, width - cardW - 12, 12, cardW, 28)) {
			minecraft.setScreen(new AccountsScreen(this));
			return true;
		}
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		// The title screen has nowhere to go back to.
	}

	/** Whether the custom menu should replace the vanilla title screen. */
	public static boolean enabled() {
		return VantageClient.get().interfaceModule().customMenu.enabled();
	}
}
