package dev.vantage.launcher.ui.pages;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.launcher.game.GameLauncher;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.ui.GameState;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Landing page: greeting, installation picker, Play button, quick join and last-session status. */
public final class HomePage implements Page {
	private final MainWindow window;
	private final VBox root = new VBox();
	private final Label greeting = Ui.label("", "h1");
	private final Label subtitle = Ui.label("", "muted");
	private final ComboBox<Instance> instancePicker = new ComboBox<>();
	private final HBox details = new HBox(8);
	private final Button play = Ui.button("PLAY", Icons.PLAY, "primary", "play-button");
	private final Button stop = Ui.button("Stop", Icons.STOP, "danger");
	private final TextField quickJoin = new TextField();
	private final VBox lastSession = new VBox(8);

	public HomePage(MainWindow window) {
		this.window = window;
		root.getStyleClass().add("page");

		// Hero
		VBox hero = new VBox(14);
		hero.getStyleClass().add("hero");
		Label tag = MainWindow.badge(VantageInfo.NAME.toUpperCase() + " " + VantageInfo.VERSION, "accent");
		instancePicker.setPrefWidth(280);
		instancePicker.setOnAction(e -> {
			Instance selected = instancePicker.getValue();
			if (selected != null) {
				window.context().settings.select(selected);
				refreshDetails();
			}
		});
		play.setOnAction(e -> {
			Instance instance = instancePicker.getValue();
			if (instance != null) {
				window.play(instance, GameLauncher.QuickPlay.NONE);
			}
		});
		stop.setOnAction(e -> window.game().kill());
		stop.setVisible(false);
		stop.setManaged(false);
		HBox actions = new HBox(14, play, stop, new VBox(6, Ui.label("Installation", "small", "muted"), instancePicker));
		actions.setAlignment(Pos.BOTTOM_LEFT);
		hero.getChildren().addAll(tag, greeting, subtitle, details, actions);

		// Quick join
		quickJoin.setPromptText("play.example.net");
		HBox.setHgrow(quickJoin, Priority.ALWAYS);
		Button join = Ui.button("Join server", Icons.SERVER, "ghost");
		join.setOnAction(e -> quickJoin());
		quickJoin.setOnAction(e -> quickJoin());
		VBox quickCard = new VBox(10, Ui.describe("Quick join", "Launch and connect straight to a server using Minecraft's Quick Play."),
			Ui.row(10, quickJoin, join));
		quickCard.getStyleClass().add("card");

		// Shortcuts
		VBox perfCard = shortcut(Icons.BOLT, "Performance pack", "Install Sodium, Lithium, ImmediatelyFast and more from Modrinth in one click.",
			"Open", () -> {
				window.navigate("mods");
				window.<ModsPage>page("mods").showPerformance();
			});
		VBox modsCard = shortcut(Icons.MODS, "Browse mods", "Search Modrinth and install mods with automatic dependency handling.",
			"Browse", () -> {
				window.navigate("mods");
				window.<ModsPage>page("mods").showBrowse();
			});
		lastSession.getStyleClass().add("card");
		HBox cards = new HBox(16, perfCard, modsCard, lastSession);
		cards.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
		perfCard.setMaxWidth(Double.MAX_VALUE);
		modsCard.setMaxWidth(Double.MAX_VALUE);
		lastSession.setMaxWidth(Double.MAX_VALUE);

		root.getChildren().addAll(hero, quickCard, cards);

		window.game().phaseProperty().addListener((obs, old, phase) -> updatePlayState(phase));
		window.game().lastExitProperty().addListener((obs, old, exit) -> refreshLastSession());
		refreshLastSession();
	}

	private VBox shortcut(String icon, String title, String text, String action, Runnable onClick) {
		Button button = Ui.button(action, null, "ghost");
		button.setOnAction(e -> onClick.run());
		Node iconNode = Icons.of(icon, 22);
		iconNode.lookupAll(".icon").forEach(n -> n.setStyle("-fx-fill: -v-accent;"));
		Label body = Ui.label(text, "muted");
		body.setWrapText(true);
		VBox card = new VBox(10, iconNode, Ui.label(title, "h3"), body, Ui.spacer(), button);
		card.getStyleClass().add("card");
		card.setPrefWidth(260);
		return card;
	}

