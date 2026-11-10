package dev.vantage.launcher.ui.pages;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.OperatingSystem;
import dev.vantage.launcher.game.JavaRuntimeManager;
import dev.vantage.launcher.settings.LauncherSettings;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Overlay;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.ToggleSwitch;
import dev.vantage.launcher.ui.Ui;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;

/** Launcher preferences. Every control saves immediately. */
public final class SettingsPage implements Page {
	private static final List<String> SWATCHES = List.of("#7C5CFF", "#4F8CFF", "#22C3A6", "#3DDC97", "#FFB547", "#FF7A45", "#FF5C7A", "#E86BFF");

	private final MainWindow window;
	private final VBox root = new VBox();
	private final VBox runtimes = new VBox(8);

	public SettingsPage(MainWindow window) {
		this.window = window;
		root.getStyleClass().add("page");
		root.getChildren().add(new VBox(4, Ui.label("Settings", "h1"), Ui.label("Changes are saved automatically.", "muted")));
		root.getChildren().addAll(appearance(), launching(), signIn(), java(), updates(), storage(), about());
	}

	private LauncherSettings settings() {
		return window.context().settings.settings();
	}

	private void save() {
		window.context().settings.save();
	}

	private VBox section(String title, Node... rows) {
		VBox card = new VBox(14);
		card.getStyleClass().add("card");
		card.getChildren().add(Ui.label(title, "h2"));
		card.getChildren().addAll(rows);
		return card;
	}

