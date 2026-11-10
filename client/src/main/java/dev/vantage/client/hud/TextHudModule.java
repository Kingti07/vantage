package dev.vantage.client.hud;

import dev.vantage.client.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphics;

/** Single-line "Label: value" element with optional brackets. */
public abstract class TextHudModule extends HudModule {
	public final BooleanSetting showLabel;
	public final BooleanSetting brackets;

	protected TextHudModule(String id, String name, String description, boolean defaultEnabled, HudAnchor anchor, float x, float y) {
		super(id, name, description, defaultEnabled, anchor, x, y);
		showLabel = add(new BooleanSetting("showLabel", "Show label", "Prefix the value with its name.", true));
		brackets = add(new BooleanSetting("brackets", "Brackets", "Wrap the text in [ ].", false));
	}

	protected abstract String label();

	/** The value text; {@code preview} is true in the HUD editor when no live data exists. */
	protected abstract String value(boolean preview);

	/** Text is rebuilt at most this often (20 times a second): readable, and far less work than once per frame. */
	private static final long REFRESH_NANOS = 50_000_000L;
	private String cachedLabel;
	private String cachedValue;
	private int cachedLabelWidth;
	private int cachedValueWidth;
	private boolean cachedPreview;
	private long cachedAt;

	/** Rebuilds the cached text when it is older than {@link #REFRESH_NANOS} or the preview mode changed. */
	private void refresh(boolean preview) {
		long now = System.nanoTime();
		if (cachedValue == null || preview != cachedPreview || now - cachedAt >= REFRESH_NANOS) {
			cachedLabel = labelPart();
			cachedValue = valuePart(preview);
			cachedLabelWidth = textWidth(cachedLabel);
			cachedValueWidth = textWidth(cachedValue);
			cachedPreview = preview;
			cachedAt = now;
		}
	}

	private String labelPart() {
		return (brackets.enabled() ? "[" : "") + (showLabel.enabled() ? label() + ": " : "");
	}

	private String valuePart(boolean preview) {
		return value(preview) + (brackets.enabled() ? "]" : "");
	}

	@Override
	public int contentWidth(boolean preview) {
		refresh(preview);
		return cachedLabelWidth + cachedValueWidth;
	}

	@Override
	public int contentHeight(boolean preview) {
		return 8;
	}

	@Override
	protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
		refresh(preview);
		drawText(g, cachedLabel, x, y, accent());
		drawText(g, cachedValue, x + cachedLabelWidth, y, text());
	}
}
