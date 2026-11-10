package dev.vantage.launcher.ui;

import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.ProgressListener;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bottom status bar listing the running long operations (install, download, launch). Each operation gets its own
 * row with status text, progress bar, transfer details (size, speed, remaining time) and, if it supports it, a
 * cancel button. Updates from worker threads are coalesced to at most one UI update per frame and task.
 */
public final class TaskBar extends VBox {
	/** One running operation. {@link #begin} must be called on the JavaFX thread, the listener methods on any thread. */
	public final class Task implements ProgressListener {
		private final HBox row = new HBox(14);
		private final Label status = Ui.label("", "muted");
		private final Label detail = Ui.label("", "small", "faint");
		private final ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final AtomicReference<String> pendingStatus = new AtomicReference<>();
		private final AtomicReference<Double> pendingProgress = new AtomicReference<>();
		private final AtomicReference<String> pendingTransfer = new AtomicReference<>();
		private final AtomicBoolean scheduled = new AtomicBoolean();
		private double fraction = -1;
		private String transferText = "";
		private boolean finished;

		private Task(String initialStatus, boolean cancellable) {
			row.setAlignment(Pos.CENTER_LEFT);
			status.setText(initialStatus);
			status.setMinWidth(260);
			status.setMaxWidth(420);
			bar.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(bar, Priority.ALWAYS);
			detail.setMinWidth(Label.USE_PREF_SIZE);
			row.getChildren().addAll(status, bar, detail);
			if (cancellable) {
				Button cancel = Ui.button("Cancel", null, "ghost", "small-button");
				cancel.setOnAction(e -> {
					cancelled.set(true);
					cancel.setDisable(true);
					status.setText("Cancelling…");
				});
				row.getChildren().add(cancel);
			}
		}

		@Override
		public void status(String message) {
			if (!cancelled.get()) {
				pendingStatus.set(message);
				schedule();
			}
		}

		@Override
		public void progress(double value) {
			pendingProgress.set(value);
			schedule();
		}

		@Override
		public void transfer(long doneBytes, long totalBytes, double bytesPerSecond) {
			pendingTransfer.set(formatTransfer(doneBytes, totalBytes, bytesPerSecond));
			schedule();
		}

		@Override
		public boolean isCancelled() {
			return cancelled.get();
		}

		/** Removes this task from the bar. Call it on the JavaFX thread when the operation ends (also on failure). */
		public void finish() {
			if (finished) {
				return;
			}
			finished = true;
			getChildren().remove(row);
			if (getChildren().isEmpty()) {
				setVisible(false);
				setManaged(false);
			}
		}

		private void schedule() {
			if (scheduled.compareAndSet(false, true)) {
				Platform.runLater(this::apply);
			}
		}

		private void apply() {
			scheduled.set(false);
			String message = pendingStatus.getAndSet(null);
			if (message != null) {
				status.setText(message);
				// A new step starts without transfer details until its first measurement arrives.
				transferText = "";
			}
			String transfer = pendingTransfer.getAndSet(null);
			if (transfer != null) {
				transferText = transfer;
			}
			Double value = pendingProgress.getAndSet(null);
			if (value != null) {
				fraction = value;
				bar.setProgress(value < 0 ? ProgressBar.INDETERMINATE_PROGRESS : value);
			}
			String percent = fraction < 0 ? "" : Math.round(fraction * 100) + "%";
			detail.setText(transferText.isEmpty() ? percent : percent + " · " + transferText);
		}
	}

	public TaskBar() {
		getStyleClass().add("task-bar");
		setSpacing(8);
		setVisible(false);
		setManaged(false);
	}

	/** Starts tracking an operation that cannot be cancelled. */
	public Task begin(String initialStatus) {
		return begin(initialStatus, false);
	}

	/** Starts tracking an operation; with {@code cancellable} the row offers a Cancel button. */
	public Task begin(String initialStatus, boolean cancellable) {
		Task task = new Task(initialStatus, cancellable);
		getChildren().add(task.row);
		if (!isVisible()) {
			setVisible(true);
			setManaged(true);
			FadeTransition fade = new FadeTransition(Duration.millis(150), this);
			fade.setFromValue(0);
			fade.setToValue(1);
			fade.play();
		}
		return task;
	}

	/** "312.4 MB of 700.0 MB · 12.3 MB/s · 0:42 left" */
	static String formatTransfer(long doneBytes, long totalBytes, double bytesPerSecond) {
		StringBuilder text = new StringBuilder(FileUtil.humanSize(Math.max(0, doneBytes)));
		if (totalBytes > 0) {
			text.append(" of ").append(FileUtil.humanSize(totalBytes));
		}
		if (bytesPerSecond > 0) {
			text.append(" · ").append(FileUtil.humanSize((long) bytesPerSecond)).append("/s");
			if (totalBytes > doneBytes) {
				long seconds = Math.round((totalBytes - doneBytes) / bytesPerSecond);
				text.append(" · ").append(formatDuration(seconds)).append(" left");
			}
		}
		return text.toString();
	}

	static String formatDuration(long seconds) {
		if (seconds >= 3600) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
		}
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}
}
