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

	/**
	 * Byte-level detail of the current transfer, used to show size, speed and remaining time.
	 *
	 * @param doneBytes      bytes transferred in the current step
	 * @param totalBytes     bytes expected in the current step, or a value &lt;= 0 if unknown
	 * @param bytesPerSecond smoothed transfer rate
	 */
	default void transfer(long doneBytes, long totalBytes, double bytesPerSecond) {
	}

	/** True once the user asked to cancel. Long operations stop with a {@link CancelledException}. */
	default boolean isCancelled() {
		return false;
	}

	/** Throws {@link CancelledException} if the user asked to cancel. */
	default void checkCancelled() throws CancelledException {
		if (isCancelled()) {
			throw new CancelledException("Cancelled");
		}
	}
}
