package dev.vantage.launcher.ui.pages;

import dev.vantage.launcher.game.GameLogParser.Level;
import dev.vantage.launcher.game.GameLogParser.LogLine;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.ui.GameState;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.ToggleSwitch;
import dev.vantage.launcher.ui.Ui;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Live game output as readable, coloured lines (parsed from Minecraft's XML log events), with search, a
 * "problems only" filter and shortcuts to logs and crash reports. A virtualised list keeps the UI fast however
 * much the game prints.
 */
public final class ConsolePage implements Page {
	private static final String[] LEVEL_STYLES = {"log-error", "log-warn", "log-info", "log-debug", "log-launcher", "log-plain"};

	private final MainWindow window;
	private final VBox root = new VBox(12);
	private final FilteredList<LogLine> filtered;
	private final ListView<LogLine> list;
	private final TextField search = new TextField();
	private final ToggleSwitch follow = new ToggleSwitch(true);
	private final ToggleSwitch problemsOnly = new ToggleSwitch(false);
	private final Label empty = Ui.label("Nothing here yet. Start the game and its output appears here.", "muted");
	private boolean scrollScheduled;

	public ConsolePage(MainWindow window) {
		this.window = window;
		root.getStyleClass().add("page");

		filtered = new FilteredList<>(window.game().lines(), line -> true);
		list = new ListView<>(filtered);
		list.getStyleClass().add("console-list");
		list.setCellFactory(view -> new LogCell());
		list.setPlaceholder(empty);
		VBox.setVgrow(list, Priority.ALWAYS);

		search.setPromptText("Search output");
		search.setPrefWidth(220);
		search.textProperty().addListener((o, a, b) -> updateFilter());
		problemsOnly.selectedProperty().addListener((o, a, b) -> updateFilter());
		window.game().lines().addListener((ListChangeListener<LogLine>) change -> scheduleScroll());

		Button copy = Ui.button("Copy", Icons.COPY, "ghost");
		copy.setOnAction(e -> {
			ClipboardContent content = new ClipboardContent();
			content.putString(filtered.stream().map(LogLine::format).collect(Collectors.joining("\n")));
			Clipboard.getSystemClipboard().setContent(content);
			Toasts.success(filtered.size() + " lines copied to the clipboard.");
		});
		Button clear = Ui.button("Clear", Icons.DELETE, "ghost");
		clear.setOnAction(e -> window.game().clear());
		Button logs = Ui.button("Game logs", Icons.FOLDER, "ghost");
		logs.setOnAction(e -> Ui.openFolder(gameDir().resolve("logs")));
		Button crashes = Ui.button("Crash reports", Icons.FOLDER, "ghost");
		crashes.setOnAction(e -> Ui.openFolder(gameDir().resolve("crash-reports")));
		Button kill = Ui.button("Stop game", Icons.STOP, "danger");
		kill.setOnAction(e -> window.game().kill());
		kill.disableProperty().bind(window.game().phaseProperty().isNotEqualTo(GameState.Phase.RUNNING));

		HBox header = Ui.row(10, new VBox(4, Ui.label("Console", "h1"), Ui.label("Output of the running game.", "muted")), Ui.spacer(),
			logs, crashes, kill);
		HBox toolbar = Ui.row(10, search, Ui.label("Problems only", "small", "muted"), problemsOnly,
			Ui.label("Follow", "small", "muted"), follow, Ui.spacer(), copy, clear);
		root.getChildren().addAll(header, toolbar, list);
	}

	private void updateFilter() {
		String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		boolean problems = problemsOnly.isSelected();
		filtered.setPredicate(line -> (!problems || line.level() == Level.ERROR || line.level() == Level.WARN)
			&& (needle.isEmpty() || line.text().toLowerCase(Locale.ROOT).contains(needle)));
		scheduleScroll();
	}

	/** Scrolls to the newest line at most once per frame while "Follow" is on. */
	private void scheduleScroll() {
		if (!follow.isSelected() || scrollScheduled) {
			return;
		}
		scrollScheduled = true;
		Platform.runLater(() -> {
			scrollScheduled = false;
			if (!filtered.isEmpty()) {
				list.scrollTo(filtered.size() - 1);
			}
		});
	}

	private java.nio.file.Path gameDir() {
		Instance instance = window.context().settings.selectedInstance();
		return instance.resolveGameDirectory(window.context().paths.instances());
	}

	@Override
	public Node view() {
		return root;
	}

	@Override
	public void onShow() {
		scheduleScroll();
	}

	private static final class LogCell extends ListCell<LogLine> {
		@Override
		protected void updateItem(LogLine line, boolean emptyCell) {
			super.updateItem(line, emptyCell);
			getStyleClass().removeAll(LEVEL_STYLES);
			if (emptyCell || line == null) {
				setText(null);
				return;
			}
			setText(line.format());
			getStyleClass().add("log-" + line.level().name().toLowerCase(Locale.ROOT));
		}
	}
}
