package dev.vantage.launcher.ui;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.launcher.LauncherContext;
import dev.vantage.launcher.game.GameLauncher;
import dev.vantage.launcher.game.LaunchService;
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
		String lastPage = context.settings.settings().lastPage;
		navigate(pages.containsKey(lastPage) ? lastPage : "home");
	}

	/** Starts the selected installation (Ctrl+Enter). */
	public void playSelected() {
		play(context.settings.selectedInstance(), GameLauncher.QuickPlay.NONE);
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
		if (!context.startupWarnings.isEmpty()) {
			Overlay.message("Some saved data had to be repaired", String.join("\n\n", context.startupWarnings));
		}
		dev.vantage.launcher.Main.failedUpdate().ifPresent(version -> Overlay.message("Update could not be started",
			"Vantage " + version + " did not start correctly, so you are still using version " + VantageInfo.VERSION
				+ ". This update will not be offered again. Please report the problem on GitHub."));
		if (settings.checkUpdatesOnStart) {
			UpdatePrompt.check(this, false);
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
		if (!id.equals(context.settings.settings().lastPage)) {
			context.settings.settings().lastPage = id;
			context.settings.save();
		}
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
		// The in-game account switcher may have changed accounts.json since we last read it.
		context.accounts.reload();
		if (context.accounts.selected().isEmpty()) {
			Toasts.error("Add a Microsoft account before playing.");
			navigate("accounts");
			return;
		}
		context.settings.select(instance);
		game.preparing();
		game.clear();
		game.append("[Vantage] Preparing " + instance.name + " (Minecraft " + instance.minecraftVersion + ")");
		var progress = taskBar.begin("Preparing " + instance.name, true);
		LauncherSettings settings = context.settings.settings();
		Ui.async(() -> context.launchService.launch(instance, settings, context.accounts, quickPlay, progress, game::append),
			session -> {
				progress.finish();
				game.started(session);
				if (session.offline()) {
					Toasts.info("You are offline: Minecraft starts with your saved session. Singleplayer works; online servers need a connection.");
				}
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
				LaunchService.whenExited(session, code -> {
					game.append("[Vantage] Minecraft exited with code " + code);
					Platform.runLater(() -> onGameExit(code));
				});
			},
			error -> {
				progress.finish();
				game.idle();
				if (error instanceof dev.vantage.common.util.CancelledException) {
					game.append("[Vantage] Launch cancelled.");
					Toasts.info("Launch cancelled. Downloaded files are kept for next time.");
					return;
				}
				game.append("[Vantage] Launch failed: " + Ui.message(error));
				LOGGER.error("Launch failed", error);
				Overlay.error("Could not start Minecraft", error);
			});
	}

	private void onGameExit(int code) {
		Instance lastInstance = context.settings.selectedInstance();
		game.exited(code);
		if (context.launchService.takeRelaunchRequest()) {
			context.accounts.reload();
			refreshAccountChip();
			game.append("[Vantage] Restarting with account " + context.accounts.selected().map(MinecraftAccount::username).orElse("?"));
			play(lastInstance, GameLauncher.QuickPlay.NONE);
			return;
		}
		LauncherSettings settings = context.settings.settings();
		GameState.LastExit exit = game.lastExitProperty().get();
		boolean crashed = code != 0 && (exit == null || !exit.stoppedByUser());
		// "Close after launch" still comes back after a crash, otherwise the player would never learn why.
		if (settings.afterLaunch == LauncherSettings.AfterLaunch.CLOSE && !crashed) {
			Platform.exit();
			return;
		}
		if (!stage.isShowing()) {
			stage.show();
		}
		stage.setIconified(false);
		if (crashed && exit != null) {
			showCrash(exit);
		}
	}

	/** Explains a crash with the start of the crash report, or the last errors from the console if there is none. */
	private void showCrash(GameState.LastExit exit) {
		java.util.Optional<java.nio.file.Path> report = dev.vantage.launcher.game.CrashReports.newestSince(exit.gameDir(), exit.startedAt());
		if (report.isPresent()) {
			try {
				String excerpt = dev.vantage.launcher.game.CrashReports.excerpt(report.get(), 60);
				Overlay.report("Minecraft crashed", "Minecraft wrote a crash report. The start of it usually names the mod or "
					+ "setting that caused the crash. Share it when asking for help.", excerpt, report.get());
				return;
			} catch (java.io.IOException e) {
				LOGGER.warn("Could not read crash report {}", report.get(), e);
			}
		}
		java.util.List<String> problems = game.lines().stream()
			.filter(l -> l.level() == dev.vantage.launcher.game.GameLogParser.Level.ERROR)
			.map(dev.vantage.launcher.game.GameLogParser.LogLine::format)
			.toList();
		String details = problems.isEmpty() ? "The game printed no errors. Open the Console for its full output."
			: String.join("\n", problems.subList(Math.max(0, problems.size() - 40), problems.size()));
		Overlay.report("Minecraft stopped unexpectedly", "The game ended with exit code " + exit.exitCode()
			+ " and left no crash report. These are its last error messages:", details, null);
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
