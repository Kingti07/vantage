package dev.vantage.launcher.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Small UI helpers shared by all pages. */
public final class Ui {
	private static final Logger LOGGER = LoggerFactory.getLogger(Ui.class);
	private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
		Thread thread = new Thread(r, "vantage-ui-worker");
		thread.setDaemon(true);
		return thread;
	});

	private Ui() {
	}

	/** Runs {@code work} off the FX thread and delivers the result (or error) back on the FX thread. */
	public static <T> void async(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
		POOL.submit(() -> {
			try {
				T result = work.call();
				Platform.runLater(() -> onSuccess.accept(result));
			} catch (Throwable t) {
				LOGGER.warn("Background task failed", t);
				Platform.runLater(() -> onError.accept(t));
			}
		});
	}

	public static void background(Runnable work) {
		POOL.submit(work);
	}

	public static Label label(String text, String... styles) {
		Label label = new Label(text);
		label.getStyleClass().addAll(styles);
		return label;
	}

	public static Button button(String text, String icon, String... styles) {
		Button button = new Button(text);
		if (icon != null) {
			button.setGraphic(Icons.of(icon, 16));
		}
		button.getStyleClass().addAll(styles);
		// Never let a layout squeeze a button into an ellipsis.
		button.setMinWidth(Region.USE_PREF_SIZE);
		return button;
	}

	public static Button iconButton(String icon, String tooltip, String... styles) {
		Button button = new Button();
		button.setGraphic(Icons.of(icon, 18));
		button.getStyleClass().add("icon-only");
		button.getStyleClass().addAll(styles);
		button.setTooltip(new Tooltip(tooltip));
		button.setMinWidth(Region.USE_PREF_SIZE);
		return button;
	}

	public static Region spacer() {
		Region region = new Region();
		HBox.setHgrow(region, Priority.ALWAYS);
		VBox.setVgrow(region, Priority.ALWAYS);
		return region;
	}

	public static HBox row(double spacing, Node... children) {
		HBox box = new HBox(spacing, children);
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	public static VBox column(double spacing, Node... children) {
		return new VBox(spacing, children);
	}

	/** Title + description block used by settings rows. */
	public static VBox describe(String title, String description) {
		Label t = label(title, "h3");
		Label d = label(description, "muted");
		d.setWrapText(true);
		return new VBox(4, t, d);
	}

	public static String compactNumber(long value) {
		if (value >= 1_000_000_000) {
			return String.format(Locale.ROOT, "%.1fB", value / 1e9);
		}
		if (value >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", value / 1e6);
		}
		if (value >= 1_000) {
			return String.format(Locale.ROOT, "%.1fK", value / 1e3);
		}
		return Long.toString(value);
	}

	/** "3 days ago" style relative time for ISO-8601 timestamps. */
	public static String relativeTime(String isoTimestamp) {
		if (isoTimestamp == null) {
			return "";
		}
		try {
			Instant then = OffsetDateTime.parse(isoTimestamp).toInstant();
			return relativeTime(then);
		} catch (DateTimeParseException e) {
			return "";
		}
	}

	public static String relativeTime(Instant then) {
		Duration ago = Duration.between(then, Instant.now());
		long days = ago.toDays();
		if (days >= 365) {
			return (days / 365) + (days / 365 == 1 ? " year ago" : " years ago");
		}
		if (days >= 30) {
			return (days / 30) + (days / 30 == 1 ? " month ago" : " months ago");
		}
		if (days >= 1) {
			return days + (days == 1 ? " day ago" : " days ago");
		}
		long hours = ago.toHours();
		if (hours >= 1) {
			return hours + (hours == 1 ? " hour ago" : " hours ago");
		}
		return "just now";
	}

	private static javafx.application.HostServices hostServices;

	static void setHostServices(javafx.application.HostServices services) {
		hostServices = services;
	}

	/** Opens a folder in the system file manager (created first if missing). */
	public static void openFolder(Path folder) {
		background(() -> {
			try {
				Files.createDirectories(folder);
				openNative(folder.toAbsolutePath().toString());
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Could not open folder {}", folder, e);
			}
		});
	}

	/** Opens an http(s) link in the default browser. Other schemes are ignored. */
	public static void openUrl(String url) {
		if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
			return;
		}
		if (hostServices != null) {
			hostServices.showDocument(URI.create(url).toString());
			return;
		}
		background(() -> {
			try {
				openNative(url);
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Could not open {}", url, e);
			}
		});
	}

	private static void openNative(String target) throws IOException {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			new ProcessBuilder("explorer", target).start();
		} else if (os.contains("mac")) {
			new ProcessBuilder("open", target).start();
		} else {
			new ProcessBuilder("xdg-open", target).start();
		}
	}

	public static String message(Throwable t) {
		Throwable root = t;
		while (root.getCause() != null && root.getMessage() == null) {
			root = root.getCause();
		}
		return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
	}
}
