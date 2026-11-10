package dev.vantage.launcher.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskBarFormatTest {
	@Test
	void formatsSizeSpeedAndRemainingTime() {
		long mb = 1024L * 1024L;
		assertEquals("100.0 MB of 700.0 MB · 10.0 MB/s · 1:00 left", TaskBar.formatTransfer(100 * mb, 700 * mb, 10 * mb));
		assertEquals("5.0 MB", TaskBar.formatTransfer(5 * mb, 0, 0));
	}

	@Test
	void formatsLongDurations() {
		assertEquals("0:05", TaskBar.formatDuration(5));
		assertEquals("1:01:01", TaskBar.formatDuration(3661));
	}
}
