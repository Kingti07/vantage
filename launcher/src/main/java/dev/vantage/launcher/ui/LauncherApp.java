package dev.vantage.launcher.ui;

import dev.vantage.common.VantageInfo;
import dev.vantage.launcher.LauncherContext;
import dev.vantage.launcher.LauncherPaths;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/** JavaFX application: creates the services and the main window. */
public final class LauncherApp extends Application {
	@Override
	public void start(Stage stage) {
		Ui.setHostServices(getHostServices());
		// "Close launcher after start" only hides the window: the process must stay alive to keep reading the game's
		// output and to handle restart requests. Closing the window explicitly still exits.
		Platform.setImplicitExit(false);
		stage.setOnCloseRequest(event -> Platform.exit());
		LauncherContext context = new LauncherContext(LauncherPaths.resolve());
		MainWindow window = new MainWindow(context, stage);
		Scene scene = new Scene(window.root(), 1180, 740);
		scene.getStylesheets().add(LauncherApp.class.getResource("vantage.css").toExternalForm());
		stage.setScene(scene);
		stage.setTitle(VantageInfo.NAME + " Launcher " + VantageInfo.VERSION);
		stage.setMinWidth(980);
		stage.setMinHeight(640);
		stage.getIcons().add(appIcon());
		stage.show();
		window.onShown();
		String snapshots = System.getProperty("vantage.uiSnapshot");
		if (snapshots != null && !snapshots.isBlank()) {
			UiSnapshots.run(window, java.nio.file.Path.of(snapshots));
		}
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
