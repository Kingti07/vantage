package dev.vantage.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Small transient notifications stacked in the bottom-right corner. */
public final class Toasts {
	public enum Kind {INFO, SUCCESS, ERROR}

	private static VBox stack;

	private Toasts() {
	}

	static void install(StackPane root) {
		stack = new VBox(10);
		stack.setAlignment(Pos.BOTTOM_RIGHT);
		stack.setPickOnBounds(false);
		stack.setMouseTransparent(false);
		stack.setMaxSize(420, StackPane.USE_PREF_SIZE);
		StackPane.setAlignment(stack, Pos.BOTTOM_RIGHT);
		StackPane.setMargin(stack, new javafx.geometry.Insets(0, 24, 72, 0));
		root.getChildren().add(stack);
	}

	public static void info(String message) {
		show(message, Kind.INFO);
	}

	public static void success(String message) {
		show(message, Kind.SUCCESS);
	}

	public static void error(String message) {
		show(message, Kind.ERROR);
	}

	public static void show(String message, Kind kind) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> show(message, kind));
			return;
		}
		if (stack == null) {
			return;
		}
		String icon = switch (kind) {
			case SUCCESS -> Icons.CHECK;
			case ERROR -> Icons.WARNING;
			case INFO -> Icons.BOLT;
		};
		Label text = Ui.label(message);
		text.setWrapText(true);
		text.setMaxWidth(340);
		HBox toast = new HBox(10, Icons.of(icon, 18), text);
		toast.setAlignment(Pos.CENTER_LEFT);
		toast.getStyleClass().addAll("toast", kind.name().toLowerCase());
		toast.setMaxWidth(400);
		stack.getChildren().add(toast);
		if (stack.getChildren().size() > 4) {
			stack.getChildren().removeFirst();
		}

		FadeTransition in = new FadeTransition(Duration.millis(160), toast);
		in.setFromValue(0);
		in.setToValue(1);
		TranslateTransition slide = new TranslateTransition(Duration.millis(160), toast);
		slide.setFromY(12);
		slide.setToY(0);
		PauseTransition hold = new PauseTransition(Duration.seconds(kind == Kind.ERROR ? 7 : 4));
		FadeTransition out = new FadeTransition(Duration.millis(220), toast);
		out.setToValue(0);
		SequentialTransition sequence = new SequentialTransition(in, hold, out);
		sequence.setOnFinished(e -> stack.getChildren().remove(toast));
		slide.play();
		sequence.play();
		toast.setOnMouseClicked(e -> stack.getChildren().remove(toast));
	}
}