	private HBox setting(String title, String description, Node control) {
		VBox text = Ui.describe(title, description);
		HBox.setHgrow(text, Priority.ALWAYS);
		HBox row = new HBox(20, text, control);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private Node appearance() {
		HBox swatches = new HBox(8);
		swatches.setAlignment(Pos.CENTER_RIGHT);
		ColorPicker custom = new ColorPicker(Color.web(settings().accentColor));
		custom.setPrefWidth(48);
		custom.setStyle("-fx-color-label-visible: false;");
		custom.setOnAction(e -> setAccent(toHex(custom.getValue())));
		for (String color : SWATCHES) {
			Region swatch = new Region();
			swatch.setPrefSize(24, 24);
			swatch.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 12; -fx-cursor: hand;");
			swatch.setOnMouseClicked(e -> {
				setAccent(color);
				custom.setValue(Color.web(color));
			});
			swatches.getChildren().add(swatch);
		}
		swatches.getChildren().add(custom);
		ToggleSwitch animations = new ToggleSwitch(settings().animations);
		animations.selectedProperty().addListener((o, a, b) -> {
			settings().animations = b;
			save();
		});
		return section("Appearance",
			setting("Accent colour", "Used for buttons, highlights and progress across the launcher.", swatches),
			setting("Animations", "Page transitions and dialog animations.", animations));
	}

	private void setAccent(String hex) {
		settings().accentColor = hex;
		save();
		window.applyAccent();
	}

	private static String toHex(Color color) {
		return String.format(Locale.ROOT, "#%02X%02X%02X", (int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255),
			(int) Math.round(color.getBlue() * 255));
	}

	private Node launching() {
		ComboBox<LauncherSettings.AfterLaunch> after = new ComboBox<>(FXCollections.observableArrayList(LauncherSettings.AfterLaunch.values()));
		after.setValue(settings().afterLaunch);
		after.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(LauncherSettings.AfterLaunch value) {
				return value == null ? "" : switch (value) {
					case KEEP_OPEN -> "Keep launcher open";
					case MINIMIZE -> "Minimize launcher";
					case CLOSE -> "Close launcher";
				};
			}

			@Override
			public LauncherSettings.AfterLaunch fromString(String string) {
				return null;
			}
		});
		after.setOnAction(e -> {
			settings().afterLaunch = after.getValue();
			save();
		});
		ToggleSwitch console = new ToggleSwitch(settings().consoleOnLaunch);
		console.selectedProperty().addListener((o, a, b) -> {
			settings().consoleOnLaunch = b;
			save();
		});
		Slider threads = new Slider(2, 16, settings().downloadThreads);
		threads.setPrefWidth(200);
		Label threadLabel = Ui.label(settings().downloadThreads + " parallel", "muted");
		threads.valueProperty().addListener((o, a, b) -> {
			settings().downloadThreads = b.intValue();
			threadLabel.setText(b.intValue() + " parallel");
			save();
		});
		return section("Launching",
			setting("When the game starts", "What the launcher does after Minecraft has started.", after),
			setting("Show console on launch", "Switch to the Console page to watch the game log.", console),
			setting("Download threads", "Parallel downloads for game files. Lower this on slow connections.", Ui.row(10, threads, threadLabel)));
	}

	private Node signIn() {
		TextField clientId = new TextField(settings().msaClientId);
		clientId.setPromptText("Built-in: " + dev.vantage.common.VantageInfo.DEFAULT_MSA_CLIENT_ID);
		clientId.setPrefWidth(340);
		clientId.textProperty().addListener((o, a, b) -> {
			settings().msaClientId = b.trim();
			save();
		});
		Button docs = Ui.button("Request Minecraft API access", Icons.OPEN, "ghost");
		docs.setOnAction(e -> Ui.openUrl("https://aka.ms/mce-reviewappid"));
		Label help = Ui.label("Microsoft only lets approved Azure applications sign players in to Minecraft. Register an application "
			+ "(supported account types: personal Microsoft accounts; enable \"Allow public client flows\"), request access for it, and paste "
			+ "its Application (client) ID here. It can also be provided with -Dvantage.msaClientId when building a distribution.", "muted");
		help.setWrapText(true);
		return section("Microsoft sign-in", setting("Azure application (client) ID", "Leave empty to use the id built into this launcher.", clientId), help, docs);
	}

	private Node java() {
		Label platform = Ui.label("Runtimes are downloaded from Mojang for platform " + JavaRuntimeManager.platformKey()
			+ " in the version each Minecraft release requires. A custom Java executable can be set per installation.", "muted");
		platform.setWrapText(true);
		return section("Java runtimes", platform, runtimes);
	}

	private void refreshRuntimes() {
		runtimes.getChildren().setAll(Ui.label("Loading…", "small", "muted"));
		Ui.async(() -> window.context().runtimes.installed(), list -> {
			runtimes.getChildren().clear();
			if (list.isEmpty()) {
				runtimes.getChildren().add(Ui.label("No runtimes downloaded yet – the right one is installed on first launch.", "small", "faint"));
			}
			for (JavaRuntimeManager.InstalledRuntime runtime : list) {
				Button delete = Ui.iconButton(Icons.DELETE, "Delete runtime", "ghost");
				delete.setOnAction(e -> Overlay.confirm("Delete Java " + runtime.version() + "?",
					"It will be downloaded again when an installation needs it.", "Delete", true, () -> Ui.async(() -> {
						window.context().runtimes.delete(runtime);
						return null;
					}, ok -> refreshRuntimes(), err -> Toasts.error(Ui.message(err)))));
				HBox row = Ui.row(10, Icons.of(Icons.TERMINAL, 18), new VBox(2, Ui.label("Java " + runtime.version(), "h3"),
					Ui.label(runtime.component() + " · " + runtime.home(), "small", "faint")), Ui.spacer(), delete);
				row.getStyleClass().add("inset");
				runtimes.getChildren().add(row);
			}
		}, err -> runtimes.getChildren().setAll(Ui.label(Ui.message(err), "danger-text")));
	}

	private Node updates() {
		TextField repository = new TextField(settings().updateRepository);
		repository.setPromptText("Built-in: " + dev.vantage.common.VantageInfo.DEFAULT_UPDATE_REPOSITORY);
		repository.setPrefWidth(260);
		repository.textProperty().addListener((o, a, b) -> {
			settings().updateRepository = b.trim();
			save();
		});
		ToggleSwitch auto = new ToggleSwitch(settings().checkUpdatesOnStart);
		auto.selectedProperty().addListener((o, a, b) -> {
			settings().checkUpdatesOnStart = b;
			save();
		});
		Button check = Ui.button("Check now", Icons.REFRESH, "ghost");
		check.setOnAction(e -> checkForUpdates(window, true));
		return section("Updates",
			setting("Release channel", "GitHub repository whose releases publish Vantage builds. Leave empty for the official "
				+ "repository; only enter another one if you trust its owner. Every update is checked against its published SHA-256 checksum.", repository),
			setting("Check on start", "Look for a new version every time the launcher opens.", auto),
			Ui.row(10, Ui.label("Current version " + VantageInfo.VERSION, "muted"), Ui.spacer(), check));
	}

	/** Checks the release channel and offers to download a newer launcher (which also contains the newer client). */
	public static void checkForUpdates(MainWindow window, boolean userInitiated) {
		LauncherSettings settings = window.context().settings.settings();
		String platform = platformClassifier();
		Ui.async(() -> window.context().updater.check(settings.effectiveUpdateRepository(), platform), release -> {
			if (release.isEmpty()) {
				if (userInitiated) {
					Toasts.success("You are on the latest version (" + VantageInfo.VERSION + ").");
				}
				return;
			}
			var r = release.get();
			String repository = settings.effectiveUpdateRepository();
			String notes = r.notes().isBlank() ? r.name() : r.notes();
			if (!dev.vantage.launcher.update.SelfUpdater.isOfficialRepository(repository)) {
				notes = "Warning: this update comes from \"" + repository + "\", not from the official Vantage repository. "
					+ "Only install it if you trust its owner.\n\n" + notes;
			}
			Overlay.confirm("Vantage " + r.version() + " is available", notes, "Download update", false, () -> {
				var progress = window.taskBar().begin("Downloading update", true);
				Ui.async(() -> window.context().updater.download(r, platform, progress), path -> {
					progress.finish();
					Overlay.message("Update ready", "Vantage " + r.version() + " has been downloaded. It starts automatically the next time you open the launcher.");
				}, err -> {
					progress.finish();
					Toasts.error("Update download failed: " + Ui.message(err));
				});
			});
		}, err -> {
			if (userInitiated) {
				Toasts.error(Ui.message(err));
			}
		});
	}

	private static String platformClassifier() {
		boolean arm = OperatingSystem.isArm64();
		return switch (OperatingSystem.current()) {
			case WINDOWS -> "win";
			case MACOS -> arm ? "mac-aarch64" : "mac";
			case LINUX -> arm ? "linux-aarch64" : "linux";
		};
	}

	private Node storage() {
		var paths = window.context().paths;
		Button open = Ui.button("Open data folder", Icons.FOLDER, "ghost");
		open.setOnAction(e -> Ui.openFolder(paths.root()));
		Button logs = Ui.button("Open launcher logs", Icons.FOLDER, "ghost");
		logs.setOnAction(e -> Ui.openFolder(paths.logs()));
		Button clear = Ui.button("Clear cache", Icons.DELETE, "ghost");
		clear.setOnAction(e -> Ui.async(() -> {
			FileUtil.deleteRecursively(paths.cache());
			java.nio.file.Files.createDirectories(paths.cache());
			window.context().modrinth.clearCache();
			return null;
		}, ok -> Toasts.success("Cache cleared."), err -> Toasts.error(Ui.message(err))));
		Label location = Ui.label(paths.root().toString(), "small", "mono", "faint");
		return section("Storage", location, Ui.row(10, open, logs, clear));
	}

	private Node about() {
		Label text = Ui.label(VantageInfo.NAME + " " + VantageInfo.VERSION + " · Minecraft PvP client and launcher.\n"
			+ "Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. Minecraft game files are "
			+ "downloaded directly from Mojang's servers to your computer; mods are downloaded from Modrinth under their own licenses.", "muted");
		text.setWrapText(true);
		return section("About", text);
	}

	@Override
	public Node view() {
		ScrollPane scroll = new ScrollPane(root);
		scroll.setFitToWidth(true);
		return scroll;
	}

	@Override
	public void onShow() {
		refreshRuntimes();
	}
}
