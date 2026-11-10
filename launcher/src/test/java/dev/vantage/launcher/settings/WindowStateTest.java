package dev.vantage.launcher.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowStateTest {
	@Test
	void repairsImpossibleWindowSizes() {
		LauncherSettings settings = new LauncherSettings();
		settings.window.width = 10;
		settings.window.height = Double.NaN;
		settings.window.positioned = true;
		settings.window.x = Double.POSITIVE_INFINITY;
		settings.lastPage = null;
		assertTrue(settings.validate());
		assertEquals(LauncherSettings.WindowState.DEFAULT_WIDTH, settings.window.width);
		assertEquals(LauncherSettings.WindowState.DEFAULT_HEIGHT, settings.window.height);
		assertFalse(settings.window.positioned);
		assertEquals("home", settings.lastPage);
	}

	@Test
	void keepsValidWindowState() {
		LauncherSettings settings = new LauncherSettings();
		settings.window.width = 1400;
		settings.window.height = 900;
		settings.window.positioned = true;
		settings.window.x = -1200;
		assertFalse(settings.validate(), "a window on a monitor left of the main one is fine");
		assertEquals(1400, settings.window.width);
	}
}
