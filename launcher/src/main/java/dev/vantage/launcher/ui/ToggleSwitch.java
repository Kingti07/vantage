package dev.vantage.launcher.ui;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** Animated on/off switch. */
public final class ToggleSwitch extends StackPane {
	private static final double WIDTH = 42;
	private static final double HEIGHT = 24;

	private final BooleanProperty selected = new SimpleBooleanProperty();
	private final Circle knob = new Circle(8);

	public ToggleSwitch(boolean initial) {
		getStyleClass().add("toggle-track");
		setMinSize(WIDTH, HEIGHT);
		setPrefSize(WIDTH, HEIGHT);
		setMaxSize(WIDTH, HEIGHT);
		setAlignment(Pos.CENTER_LEFT);
		setCursor(Cursor.HAND);
		knob.getStyleClass().add("toggle-knob");
		knob.setTranslateX(4);
		getChildren().add(knob);
		selected.addListener((obs, old, value) -> update(true));
		selected.set(initial);
		update(false);
		setOnMouseClicked(e -> {
			if (!isDisabled()) {
				selected.set(!selected.get());
			}
		});
	}

	private void update(boolean animate) {
		double target = selected.get() ? WIDTH - 20 : 4;
		getStyleClass().remove("on");
		if (selected.get()) {
			getStyleClass().add("on");
		}
		if (animate) {
			TranslateTransition transition = new TranslateTransition(Duration.millis(140), knob);
			transition.setToX(target);
			transition.play();
		} else {
			knob.setTranslateX(target);
		}
	}

	public BooleanProperty selectedProperty() {
		return selected;
	}

	public boolean isSelected() {
		return selected.get();
	}

	public void setSelected(boolean value) {
		selected.set(value);
	}
}
