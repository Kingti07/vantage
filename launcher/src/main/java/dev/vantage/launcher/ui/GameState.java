package dev.vantage.launcher.ui;

import dev.vantage.launcher.game.LaunchService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Observable state of the game process plus a bounded buffer of its console output.
 */
public final class GameState {
	public enum Phase {IDLE, PREPARING, RUNNING}

	public record LastExit(int exitCode, String instanceName, java.nio.file.Path gameDir) {
	}

	private static final int MAX_LINES = 8000;

	private final ObjectProperty<Phase> phase = new SimpleObjectProperty<>(Phase.IDLE);
	private final ObjectProperty<LastExit> lastExit = new SimpleObjectProperty<>();
	private final ArrayDeque<String> lines = new ArrayDeque<>();
	private final List<Consumer<List<String>>> lineListeners = new ArrayList<>();
	private final List<String> pending = new ArrayList<>();
	private boolean flushScheduled;
	private LaunchService.Session session;

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
		phase.set(Phase.RUNNING);
	}

	public void exited(int code) {
		LaunchService.Session ended = session;
		session = null;
		phase.set(Phase.IDLE);
		if (ended != null) {
			lastExit.set(new LastExit(code, ended.instance().name, ended.gameDir()));
		}
	}

	/** Forcefully stops the running game. */
	public void kill() {
		if (session != null) {
			session.process().destroy();
		}
	}

	/** Thread-safe; lines are delivered to listeners in batches on the FX thread. */
	public void append(String line) {
		synchronized (pending) {
			pending.add(line);
			if (flushScheduled) {
				return;
			}
			flushScheduled = true;
		}
		Platform.runLater(this::flush);
	}

	private void flush() {
		List<String> batch;
		synchronized (pending) {
			batch = new ArrayList<>(pending);
			pending.clear();
			flushScheduled = false;
		}
		for (String line : batch) {
			lines.addLast(line);
			if (lines.size() > MAX_LINES) {
				lines.removeFirst();
			}
		}
		lineListeners.forEach(listener -> listener.accept(batch));
	}

	public List<String> lines() {
		return new ArrayList<>(lines);
	}

	public void clear() {
		lines.clear();
	}

	public void onLines(Consumer<List<String>> listener) {
		lineListeners.add(listener);
	}
}
