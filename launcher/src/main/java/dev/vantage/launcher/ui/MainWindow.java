package dev.vantage.launcher.ui;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.LauncherContext;
import dev.vantage.launcher.game.GameLauncher;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.settings.LauncherSettings;
import dev.vantage.launcher.ui.pages.AccountsPage;
import dev.vantage.launcher.ui.pages.ConsolePage;
import dev.vantage.launcher.ui.pages.HomePage;
import dev.vantage.launcher.ui.pages.InstallationsPage;
import dev.vantage.launcher.ui.pages.ModsPage;
import dev.vantage.launcher.ui.pages.Page;
import dev.vantage.launcher.ui.pages.SettingsPage;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Window shell: sidebar navigation, animated page container, task bar, overlays and toasts. Also owns the
 * "Play" pipeline so any page can start the game.
 */
public final class MainWindow {
	private static final Logger LOGGER = LoggerFactory.getLogger(MainWindow.class);

	private final LauncherContext context;
	private final Stage stage;
	private final StackPane root = new StackPane();
	private final StackPane content = new StackPane();
	private final TaskBar taskBar = new TaskBar();
	private final GameState game = new GameState();
	private final Map<String, Page> pages = new LinkedHashMap<>();
	private final Map<String, Button> navButtons = new LinkedHashMap<>();
	private final VBox accountChip = new VBox();
	private String current;

	public MainWindow(LauncherContext context, Stage stage) {
		this.context = context;
		this.stage = stage;
		applyAccent();

		pages.put("home", new HomePage(this));
		pages.put("installations", new InstallationsPage(this));
		pages.put("mods", new ModsPage(this));
		pages.put("accounts", new AccountsPage(this));
		pages.put("settings", new SettingsPage(this));
		pages.put("console", new ConsolePage(this));

		BorderPane shell = new BorderPane();
		shell.setLeft(sidebar());
		shell.setCenter(content);
		shell.setBottom(taskBar);
		content.getStyleClass().add("content");
		root.getChildren().add(shell);
		Overlay.install(root);
		Toasts.install(root);
		navigate("home");
	}

	public StackPane root() {
		return root;
	}

	public LauncherContext context() {
		return context;
	}

	public Stage stage() {
		return stage;
	}

	public TaskBar taskBar() {
		return taskBar;
	}

	public GameState game() {
		return game;
	}

	/** Called once the stage is visible. */
	public void onShown() {
		LauncherSettings settings = context.settings.settings();
		if (settings.checkUpdatesOnStart && !settings.updateRepository.isBlank()) {
			SettingsPage.checkForUpdates(this, false);
		}
	}

	public void applyAccent() {
		String accent = context.settings.settings().accentColor;
		if (accent == null || !accent.matches("#[0-9a-fA-F]{6}")) {
			accent = "#7C5CFF";
		}
		root.setStyle("-v-accent: " + accent + ";");
	}

	private VBox sidebar() {
		VBox sidebar = new VBox();
		sidebar.getStyleClass().add("sidebar");
		sidebar.setPrefWidth(224);
		sidebar.setMinWidth(224);

		Node logo = Icons.of(Icons.LOGO, 28);
		logo.setStyle("-fx-padding: 0;");
		logo.lookupAll(".icon").forEach(n -> n.setStyle("-fx-fill: -v-accent;"));
		VBox brandText = new VBox(0, Ui.label(VantageInfo.NAME.toUpperCase(), "brand"), Ui.label("PVP CLIENT · " + VantageInfo.VERSION, "brand-sub"));
		HBox brand = new HBox(10, logo, brandText);
		brand.setAlignment(Pos.CENTER_LEFT);
		brand.setStyle("-fx-padding: 0 6 22 6;");
		sidebar.getChildren().add(brand);

		addNav(sidebar, "home", "Home", Icons.HOME);
		addNav(sidebar, "installations", "Installations", Icons.LAYERS);
		addNav(sidebar, "mods", "Mods", Icons.MODS);
		addNav(sidebar, "accounts", "Accounts", Icons.PERSON);
		addNav(sidebar, "settings", "Settings", Icons.SETTINGS);
		addNav(sidebar, "console", "Console", Icons.TERMINAL);
		sidebar.getChildren().add(Ui.spacer());

		accountChip.getStyleClass().add("account-chip");
		accountChip.setOnMouseClicked(e -> navigate("accounts"));
		refreshAccountChip();
		sidebar.getChildren().add(accountChip);
		return sidebar;
	}

	private void addNav(VBox sidebar, String id, String text, String icon) {
		Button button = new Button(text);
		button.setGraphic(Icons.of(icon, 18));
		button.getStyleClass().setAll("nav-button");
		button.setMaxWidth(Double.MAX_VALUE);
		button.setOnAction(e -> navigate(id));
		navButtons.put(id, button);
		sidebar.getChildren().add(button);
	}

