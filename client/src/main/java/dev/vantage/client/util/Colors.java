package dev.vantage.client.util;

/** ARGB colour helpers. */
public final class Colors {
	private Colors() {
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	public static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	public static int green(int argb) {
		return (argb >> 8) & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	public static int withAlpha(int argb, int alpha) {
		return (clamp(alpha) << 24) | (argb & 0xFFFFFF);
	}

	/** Multiplies the existing alpha by {@code factor} (0..1). */
	public static int fade(int argb, float factor) {
		return withAlpha(argb, Math.round(alpha(argb) * Math.max(0, Math.min(1, factor))));
	}

	public static int lerp(int from, int to, float t) {
		t = Math.max(0, Math.min(1, t));
		return argb(
			Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
			Math.round(red(from) + (red(to) - red(from)) * t),
			Math.round(green(from) + (green(to) - green(from)) * t),
			Math.round(blue(from) + (blue(to) - blue(from)) * t));
	}

	/** Lightens (positive amount) or darkens (negative) a colour, keeping alpha. */
	public static int shade(int argb, float amount) {
		int target = amount >= 0 ? 0xFFFFFFFF : 0xFF000000;
		int shaded = lerp(argb, target, Math.abs(amount));
		return withAlpha(shaded, alpha(argb));
	}

	/** Rainbow colour cycling over time; {@code offset} shifts the phase (e.g. per character or element). */
	public static int chroma(float speed, float offset, int alpha) {
		float hue = ((System.currentTimeMillis() % 100_000L) / 1000f * speed + offset) % 1f;
		return withAlpha(java.awt.Color.HSBtoRGB(hue, 0.75f, 1f), alpha);
	}

	public static int hsv(float hue, float saturation, float value, int alpha) {
		return withAlpha(java.awt.Color.HSBtoRGB(hue, saturation, value), alpha);
	}

	public static float[] toHsv(int argb) {
		return java.awt.Color.RGBtoHSB(red(argb), green(argb), blue(argb), null);
	}

	public static String toHex(int argb) {
		return String.format(java.util.Locale.ROOT, "#%08X", argb);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
