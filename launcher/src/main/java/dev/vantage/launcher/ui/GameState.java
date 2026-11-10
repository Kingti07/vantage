package dev.vantage.launcher.ui;

import dev.vantage.launcher.game.GameLogParser;
import dev.vantage.launcher.game.GameLogParser.LogLine;
import dev.vantage.launcher.game.LaunchService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Observable state of the game process plus a bounded, parsed buffer of its console output.
 */
public final class GameState {
	public enum Phase {IDLE, PREPARING, RUNNING}

	/**
	 * How the last game session ended.
	 *
	 * @param startedAt epoch millis when the process was started (to find the crash report it wrote)
	 * @param stoppedByUser true if the user pressed "Stop game", so a non-zero exit code is not a crash
	 */
	public record LastExit(int exitCode, String instanceName, Path gameDir, long startedAt, boolean stoppedByUser) {
	}

	/** Console lines kept in memory; older ones are dropped (the game's own logs/latest.log has everything). */
	public static final int MAX_LINES = 5_000;

	private final ObjectProperty<Phase> phase = new SimpleObjectProperty<>(Phase.IDLE);
	private final ObjectProperty<LastExit> lastExit = new SimpleObjectProperty<>();
	private final ObservableList<LogLine> lines = FXCollections.observableArrayList();
	private final ObservableList<LogLine> readOnlyLines = FXCollections.unmodifiableObservableList(lines);
	private final GameLogParser parser = new GameLogParser();
	private final List<LogLine> pending = new ArrayList<>();
	private boolean flushScheduled;
	private LaunchService.Session session;
	private long startedAt;
	private volatile boolean stoppedByUser;

	public ReadOnlyObjectProperty<Phase> phaseProperty() {
		return phase;
	}

	public Phase phase() {
		return phase.get();
	}

	public ReadOnlyObjectProperty<LastExit> lastExitProperty() {
		return lastExit;
	}

	public void preparing() {
		phase.set(Phase.PREPARING);
	}

	public void idle() {
		phase.set(Phase.IDLE);
	}

	public void started(LaunchService.Session session) {
		this.session = session;
		this.startedAt = System.currentTimeMillis();
		this.stoppedByUser = false;
		phase.set(Phase.RUNNING);
	}

	public void exited(int code) {
		LaunchService.Session ended = session;
		session = null;
		phase.set(Phase.IDLE);
		if (ended != null) {
			lastExit.set(new LastExit(code, ended.instance().name, ended.gameDir(), startedAt, stoppedByUser));
		}
	}

	/** Stops the running game (the user pressed "Stop game"). */
	public void kill() {
		if (session != null) {
			stoppedByUser = true;
			session.process().destroy();
		}
	}

	/** Thread-safe; raw output is parsed and delivered to the observable list in batches on the FX thread. */
	public void append(String raw) {
		synchronized (pending) {
			// Launcher messages bypass the parser so they never end up inside a half-received log event.
			pending.addAll(raw.startsWith("[Vantage]") ? List.of(GameLogParser.plain(raw)) : parser.feed(raw));
			if (pending.isEmpty() || flushScheduled) {
				return;
			}
			flushScheduled = true;
		}
		Platform.runLater(this::flush);
	}

	private void flush() {
		List<LogLine> batch;
		synchronized (pending) {
			batch = new ArrayList<>(pending);
			pending.clear();
			flushScheduled = false;
		}
		lines.addAll(batch);
		if (lines.size() > MAX_LINES) {
			lines.remove(0, lines.size() - MAX_LINES);
		}
	}

	/** The parsed console output (FX thread only). */
	public ObservableList<LogLine> lines() {
		return readOnlyLines;
	}

	public void clear() {
		lines.clear();
	}
}
