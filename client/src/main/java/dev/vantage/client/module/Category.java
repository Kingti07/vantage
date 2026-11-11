package dev.vantage.client.module;

public enum Category {
	HUD("HUD"),
	PVP("PvP"),
	CAMERA("Camera"),
	VISUAL("Visual"),
	PERFORMANCE("Performance"),
	UTILITY("Utility"),
	INTERFACE("Interface");

	public final String label;

	Category(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}
