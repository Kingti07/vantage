package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.AccountStore;
import dev.vantage.common.auth.MinecraftAccount;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Shows the signed-in account and the accounts saved in the Vantage launcher. Switching selects the account in
 * the launcher and restarts the game through it: Minecraft only supports changing the session at start-up, so
 * this is the reliable, officially authenticated way to switch.
 */
public final class AccountsScreen extends VScreen {
	public static final String RELAUNCH_FILE = "relaunch-request.json";
	private List<MinecraftAccount> accounts = List.of();
	private String error;

	public AccountsScreen(Screen parent) {
		super("Accounts", parent);
	}

	private static Path launcherDir() {
		String dir = System.getProperty(VantageInfo.PROP_LAUNCHER_DIR);
		return dir == null || dir.isBlank() ? null : Path.of(dir);
	}

	@Override
	protected void build() {
		Path dir = launcherDir();
		error = null;
		accounts = List.of();
		if (dir != null) {
			AccountStore store = new AccountStore(dir);
			try {
				store.load();
				accounts = store.accounts();
			} catch (IOException e) {
				error = "Could not read launcher accounts: " + e.getMessage();
			}
		}
		int y = 120;
		String current = minecraft.getUser().getProfileId().toString().replace("-", "");
		for (MinecraftAccount account : accounts) {
			boolean active = account.uuid().equalsIgnoreCase(current);
			Button button = add(new Button(width / 2 + 60, y + 6, 120, 18, () -> active ? "Current account" : "Switch & restart",
				active ? Button.Style.GHOST : Button.Style.PRIMARY, () -> switchTo(account)));
			button.enabled = !active && minecraft.level == null;
			if (!active && minecraft.level != null) {
				button.tooltip = "Leave the world first.";
			}
			y += 34;
		}
		add(new Button(width / 2 - 50, height - 30, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
	}

	private void switchTo(MinecraftAccount account) {
		Path dir = launcherDir();
		if (dir == null) {
			return;
		}
		try {
			AccountStore store = new AccountStore(dir);
			store.load();
			store.select(account.uuid());
			Files.writeString(dir.resolve(RELAUNCH_FILE), "{\"account\":\"" + account.uuid() + "\"}", StandardCharsets.UTF_8);
			VantageClient.get().config().save();
			minecraft.stop();
		} catch (IOException e) {
			toast("Could not switch: " + e.getMessage());
		}
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int cx = width / 2;
		header(g, cx - 180, 16, "Accounts", "Microsoft accounts are added and removed in the Vantage launcher.");
		var user = minecraft.getUser();
		Render2D.panel(g, cx - 180, 52, 360, 48, 10, Theme.panel(), Theme.accent());
		var skin = minecraft.player != null ? minecraft.player.getSkin() : DefaultPlayerSkin.get(user.getProfileId());
		PlayerFaceRenderer.draw(g, skin, cx - 168, 60, 32);
		Text.draw(g, user.getName(), cx - 126, 62, Theme.TEXT);
		Text.draw(g, user.getProfileId().toString(), cx - 126, 74, Theme.FAINT);
		Text.draw(g, "Signed in with Microsoft", cx - 126, 86, Theme.SUCCESS);

		if (launcherDir() == null) {
			Text.drawCentered(g, "Started without the Vantage launcher – account switching is unavailable.", cx, 124, Theme.MUTED);
			return;
		}
		if (error != null) {
			Text.drawCentered(g, error, cx, 124, Theme.DANGER);
		}
		int y = 120;
		for (MinecraftAccount account : accounts) {
			Render2D.panel(g, cx - 180, y, 360, 30, 8, Theme.surface(), Theme.border());
			try {
				PlayerFaceRenderer.draw(g, DefaultPlayerSkin.get(UUID.fromString(account.dashedUuid())), cx - 172, y + 7, 16);
			} catch (IllegalArgumentException ignored) {
				// malformed uuid: no face
			}
			Text.draw(g, account.username(), cx - 150, y + 6, Theme.TEXT);
			Text.draw(g, account.dashedUuid(), cx - 150, y + 17, Theme.FAINT);
			y += 34;
		}
		if (accounts.isEmpty() && error == null) {
			Text.drawCentered(g, "No saved accounts. Add one on the launcher's Accounts page.", cx, 124, Theme.MUTED);
		}
	}
}