	public void refreshAccountChip() {
		accountChip.getChildren().clear();
		MinecraftAccount account = context.accounts.selected().orElse(null);
		HBox row;
		if (account == null) {
			row = new HBox(10, Icons.of(Icons.PERSON, 26), new VBox(0, Ui.label("No account", "h3"), Ui.label("Click to sign in", "small", "muted")));
		} else {
			row = new HBox(10, SkinHead.create(account.skinUrl(), 32),
				new VBox(0, Ui.label(account.username(), "h3"), Ui.label("Microsoft account", "small", "muted")));
		}
		row.setAlignment(Pos.CENTER_LEFT);
		accountChip.getChildren().add(row);
	}

	public void navigate(String id) {
		Page page = pages.get(id);
		if (page == null || id.equals(current)) {
			return;
		}
		current = id;
		navButtons.forEach((key, button) -> {
			button.getStyleClass().remove("active");
			if (key.equals(id)) {
				button.getStyleClass().add("active");
			}
		});
		Node view = page.view();
		content.getChildren().setAll(view);
		page.onShow();
		if (context.settings.settings().animations) {
			FadeTransition fade = new FadeTransition(Duration.millis(180), view);
			fade.setFromValue(0);
			fade.setToValue(1);
			TranslateTransition slide = new TranslateTransition(Duration.millis(180), view);
			slide.setFromY(10);
			slide.setToY(0);
			new ParallelTransition(fade, slide).play();
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Page> T page(String id) {
		return (T) pages.get(id);
	}

	// ------------------------------------------------------------------------------------------------ play

	public void play(Instance instance, GameLauncher.QuickPlay quickPlay) {
		if (game.phase() != GameState.Phase.IDLE) {
			Toasts.info("Minecraft is already " + (game.phase() == GameState.Phase.RUNNING ? "running." : "starting."));
			return;
		}
		if (context.accounts.selected().isEmpty()) {
			Toasts.error("Add a Microsoft account before playing.");
			navigate("accounts");
			return;
		}
		context.settings.select(instance);
		game.preparing();
		game.clear();
		game.append("[Vantage] Preparing " + instance.name + " (Minecraft " + instance.minecraftVersion + ")");
		ProgressListener progress = taskBar.begin("Preparing " + instance.name);
		LauncherSettings settings = context.settings.settings();
		Ui.async(() -> context.launchService.launch(instance, settings, context.accounts, quickPlay, progress, game::append),
			session -> {
				taskBar.finish();
				game.started(session);
				instance.lastPlayed = System.currentTimeMillis();
				context.settings.updateInstance(instance);
				refreshAccountChip();
				if (settings.consoleOnLaunch) {
					navigate("console");
				}
				switch (settings.afterLaunch) {
					case MINIMIZE -> stage.setIconified(true);
					case CLOSE -> stage.hide();
					case KEEP_OPEN -> {
					}
				}
				Thread waiter = new Thread(() -> {
					int code;
					try {
						code = session.process().waitFor();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
					game.append("[Vantage] Minecraft exited with code " + code);
					Platform.runLater(() -> onGameExit(code));
				}, "vantage-game-waiter");
				waiter.setDaemon(true);
				waiter.start();
			},
			error -> {
				taskBar.finish();
				game.idle();
				game.append("[Vantage] Launch failed: " + Ui.message(error));
				LOGGER.error("Launch failed", error);
				Overlay.message("Could not start Minecraft", Ui.message(error));
			});
	}

	private void onGameExit(int code) {
		Instance lastInstance = context.settings.selectedInstance();
		game.exited(code);
		// The in-game Accounts screen asks for a restart with another account by leaving this marker behind.
		java.nio.file.Path relaunch = context.paths.root().resolve("relaunch-request.json");
		if (java.nio.file.Files.exists(relaunch)) {
			try {
				java.nio.file.Files.delete(relaunch);
				context.accounts.load();
				refreshAccountChip();
				game.append("[Vantage] Restarting with account " + context.accounts.selected().map(MinecraftAccount::username).orElse("?"));
				play(lastInstance, GameLauncher.QuickPlay.NONE);
				return;
			} catch (java.io.IOException e) {
				LOGGER.warn("Could not process relaunch request", e);
			}
		}
		LauncherSettings settings = context.settings.settings();
		if (settings.afterLaunch == LauncherSettings.AfterLaunch.CLOSE) {
			Platform.exit();
			return;
		}
		if (!stage.isShowing()) {
			stage.show();
		}
		stage.setIconified(false);
		if (code != 0) {
			Toasts.error("Minecraft stopped unexpectedly (exit code " + code + "). See Console or crash reports.");
		}
	}

	public static Label badge(String text, String kind) {
		Label label = new Label(text);
		label.getStyleClass().add("badge");
		label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		if (kind != null) {
			label.getStyleClass().add(kind);
		}
		return label;
	}
}
