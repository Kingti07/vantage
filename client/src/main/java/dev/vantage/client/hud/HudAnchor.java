package dev.vantage.client.hud;

/**
 * Screen anchor of a HUD element. The element's matching corner/edge is pinned to the anchor, so layouts stay
 * correct when the window size or GUI scale changes.
 */
public enum HudAnchor {
	TOP_LEFT(0, 0), TOP_CENTER(0.5f, 0), TOP_RIGHT(1, 0),
	MIDDLE_LEFT(0, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1, 0.5f),
	BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5f, 1), BOTTOM_RIGHT(1, 1);

	public final float fx;
	public final float fy;

	HudAnchor(float fx, float fy) {
		this.fx = fx;
		this.fy = fy;
	}

	/** Anchor of the screen third that contains the given point. */
	public static HudAnchor nearest(float x, float y, float screenWidth, float screenHeight) {
		int col = x < screenWidth / 3f ? 0 : x > screenWidth * 2f / 3f ? 2 : 1;
		int row = y < screenHeight / 3f ? 0 : y > screenHeight * 2f / 3f ? 2 : 1;
		return values()[row * 3 + col];
	}
}