	private void quickJoin() {
		String address = quickJoin.getText().trim();
		if (!address.matches("[A-Za-z0-9.\\-_]+(:\\d{1,5})?")) {
			Toasts.error("Enter a server address like play.example.net or 1.2.3.4:25565.");
			return;
		}
		Instance instance = instancePicker.getValue();
		if (instance != null) {
			window.play(instance, new GameLauncher.QuickPlay(address, null));
		}
	}

	private void updatePlayState(GameState.Phase phase) {
		switch (phase) {
			case IDLE -> {
				play.setText("PLAY");
				play.setDisable(false);
				stop.setVisible(false);
				stop.setManaged(false);
			}
			case PREPARING -> {
				play.setText("PREPARING…");
				play.setDisable(true);
			}
			case RUNNING -> {
				play.setText("RUNNING");
				play.setDisable(true);
				stop.setVisible(true);
				stop.setManaged(true);
			}
		}
	}

	private void refreshLastSession() {
		lastSession.getChildren().clear();
		var exit = window.game().lastExitProperty().get();
		Node icon = Icons.of(exit == null || exit.exitCode() == 0 ? Icons.CHECK : Icons.WARNING, 22);
		if (exit == null) {
			lastSession.getChildren().addAll(icon, Ui.label("Last session", "h3"),
				wrap(Ui.label("No game has been started from this launcher session yet.", "muted")));
			return;
		}
		boolean crashed = exit.exitCode() != 0;
		icon.lookupAll(".icon").forEach(n -> n.setStyle(crashed ? "-fx-fill: -v-danger;" : "-fx-fill: -v-success;"));
		lastSession.getChildren().addAll(icon, Ui.label("Last session", "h3"),
			wrap(Ui.label(exit.instanceName() + (crashed ? " stopped with exit code " + exit.exitCode() + "." : " closed normally."), "muted")));
		if (crashed) {
			Path crashes = exit.gameDir().resolve("crash-reports");
			Button open = Ui.button(Files.isDirectory(crashes) ? "Crash reports" : "Open console", Icons.FOLDER, "ghost");
			open.setOnAction(e -> {
				if (Files.isDirectory(crashes)) {
					Ui.openFolder(crashes);
				} else {
					window.navigate("console");
				}
			});
			lastSession.getChildren().addAll(Ui.spacer(), open);
		}
	}

	private static Label wrap(Label label) {
		label.setWrapText(true);
		return label;
	}

	private void refreshDetails() {
		details.getChildren().clear();
		Instance instance = instancePicker.getValue();
		if (instance == null) {
			return;
		}
		details.getChildren().add(MainWindow.badge("Minecraft " + instance.minecraftVersion, null));
		details.getChildren().add(MainWindow.badge("Fabric " + (instance.loaderVersion.isBlank() ? "latest" : instance.loaderVersion), null));
		if (instance.vantageClient && instance.supportsVantageClient()) {
			details.getChildren().add(MainWindow.badge("Vantage client", "accent"));
		} else if (!instance.supportsVantageClient()) {
			details.getChildren().add(MainWindow.badge("Vanilla Fabric (client needs " + VantageInfo.CLIENT_MINECRAFT_VERSION + ")", "warning"));
		}
		details.getChildren().add(MainWindow.badge(instance.memoryMb / 1024.0 + " GB RAM", null));
		if (instance.lastPlayed > 0) {
			details.getChildren().add(MainWindow.badge("Played " + Ui.relativeTime(Instant.ofEpochMilli(instance.lastPlayed)), null));
		}
	}

	@Override
	public Node view() {
		ScrollPane scroll = new ScrollPane(root);
		scroll.setFitToWidth(true);
		return scroll;
	}

	@Override
	public void onShow() {
		var settings = window.context().settings;
		instancePicker.setItems(FXCollections.observableArrayList(settings.instances()));
		instancePicker.setValue(settings.selectedInstance());
		MinecraftAccount account = window.context().accounts.selected().orElse(null);
		greeting.setText(account == null ? "Welcome to " + VantageInfo.NAME : "Welcome back, " + account.username());
		subtitle.setText(account == null
			? "Sign in with your Microsoft account to start playing."
			: "Your PvP setup is ready. Pick an installation and jump in.");
		refreshDetails();
		updatePlayState(window.game().phase());
	}
}
