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

	private String labelPart() {
		return (brackets.enabled() ? "[" : "") + (showLabel.enabled() ? label() + ": " : "");
	}

	private String valuePart(boolean preview) {
		return value(preview) + (brackets.enabled() ? "]" : "");
	}

	@Override
	public int contentWidth(boolean preview) {
		return textWidth(labelPart()) + textWidth(valuePart(preview));
	}

	@Override
	public int contentHeight(boolean preview) {
		return 8;
	}

	@Override
	protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
		String label = labelPart();
		drawText(g, label, x, y, accent());
		drawText(g, valuePart(preview), x + textWidth(label), y, text());
	}
}
