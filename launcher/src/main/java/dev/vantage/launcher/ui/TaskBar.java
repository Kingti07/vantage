package dev.vantage.launcher.ui;

import dev.vantage.common.util.ProgressListener;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bottom status bar showing the current long-running operation (install, download, launch) with a progress
 * bar. Updates from worker threads are coalesced to at most one UI update per frame.
 */
public final class TaskBar extends HBox {
	private final Label status = Ui.label("", "muted");
	private final Label percent = Ui.label("", "small", "faint");
	private final ProgressBar bar = new ProgressBar(0);
	private final AtomicReference<String> pendingStatus = new AtomicReference<>();
	private final AtomicReference<Double> pendingProgress = new AtomicReference<>();
	private final AtomicBoolean scheduled = new AtomicBoolean();
	private int activeTasks;

	public TaskBar() {
		getStyleClass().add("task-bar");
		setAlignment(Pos.CENTER_LEFT);
		bar.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(bar, Priority.ALWAYS);
		status.setMinWidth(260);
		status.setMaxWidth(420);
		getChildren().addAll(status, bar, percent);
		setVisible(false);
		setManaged(false);
	}

	/** Starts tracking a task. Call {@link #finish()} when done (also on failure). */
	public ProgressListener begin(String initialStatus) {
		activeTasks++;
		status.setText(initialStatus);
		bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
		percent.setText("");
		if (!isVisible()) {
			setVisible(true);
			setManaged(true);
			FadeTransition fade = new FadeTransition(Duration.millis(150), this);
			fade.setFromValue(0);
			fade.setToValue(1);
			fade.play();
		}
		return new ProgressListener() {
			@Override
			public void status(String message) {
				pendingStatus.set(message);
				schedule();
			}

			@Override
			public void progress(double fraction) {
				pendingProgress.set(fraction);
				schedule();
			}
		};
	}

	public void finish() {
		activeTasks = Math.max(0, activeTasks - 1);
		if (activeTasks == 0) {
			setVisible(false);
			setManaged(false);
		}
	}

	private void schedule() {
		if (scheduled.compareAndSet(false, true)) {
			Platform.runLater(() -> {
				scheduled.set(false);
				String message = pendingStatus.getAndSet(null);
				if (message != null) {
					status.setText(message);
				}
				Double progress = pendingProgress.getAndSet(null);
				if (progress != null) {
					if (progress < 0) {
						bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
						percent.setText("");
					} else {
						bar.setProgress(progress);
						percent.setText(Math.round(progress * 100) + "%");
					}
				}
			});
		}
	}
}
