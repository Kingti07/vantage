package dev.vantage.client.util;

/**
 * Frame-rate independent smoothing: the value approaches its target exponentially, so animations look the
 * same at 30 and 300 FPS.
 */
public final class Anim {
	private float value;
	private float target;
	private final float speed;
	private long lastNanos = System.nanoTime();

	public Anim(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public Anim target(float target) {
		this.target = target;
		return this;
	}

	public float get() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		value += (target - value) * (1 - (float) Math.exp(-dt * speed));
		if (Math.abs(target - value) < 0.0005f) {
			value = target;
		}
		return value;
	}

	public void snap(float value) {
		this.value = value;
		this.target = value;
	}

	public float target() {
		return target;
	}

	public static float easeOutCubic(float t) {
		float clamped = Math.max(0, Math.min(1, t));
		return 1 - (float) Math.pow(1 - clamped, 3);
	}
}
