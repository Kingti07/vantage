package dev.vantage.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * In-window modal dialogs. Using overlays instead of native windows keeps every dialog on-theme and makes
 * the launcher feel like one surface.
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
		HBox buttons = new HBox(10, Ui.spacer(), cancel, ok);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		box.getChildren().addAll(heading, body, buttons);
		Runnable close = show(box, 460);
		cancel.setOnAction(e -> close.run());
		ok.setOnAction(e -> {
			close.run();
			onConfirm.run();
		});
	}

	public static void message(String title, String message) {
		VBox box = new VBox(14);
		box.getStyleClass().add("dialog");
		Label body = Ui.label(message, "muted");
		body.setWrapText(true);
		Button ok = Ui.button("OK", null, "primary");
		HBox buttons = new HBox(Ui.spacer(), ok);
		box.getChildren().addAll(Ui.label(title, "h2"), body, buttons);
		Runnable close = show(box, 480);
		ok.setOnAction(e -> close.run());
	}
}
