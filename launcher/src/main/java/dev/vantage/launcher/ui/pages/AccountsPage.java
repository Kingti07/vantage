package dev.vantage.launcher.ui.pages;

import dev.vantage.common.auth.MicrosoftAuth;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Overlay;
import dev.vantage.launcher.ui.SkinHead;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.concurrent.atomic.AtomicBoolean;

/** Microsoft account management: sign in (device code flow), switch active account, sign out. */
public final class AccountsPage implements Page {
	private final MainWindow window;
	private final VBox root = new VBox();
	private final VBox notice = new VBox();
	private final VBox list = new VBox(10);

	public AccountsPage(MainWindow window) {
		this.window = window;
		root.getStyleClass().add("page");
		Button add = Ui.button("Add Microsoft account", Icons.ADD, "primary");
		add.setOnAction(e -> signIn());
		HBox header = Ui.row(12, new VBox(4, Ui.label("Accounts", "h1"),
			Ui.label("Sign in with the Microsoft account that owns Minecraft: Java Edition. Your password never touches Vantage.", "muted")),
			Ui.spacer(), add);
		root.getChildren().addAll(header, notice, list);
	}

	@Override
	public Node view() {
		ScrollPane scroll = new ScrollPane(root);
		scroll.setFitToWidth(true);
		return scroll;
	}

	@Override
	public void onShow() {
		notice.getChildren().clear();
		if (window.context().settings.settings().effectiveClientId().isBlank()) {
			VBox card = new VBox(8);
			card.getStyleClass().add("card");
			Label text = Ui.label("Microsoft sign-in needs an Azure application (client) id that Microsoft has approved for the Minecraft API. "
				+ "Register an app in the Azure portal (personal Microsoft accounts, public client flows enabled), request Minecraft API "
				+ "access at aka.ms/mce-reviewappid, then paste its client id in Settings.", "muted");
			text.setWrapText(true);
			Button settings = Ui.button("Open settings", Icons.SETTINGS, "ghost");
			settings.setOnAction(e -> window.navigate("settings"));
			Button docs = Ui.button("Request API access", Icons.OPEN, "ghost");
			docs.setOnAction(e -> Ui.openUrl("https://aka.ms/mce-reviewappid"));
			card.getChildren().addAll(Ui.label("Sign-in is not configured", "h3", "warning-text"), text, Ui.row(10, settings, docs));
			notice.getChildren().add(card);
		}
		list.getChildren().clear();
		var accounts = window.context().accounts.accounts();
		String selected = window.context().accounts.selected().map(MinecraftAccount::uuid).orElse(null);
		if (accounts.isEmpty()) {
			VBox empty = new VBox(6, Ui.label("No accounts yet", "h3"), Ui.label("Add your Microsoft account to play online.", "muted"));
			empty.getStyleClass().add("card");
			list.getChildren().add(empty);
		}
		for (MinecraftAccount account : accounts) {
			boolean active = account.uuid().equals(selected);
			HBox row = new HBox(14);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("card");
			if (active) {
				row.getStyleClass().add("card-selected");
			}
			VBox text = new VBox(3, Ui.row(8, Ui.label(account.username(), "h3"), active ? MainWindow.badge("Active", "accent") : new Label()),
				Ui.label(account.dashedUuid(), "small", "mono", "faint"));
			Button use = Ui.button("Use", Icons.CHECK, "ghost");
			use.setDisable(active);
			use.setOnAction(e -> {
				try {
					window.context().accounts.select(account.uuid());
					window.refreshAccountChip();
					onShow();
				} catch (java.io.IOException ex) {
					Toasts.error(ex.getMessage());
				}
			});
			Button remove = Ui.iconButton(Icons.DELETE, "Sign out", "ghost");
			remove.setOnAction(e -> Overlay.confirm("Sign out " + account.username() + "?",
				"The saved session is removed from this computer.", "Sign out", true, () -> {
					try {
						window.context().accounts.remove(account.uuid());
						window.refreshAccountChip();
						onShow();
					} catch (java.io.IOException ex) {
						Toasts.error(ex.getMessage());
					}
				}));
			row.getChildren().addAll(SkinHead.create(account.skinUrl(), 48), text, Ui.spacer(), use, remove);
			list.getChildren().add(row);
		}
	}

	private void signIn() {
		String clientId = window.context().settings.settings().effectiveClientId();
		if (clientId.isBlank()) {
			Toasts.error("Set your Azure client id in Settings first.");
			window.navigate("settings");
			return;
		}
		MicrosoftAuth auth = new MicrosoftAuth(clientId);
		AtomicBoolean cancelled = new AtomicBoolean();

		VBox dialog = new VBox(16);
		dialog.getStyleClass().add("dialog");
		dialog.setAlignment(Pos.TOP_LEFT);
		Label status = Ui.label("Contacting Microsoft…", "muted");
		ProgressIndicator spinner = new ProgressIndicator();
		spinner.setPrefSize(22, 22);
		Button cancel = Ui.button("Cancel", null, "ghost");
		dialog.getChildren().addAll(Ui.label("Sign in with Microsoft", "h2"), Ui.row(10, spinner, status), Ui.row(10, Ui.spacer(), cancel));
		Runnable close = Overlay.show(dialog, 520);
		cancel.setOnAction(e -> {
			cancelled.set(true);
			close.run();
		});

		Ui.async(auth::requestDeviceCode, code -> {
			Label codeLabel = Ui.label(code.userCode(), "code-code");
			Button copyOpen = Ui.button("Copy code & open " + code.verificationUri().replace("https://", ""), Icons.OPEN, "primary");
			copyOpen.setOnAction(e -> {
				ClipboardContent content = new ClipboardContent();
				content.putString(code.userCode());
				Clipboard.getSystemClipboard().setContent(content);
				Ui.openUrl(code.verificationUri());
			});
			Label help = Ui.label("Open the page, enter this code and sign in with the Microsoft account that owns Minecraft.", "muted");
			help.setWrapText(true);
			VBox codeBox = new VBox(10, codeLabel, help, copyOpen);
			codeBox.getStyleClass().add("inset");
			dialog.getChildren().add(2, codeBox);
			status.setText("Waiting for you to finish signing in…");
			Ui.async(() -> auth.awaitDeviceLogin(code, cancelled::get), account -> {
				close.run();
				try {
					window.context().accounts.put(account);
					Toasts.success("Signed in as " + account.username() + ".");
				} catch (java.io.IOException ex) {
					Overlay.error("Signed in, but the account could not be saved", ex);
				}
				window.refreshAccountChip();
				onShow();
			}, err -> {
				if (!cancelled.get()) {
					close.run();
					Overlay.error("Sign-in failed", err);
				}
			});
		}, err -> {
			close.run();
			Overlay.error("Sign-in failed", err);
		});
	}
}
