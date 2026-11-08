package dev.vantage.common.util;

/**
 * Receives progress for long-running work (downloads, installs). Implementations must be thread-safe:
 * downloads run in parallel and report from worker threads.
 */
public interface ProgressListener {
	ProgressListener NONE = new ProgressListener() {
		@Override
		public void status(String message) {
		}

		@Override
		public void progress(double fraction) {
		}
	};

	/** Human readable description of the current step. */
	void status(String message);

	/** Overall progress in [0, 1], or a negative value for indeterminate. */
	void progress(double fraction);
}
