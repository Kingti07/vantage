package dev.vantage.client.hud;

import com.google.gson.JsonObject;
import dev.vantage.client.config.ConfigManager;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A movable, scalable and styleable HUD element. Position is stored relative to a {@link HudAnchor}; the
 * shared style settings (colours, background, border, font, padding, transparency) are available on every
 * element and edited live in the HUD editor.
 */
public abstract class HudModule extends Module {
	public enum Align {LEFT, CENTER, RIGHT}

	private final HudAnchor defaultAnchor;
	private final float defaultX;
	private final float defaultY;
	private HudAnchor anchor;
	private float offsetX;
	private float offsetY;
	private float scale = 1f;

	public final ColorSetting textColor;
	public final ColorSetting accentColor;
	public final BooleanSetting background;
	public final ColorSetting backgroundColor;
	public final BooleanSetting border;
	public final ColorSetting borderColor;
	public final NumberSetting cornerRadius;
	public final NumberSetting padding;
	public final BooleanSetting textShadow;
	public final EnumSetting<Text.Face> font;
	public final NumberSetting opacity;

	protected HudModule(String id, String name, String description, boolean defaultEnabled, HudAnchor anchor, float x, float y) {
		super(id, name, description, Category.HUD, defaultEnabled);
		this.defaultAnchor = anchor;
		this.defaultX = x;
		this.defaultY = y;
		this.anchor = anchor;
		this.offsetX = x;
		this.offsetY = y;
		textColor = add(new ColorSetting("textColor", "Text colour", "Main text colour.", 0xFFFFFFFF));
		accentColor = add(new ColorSetting("accentColor", "Accent colour", "Labels, highlights and bars.", 0xFFA48BFF));
		background = add(new BooleanSetting("background", "Background", "Draw a panel behind the element.", true));
		backgroundColor = add(new ColorSetting("backgroundColor", "Background colour", "Panel colour; alpha controls transparency.", 0x8C0B0D12, false));
		backgroundColor.visibleWhen(background::enabled);
		border = add(new BooleanSetting("border", "Border", "Outline around the panel.", false));
		borderColor = add(new ColorSetting("borderColor", "Border colour", "Outline colour.", 0x667C5CFF));
		borderColor.visibleWhen(border::enabled);
		cornerRadius = add(new NumberSetting("cornerRadius", "Corner radius", "Rounded corners of the panel.", 3, 0, 8, 1, "px"));
		padding = add(new NumberSetting("padding", "Padding", "Space between text and panel edge.", 4, 0, 10, 1, "px"));
		textShadow = add(new BooleanSetting("textShadow", "Text shadow", "Drop shadow under text.", false));
		font = add(new EnumSetting<>("font", "Font", "Font used by this element.", Text.Face.DEFAULT));
		opacity = add(new NumberSetting("opacity", "Opacity", "Overall transparency of the element.", 1.0, 0.1, 1.0, 0.05));
	}

	/** Content size without padding, in unscaled GUI pixels. */
	public abstract int contentWidth(boolean preview);

	public abstract int contentHeight(boolean preview);

	/** Draws the content with its top-left corner at (x, y), in unscaled GUI pixels. */
	protected abstract void renderContent(GuiGraphics g, int x, int y, boolean preview);

	/** Whether there is anything to show right now (e.g. Target HUD without a target). Ignored in the editor. */
	public boolean hasContent() {
		return true;
	}

	public int padding() {
		return padding.intValue();
	}

	public int width(boolean preview) {
		return contentWidth(preview) + padding() * 2;
	}

	public int height(boolean preview) {
		return contentHeight(preview) + padding() * 2;
	}

	public float scale() {
		return scale;
	}

	public void setScale(float value) {
		float clamped = Math.max(0.4f, Math.min(4f, value));
		if (clamped != scale) {
			scale = clamped;
			ConfigManager.markDirty();
		}
	}

	/** Top-left corner on screen in GUI pixels. */
	public float screenX(int screenWidth, boolean preview) {
		return screenWidth * anchor.fx + offsetX - width(preview) * scale * anchor.fx;
	}

	public float screenY(int screenHeight, boolean preview) {
		return screenHeight * anchor.fy + offsetY - height(preview) * scale * anchor.fy;
	}

	/** Moves the element so its top-left corner is at (x, y), re-anchoring to the closest screen region. */
	public void setScreenPosition(float x, float y, int screenWidth, int screenHeight, boolean preview) {
		float w = width(preview) * scale;
		float h = height(preview) * scale;
		HudAnchor newAnchor = HudAnchor.nearest(x + w / 2, y + h / 2, screenWidth, screenHeight);
		anchor = newAnchor;
		offsetX = x - (screenWidth * newAnchor.fx - w * newAnchor.fx);
		offsetY = y - (screenHeight * newAnchor.fy - h * newAnchor.fy);
		ConfigManager.markDirty();
	}

	public void resetLayout() {
		anchor = defaultAnchor;
		offsetX = defaultX;
		offsetY = defaultY;
		scale = 1f;
		ConfigManager.markDirty();
	}

	public void render(GuiGraphics g, int screenWidth, int screenHeight, boolean preview) {
		float x = screenX(screenWidth, preview);
		float y = screenY(screenHeight, preview);
		int w = width(preview);
		int h = height(preview);
		g.pose().pushMatrix();
		g.pose().translate(Math.round(x * 2) / 2f, Math.round(y * 2) / 2f);
		g.pose().scale(scale, scale);
		float alpha = opacity.floatValue();
		if (background.enabled()) {
			Render2D.rounded(g, 0, 0, w, h, cornerRadius.intValue(), fade(backgroundColor.color(), alpha));
		}
		if (border.enabled()) {
			Render2D.roundedBorder(g, 0, 0, w, h, cornerRadius.intValue(), 1, fade(borderColor.color(chromaOffset()), alpha));
		}
		renderContent(g, padding(), padding(), preview);
		g.pose().popMatrix();
	}

	protected float chromaOffset() {
		return (id().hashCode() & 0xFF) / 255f;
	}

	protected int text() {
		return fade(textColor.color(chromaOffset()), opacity.floatValue());
	}

	protected int accent() {
		return fade(accentColor.color(chromaOffset()), opacity.floatValue());
	}

	protected static int fade(int argb, float alpha) {
		return dev.vantage.client.util.Colors.fade(argb, alpha);
	}

	protected void drawText(GuiGraphics g, String text, int x, int y, int color) {
		Text.draw(g, text, x, y, color, textShadow.enabled(), font.get());
	}

	protected int textWidth(String text) {
		return Text.width(text, font.get());
	}

	@Override
	protected void saveExtra(JsonObject json) {
		JsonObject layout = new JsonObject();
		layout.addProperty("anchor", anchor.name());
		layout.addProperty("x", offsetX);
		layout.addProperty("y", offsetY);
		layout.addProperty("scale", scale);
		json.add("layout", layout);
	}

	@Override
	protected void loadExtra(JsonObject json) {
		if (json.has("layout") && json.get("layout").isJsonObject()) {
			JsonObject layout = json.getAsJsonObject("layout");
			try {
				anchor = HudAnchor.valueOf(layout.get("anchor").getAsString());
				offsetX = layout.get("x").getAsFloat();
				offsetY = layout.get("y").getAsFloat();
				scale = Math.max(0.4f, Math.min(4f, layout.get("scale").getAsFloat()));
				return;
			} catch (RuntimeException ignored) {
				// fall back to defaults
			}
		}
		anchor = defaultAnchor;
		offsetX = defaultX;
		offsetY = defaultY;
		scale = 1f;
	}
}
