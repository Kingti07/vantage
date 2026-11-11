package dev.vantage.launcher.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Development aid, enabled with {@code -Dvantage.uiSnapshot=<folder>}: visits every launcher page, saves a PNG of
 * the window for each and exits. Used to review the UI without clicking through it manually.
 */
final class UiSnapshots {
	private static final Logger LOGGER = LoggerFactory.getLogger(UiSnapshots.class);
	private static final List<String> PAGES = List.of("home", "installations", "installation-editor", "mods", "mods-browse",
		"mods-performance", "mods-resourcepacks", "mods-shaders", "modpacks", "accounts", "settings", "console");

	private UiSnapshots() {
	}

	static void run(MainWindow window, Path folder) {
		// Optional: replay a captured game output so the console page can be reviewed with real content.
		String console = System.getProperty("vantage.uiSnapshot.console");
		if (console != null && !console.isBlank()) {
			try {
				Files.readAllLines(Path.of(console)).forEach(window.game()::append);
			} catch (IOException e) {
				LOGGER.warn("Could not replay console output from {}", console, e);
			}
		}
		step(window, folder, 0);
	}

	private static void step(MainWindow window, Path folder, int index) {
		if (index >= PAGES.size()) {
			LOGGER.info("UI snapshots written to {}", folder);
			Platform.exit();
			return;
		}
		String page = PAGES.get(index);
		switch (page) {
			case "mods-browse" -> window.<dev.vantage.launcher.ui.pages.ModsPage>page("mods").showBrowse();
			case "mods-performance" -> window.<dev.vantage.launcher.ui.pages.ModsPage>page("mods").showPerformance();
			case "mods-resourcepacks" -> window.<dev.vantage.launcher.ui.pages.ModsPage>page("mods").showBrowse("Resource packs");
			case "mods-shaders" -> window.<dev.vantage.launcher.ui.pages.ModsPage>page("mods").showBrowse("Shaders");
			case "installation-editor" -> window.<dev.vantage.launcher.ui.pages.InstallationsPage>page("installations").createNew();
			default -> window.navigate(page);
		}
		PauseTransition wait = new PauseTransition(Duration.seconds(page.startsWith("mods") || page.endsWith("editor") || "modpacks".equals(page) ? 5 : 1.5));
		wait.setOnFinished(e -> {
			save(window, folder.resolve(String.format("launcher-%02d-%s.png", index, page)));
			if (page.endsWith("editor")) {
				// Close the dialog overlay again before moving on.
				var root = window.root();
				if (root.getChildren().size() > 2) {
					root.getChildren().remove(root.getChildren().size() - 1);
				}
			}
			step(window, folder, index + 1);
		});
		wait.play();
	}

	private static void save(MainWindow window, Path file) {
		try {
			WritableImage image = window.stage().getScene().snapshot(null);
			int w = (int) image.getWidth();
			int h = (int) image.getHeight();
			BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			PixelReader reader = image.getPixelReader();
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					out.setRGB(x, y, reader.getArgb(x, y));
				}
			}
			Files.createDirectories(file.getParent());
			ImageIO.write(out, "png", file.toFile());
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Snapshot {} failed", file, e);
		}
	}
}
