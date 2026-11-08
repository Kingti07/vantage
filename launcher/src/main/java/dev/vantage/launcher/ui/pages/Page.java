package dev.vantage.launcher.ui.pages;

import javafx.scene.Node;

/** A sidebar destination. Views are built once and refreshed in {@link #onShow()}. */
public interface Page {
	Node view();

	/** Called every time the page becomes visible. */
	default void onShow() {
	}
}
