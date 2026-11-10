package dev.vantage.launcher.ui;

import dev.vantage.common.VantageInfo;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

/**
 * In-window modal dialogs. Using overlays instead of native windows keeps every dialog on-theme and makes
 * the launcher feel like one surface. Enter confirms and Escape closes every dialog.
 */
public final class Overlay {
	private static StackPane host;

	private Overlay() {
	}

	static void install(StackPane root) {
		host = root;
	}

	/** Shows {@code content} centred above the window. Returns a handle that closes it. */
	public static Runnable show(Node content, double maxWidth) {
		StackPane dim = new StackPane();
		dim.getStyleClass().add("overlay-dim");
		VBox dialog = content instanceof VBox v && v.getStyleClass().contains("dialog") ? v : new VBox(content);
		if (!dialog.getStyleClass().contains("dialog")) {
			dialog.getStyleClass().add("dialog");
		}
		dialog.setMaxWidth(maxWidth);
		dialog.setMaxHeight(Region.USE_PREF_SIZE);
		dim.getChildren().add(dialog);
		StackPane.setAlignment(dialog, Pos.CENTER);
		host.getChildren().add(dim);

		FadeTransition fade = new FadeTransition(Duration.millis(140), dim);
		fade.setFromValue(0);
		fade.setToValue(1);
		ScaleTransition scale = new ScaleTransition(Duration.millis(160), dialog);
		scale.setFromX(0.96);
		scale.setFromY(0.96);
		scale.setToX(1);
		scale.setToY(1);
		new ParallelTransition(fade, scale).play();

		Runnable close = () -> {
			FadeTransition out = new FadeTransition(Duration.millis(110), dim);
			out.setToValue(0);
			out.setOnFinished(e -> host.getChildren().remove(dim));
			out.play();
		};
		dim.setOnMouseClicked(e -> {
			if (e.getTarget() == dim) {
				close.run();
			}
		});
		dim.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				close.run();
			}
		});
		dim.requestFocus();
		return close;
	}

	/** Confirmation dialog; {@code onConfirm} runs only if the user confirms. */
	public static void confirm(String title, String message, String confirmText, boolean danger, Runnable onConfirm) {
		VBox box = new VBox(14);
		box.getStyleClass().add("dialog");
		Label heading = Ui.label(title, "h2");
		Label body = Ui.label(message, "muted");
		body.setWrapText(true);
		Button cancel = Ui.button("Cancel", null, "ghost");
		Button ok = Ui.button(confirmText, null, danger ? "danger" : "primary");
		// Destructive actions are not confirmed by an accidental Enter.
		ok.setDefaultButton(!danger);
		cancel.setCancelButton(true);
		HBox buttons = new HBox(10, Ui.spacer(), cancel, ok);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		box.getChildren().addAll(heading, body, buttons);
		Runnable close = show(box, 460);
		cancel.setOnAction(e -> close.run());
		ok.setOnAction(e -> {
			close.run();
			onConfirm.run();
		});
		(danger ? cancel : ok).requestFocus();
	}

	public static void message(String title, String message) {
		VBox box = new VBox(14);
		box.getStyleClass().add("dialog");
		Label body = Ui.label(message, "muted");
		body.setWrapText(true);
		Button ok = Ui.button("OK", null, "primary");
		ok.setDefaultButton(true);
		HBox buttons = new HBox(Ui.spacer(), ok);
		box.getChildren().addAll(Ui.label(title, "h2"), body, buttons);
		Runnable close = show(box, 480);
		ok.setOnAction(e -> close.run());
		ok.requestFocus();
	}

	/**
	 * Explains an error in plain words (see {@link ErrorText}) and offers the technical details for a bug report
	 * via "Copy details" instead of showing a stack trace.
	 */
	public static void error(String title, Throwable error) {
		VBox box = new VBox(14);
		box.getStyleClass().add("dialog");
		Label body = Ui.label(Ui.message(error), "muted");
		body.setWrapText(true);
		Button copy = Ui.button("Copy details", Icons.COPY, "ghost");
		copy.setOnAction(e -> {
			copyToClipboard(technicalDetails(title, error));
			Toasts.success("Details copied. Paste them into a bug report.");
		});
		Button ok = Ui.button("OK", null, "primary");
		ok.setDefaultButton(true);
		HBox buttons = new HBox(10, copy, Ui.spacer(), ok);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		box.getChildren().addAll(Ui.label(title, "h2"), body, buttons);
		Runnable close = show(box, 520);
		ok.setOnAction(e -> close.run());
		ok.requestFocus();
	}

	/**
	 * Shows a long text such as a crash report excerpt with "Copy" and, if {@code file} is given, "Open file".
	 */
	public static void report(String title, String message, String details, Path file) {
		VBox box = new VBox(14);
		box.getStyleClass().add("dialog");
		Label body = Ui.label(message, "muted");
		body.setWrapText(true);
		TextArea text = new TextArea(details);
		text.setEditable(false);
		text.setWrapText(false);
		text.setPrefRowCount(14);
		text.getStyleClass().add("report-area");
		Button copy = Ui.button("Copy", Icons.COPY, "ghost");
		copy.setOnAction(e -> {
			copyToClipboard(details);
			Toasts.success("Copied to the clipboard.");
		});
		HBox buttons = new HBox(10, copy);
		if (file != null) {
			Button open = Ui.button("Open file", Icons.OPEN, "ghost");
			open.setOnAction(e -> Ui.openFile(file));
			buttons.getChildren().add(open);
		}
		Button ok = Ui.button("OK", null, "primary");
		ok.setDefaultButton(true);
		buttons.getChildren().addAll(Ui.spacer(), ok);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		box.getChildren().addAll(Ui.label(title, "h2"), body, text, buttons);
		Runnable close = show(box, 820);
		ok.setOnAction(e -> close.run());
		ok.requestFocus();
	}

	static String technicalDetails(String title, Throwable error) {
		StringWriter trace = new StringWriter();
		error.printStackTrace(new PrintWriter(trace));
		return title + "\nVantage Launcher " + VantageInfo.VERSION + " · " + System.getProperty("os.name") + " "
			+ System.getProperty("os.version") + " · Java " + System.getProperty("java.version") + "\n\n" + trace;
	}

	private static void copyToClipboard(String text) {
		ClipboardContent content = new ClipboardContent();
		content.putString(text);
		Clipboard.getSystemClipboard().setContent(content);
	}
}
