package dev.vantage.launcher.ui;

import dev.vantage.common.VantageInfo;
import dev.vantage.launcher.LauncherContext;
import dev.vantage.launcher.LauncherPaths;
import dev.vantage.launcher.settings.LauncherSettings;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

/**
 * JavaFX application: creates the services and the main window, restores the window's size and position, asks
 * before closing while Minecraft runs and registers the keyboard shortcuts.
 */
public final class LauncherApp extends Application {
	/** Ctrl/Cmd + 1..6 opens these pages. */
	private static final List<String> PAGE_SHORTCUTS = List.of("home", "installations", "mods", "accounts", "settings", "console");
	private static final List<KeyCode> DIGITS = List.of(KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4,
		KeyCode.DIGIT5, KeyCode.DIGIT6);

	private LauncherContext context;
	private MainWindow window;
	private Stage stage;

	@Override
	public void start(Stage primaryStage) {
		this.stage = primaryStage;
		Ui.setHostServices(getHostServices());
		// "Close launcher after start" only hides the window: the process must stay alive to keep reading the game's
		// output and to handle restart requests. Closing the window explicitly still exits (after asking, see below).
		Platform.setImplicitExit(false);
		context = new LauncherContext(LauncherPaths.resolve());
		window = new MainWindow(context, stage);
		LauncherSettings.WindowState state = context.settings.settings().window;
		Scene scene = new Scene(window.root(), state.width, state.height);
		scene.getStylesheets().add(LauncherApp.class.getResource("vantage.css").toExternalForm());
		registerShortcuts(scene);
		stage.setScene(scene);
		stage.setTitle(VantageInfo.NAME + " Launcher " + VantageInfo.VERSION);
		stage.setMinWidth(LauncherSettings.WindowState.MIN_WIDTH);
		stage.setMinHeight(LauncherSettings.WindowState.MIN_HEIGHT);
		stage.getIcons().add(appIcon());
		if (state.positioned && Screen.getScreensForRectangle(state.x, state.y, state.width, state.height).size() > 0) {
			stage.setX(state.x);
			stage.setY(state.y);
		}
		stage.setMaximized(state.maximized);
		stage.setOnCloseRequest(event -> {
			event.consume();
			requestClose();
		});
		stage.show();
		rememberWindowState();
		dev.vantage.launcher.Main.onWindowShown(context.paths);
		window.onShown();
		String snapshots = System.getProperty("vantage.uiSnapshot");
		if (snapshots != null && !snapshots.isBlank()) {
			UiSnapshots.run(window, java.nio.file.Path.of(snapshots));
		}
	}

	private void registerShortcuts(Scene scene) {
		for (int i = 0; i < PAGE_SHORTCUTS.size(); i++) {
			String page = PAGE_SHORTCUTS.get(i);
			scene.getAccelerators().put(new KeyCodeCombination(DIGITS.get(i), KeyCombination.SHORTCUT_DOWN), () -> window.navigate(page));
		}
		scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), window::playSelected);
	}

	/** Closing while the game runs or is being prepared would silently break things, so ask first. */
	private void requestClose() {
		switch (window.game().phase()) {
			case RUNNING -> Overlay.confirm("Minecraft is still running",
				"Closing the launcher does not stop the game, but its console output and the in-game account switch stop "
					+ "working until you open the launcher again.", "Close launcher", false, Platform::exit);
			case PREPARING -> Overlay.confirm("Minecraft is being prepared",
				"Closing the launcher cancels the download. Files downloaded so far are kept for next time.", "Close launcher",
				false, Platform::exit);
			case IDLE -> Platform.exit();
		}
	}

	/** Saves size, position and maximised state half a second after the user stops moving or resizing the window. */
	private void rememberWindowState() {
		PauseTransition debounce = new PauseTransition(Duration.millis(500));
		debounce.setOnFinished(e -> {
			LauncherSettings.WindowState state = context.settings.settings().window;
			state.maximized = stage.isMaximized();
			if (!stage.isMaximized() && !stage.isIconified()) {
				state.positioned = true;
				state.x = stage.getX();
				state.y = stage.getY();
				state.width = stage.getWidth();
				state.height = stage.getHeight();
			}
			context.settings.save();
		});
		ChangeListener<Object> changed = (obs, old, value) -> debounce.playFromStart();
		stage.xProperty().addListener(changed);
		stage.yProperty().addListener(changed);
		stage.widthProperty().addListener(changed);
		stage.heightProperty().addListener(changed);
		stage.maximizedProperty().addListener(changed);
	}

	/** Generates the window icon from the logo colours so no binary asset is required. */
	private static Image appIcon() {
		int size = 64;
		WritableImage image = new WritableImage(size, size);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				double u = x / (double) size;
				double v = y / (double) size;
				// A "V" shape: inside if close to either diagonal arm.
				double left = Math.abs(u - (0.12 + v * 0.38));
				double right = Math.abs(u - (0.88 - v * 0.38));
				boolean arm = (left < 0.09 || right < 0.09) && v > 0.08 && v < 0.92;
				int argb = arm ? 0xFF7C5CFF : ((x - 32) * (x - 32) + (y - 32) * (y - 32) < 31 * 31 ? 0xFF11141B : 0x00000000);
				image.getPixelWriter().setArgb(x, y, argb);
			}
		}
		return image;
	}
}
