package dev.vantage.client.ui;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.InterfaceModule;
import dev.vantage.client.util.Colors;

/** Colour palette of the in-game UI, derived from the user's Interface settings. */
public final class Theme {
	public static final int TEXT = 0xFFE8EAF0;
	public static final int MUTED = 0xFF8A93A6;
	public static final int FAINT = 0xFF5B6375;
	public static final int DANGER = 0xFFFF5C7A;
	public static final int SUCCESS = 0xFF3DDC97;
	public static final int WARNING = 0xFFFFB547;
	public static final int RADIUS = 6;

	private Theme() {
	}

	private static InterfaceModule ui() {
		return VantageClient.get().interfaceModule();
	}

	public static int accent() {
		return ui().accentColor();
	}

	public static int secondary() {
		return ui().secondaryColor();
	}

	public static int panel() {
		return ui().panelColor.get();
	}

	public static int surface() {
		return Colors.withAlpha(Colors.shade(Colors.withAlpha(panel(), 255), 0.06f), Colors.alpha(panel()));
	}

	public static int surfaceHover() {
		return Colors.withAlpha(Colors.shade(Colors.withAlpha(panel(), 255), 0.12f), 0xF0);
	}

	public static int border() {
		return 0x30FFFFFF;
	}

	public static boolean animations() {
		return ui().animations.enabled();
	}
}
