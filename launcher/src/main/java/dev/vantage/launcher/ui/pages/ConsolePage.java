package dev.vantage.launcher.ui.pages;

import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.ToggleSwitch;
import dev.vantage.launcher.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Live game output plus shortcuts to logs and crash reports. */
public final class ConsolePage implements Page {
	private static final int MAX_CHARS = 1_500_000;

	private final MainWindow window;
	private final VBox root = new VBox();
	private final TextArea output = new TextArea();
	private final ToggleSwitch follow = new ToggleSwitch(true);

	public ConsolePage(MainWindow window) {
		this.window = window;
		root.getStyleClass().add("page");
		output.setEditable(false);
		output.setWrapText(false);
		output.getStyleClass().add("console-area");
		VBox.setVgrow(output, Priority.ALWAYS);

		Button copy = Ui.button("Copy", Icons.COPY, "ghost");
		copy.setOnAction(e -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(output.getText());
			Clipboard.getSystemClipboard().setContent(content);
			Toasts.success("Log copied to clipboard.");
		});
		Button clear = Ui.button("Clear", Icons.DELETE, "ghost");
		clear.setOnAction(e -> {
			window.game().clear();
			output.clear();
		});
		Button logs = Ui.button("Game logs", Icons.FOLDER, "ghost");
		logs.setOnAction(e -> Ui.openFolder(gameDir().resolve("logs")));
		Button crashes = Ui.button("Crash reports", Icons.FOLDER, "ghost");
		crashes.setOnAction(e -> Ui.openFolder(gameDir().resolve("crash-reports")));
		Button kill = Ui.button("Stop game", Icons.STOP, "danger");
		kill.setOnAction(e -> window.game().kill());
		kill.disableProperty().bind(window.game().phaseProperty().isNotEqualTo(dev.vantage.launcher.ui.GameState.Phase.RUNNING));

		HBox toolbar = Ui.row(10, new VBox(4, Ui.label("Console", "h1"), Ui.label("Output of the running game.", "muted")), Ui.spacer(),
			Ui.label("Follow", "small", "muted"), follow, copy, clear, logs, crashes, kill);
		root.getChildren().addAll(toolbar, output);
		window.game().onLines(this::append);
	}

	private java.nio.file.Path gameDir() {
		Instance instance = window.context().settings.selectedInstance();
		return instance.resolveGameDirectory(window.context().paths.instances());
	}

	private void append(List<String> lines) {
		StringBuilder text = new StringBuilder();
		for (String line : lines) {
			text.append(line).append('\n');
		}
		double scroll = output.getScrollTop();
		output.appendText(text.toString());
		if (output.getLength() > MAX_CHARS) {
			output.deleteText(0, output.getLength() - MAX_CHARS);
		}
		if (follow.isSelected()) {
			output.positionCaret(output.getLength());
			output.setScrollTop(Double.MAX_VALUE);
		} else {
			output.setScrollTop(scroll);
		}
	}

	@Override
	public Node view() {
		return root;
	}

	@Override
	public void onShow() {
		if (output.getText().isEmpty()) {
			List<String> lines = window.game().lines();
			if (!lines.isEmpty()) {
				append(lines);
			}
		}
	}
}
