package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.KeybindWidget;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.TextField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searchable list of every Vantage keybind. Each bind accepts multiple keys and mouse buttons; conflicts with
 * other Vantage binds and with Minecraft/mod key mappings are listed under the bind.
 */
public final class KeybindsScreen extends VScreen {
	private ScrollPanel list;
	private String query = "";

	public KeybindsScreen(Screen parent) {
		super("Keybinds", parent);
	}

	@Override
	protected void build() {
		TextField search = add(new TextField("Search keybinds…"));
		search.bounds(width - 220, 16, 200, 18);
		search.text(query).onChange(text -> {
			query = text;
			fill();
		});
		list = add(new ScrollPanel(20, 46, width - 40, height - 82));
		add(new Button(20, height - 28, 120, 18, () -> "Reset all", Button.Style.DANGER, () -> {
			VantageClient.get().keybinds().all().forEach(Keybind::reset);
			fill();
		}));
		add(new Button(width - 120, height - 28, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
		fill();
	}

	private void fill() {
		list.clear();
		String q = query.toLowerCase(Locale.ROOT).trim();
		int y = 2;
		int rowWidth = list.width - 10;
		String lastGroup = null;
		for (Keybind bind : VantageClient.get().keybinds().all()) {
			if (!q.isEmpty() && !bind.name().toLowerCase(Locale.ROOT).contains(q) && !bind.group().toLowerCase(Locale.ROOT).contains(q)
				&& !bind.describe().toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			if (!bind.group().equals(lastGroup)) {
				String group = bind.group().toUpperCase(Locale.ROOT);
				int gy = y + 4;
				list.decorate((g, off) -> Text.draw(g, group, 2, gy, Theme.accent()));
				y += 16;
				lastGroup = bind.group();
			}
			List<String> conflicts = new ArrayList<>();
			for (InputKey key : bind.keys()) {
				for (String other : VantageClient.get().keybinds().conflicts(bind, key)) {
					conflicts.add(key.displayName() + " → " + other);
				}
			}
			int rowY = y;
			int height = conflicts.isEmpty() ? 24 : 34;
			String name = bind.name();
			String conflictText = conflicts.isEmpty() ? null : "Also used by: " + String.join(", ", conflicts);
			list.decorate((g, off) -> {
				Render2D.rounded(g, 0, rowY, rowWidth, height - 2, 5, Theme.surface());
				Text.draw(g, name, 8, rowY + 7, Theme.TEXT);
				if (conflictText != null) {
					Text.draw(g, Text.ellipsize(conflictText, rowWidth - 260), 8, rowY + 19, Theme.WARNING);
				}
			});
			KeybindWidget widget = new KeybindWidget(bind);
			widget.bounds(rowWidth - 230, rowY + 4, 160, 16);
			list.add(widget);
			Button reset = new Button(rowWidth - 64, rowY + 4, 56, 16, () -> "Reset", Button.Style.GHOST, () -> {
				bind.reset();
				fill();
			});
			reset.enabled = !bind.isDefault();
			list.add(reset);
			y += height + 2;
		}
		list.setContentHeight(y + 4);
	}

	private boolean wasCapturing;

	@Override
	public void tick() {
		super.tick();
		// Refresh conflict hints once a key has been captured.
		boolean capturing = VantageClient.get().keybinds().isCapturing();
		if (wasCapturing && !capturing) {
			fill();
		}
		wasCapturing = capturing;
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, 12, "Keybinds", "Left click a bind to add a key or mouse button · right click to clear · multiple keys allowed");
	}

	@Override
	public void removed() {
		VantageClient.get().keybinds().capture(null);
	}
}
