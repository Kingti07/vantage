package dev.vantage.client.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vantage.client.VantageClient;
import dev.vantage.client.keybind.InputKey;
import dev.vantage.client.keybind.KeyboardLayout;
import dev.vantage.client.keybind.Keybind;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.TextField;
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.client.util.Colors;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keybinds on a picture of the keyboard. Every key shows whether something is bound to it (one action: accent
 * colour, several: warning colour); clicking a key, or pressing it, selects it. The panel below lists what the key
 * does and every action of Vantage, Minecraft and installed mods, which can be put on the key with one click.
 */
public final class KeyboardScreen extends VScreen {
	/** One action: a Vantage keybind (any number of keys) or a Minecraft / mod key mapping (one key). */
	private record Binding(String name, String group, @Nullable Keybind vantage, @Nullable KeyMapping vanilla) {
		boolean isVantage() {
			return vantage != null;
		}

		String keysText() {
			if (vantage != null) {
				return vantage.describe();
			}
			return vanilla.isUnbound() ? "Not bound" : vanilla.getTranslatedKeyMessage().getString();
		}
	}

	private enum Filter {
		ALL("All actions"),
		VANTAGE("Vantage"),
		MINECRAFT("Minecraft & mods");

		private final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	private final List<Binding> bindings = new ArrayList<>();
	private final Map<InputKey, List<Binding>> byKey = new HashMap<>();
	private InputKey selected;
	private String query = "";
	private Filter filter = Filter.ALL;
	private KeyboardWidget keyboard;
	private ScrollPanel assigned;
	private ScrollPanel actions;
	private TextField search;

	public KeyboardScreen(Screen parent) {
		super("Keyboard", parent);
	}

	@Override
	protected void build() {
		int area = width - 40;
		boolean numpad = area / KeyboardLayout.WIDTH_FULL >= 15;
		float layoutWidth = numpad ? KeyboardLayout.WIDTH_FULL : KeyboardLayout.WIDTH_COMPACT;
		// Header, footer and at least ~70 px for the panels below the keyboard; the keyboard gets the rest.
		int keyboardTop = compactHeader() ? 32 : 44;
		int available = height - keyboardTop - 30 - 74;
		int unit = (int) Math.max(10, Math.min(area / layoutWidth, available / KeyboardLayout.HEIGHT));
		int keyboardWidth = (int) (layoutWidth * unit);
		keyboard = add(new KeyboardWidget((width - keyboardWidth) / 2, keyboardTop, unit, numpad));

		int top = keyboard.y + keyboard.height + 4;
		int panelHeight = Math.max(50, height - top - 30);
		int leftWidth = Math.max(130, (int) (area * 0.36f));
		assigned = add(new ScrollPanel(20, top + 14, leftWidth, panelHeight - 14));
		int rightX = 20 + leftWidth + 10;
		int rightWidth = width - 20 - rightX;
		search = add(new TextField("Search actions…"));
		search.bounds(rightX, top, rightWidth - 110, 14);
		search.text(query).onChange(text -> {
			query = text;
			fillActions();
		});
		add(new Button(rightX + rightWidth - 104, top, 104, 14, () -> filter.label, Button.Style.GHOST, () -> {
			filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
			fillActions();
		}));
		actions = add(new ScrollPanel(rightX, top + 18, rightWidth, panelHeight - 18));

		add(new Button(20, height - 26, 90, 18, () -> "List view", Button.Style.GHOST, () -> minecraft.setScreen(new KeybindsScreen(this))));
		add(new Button(116, height - 26, 90, 18, () -> "Reset all", Button.Style.DANGER, this::resetAll));
		add(new Button(width - 120, height - 26, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
		refresh();
	}

	// ------------------------------------------------------------------------------------------------ data

	private void refresh() {
		bindings.clear();
		byKey.clear();
		for (Keybind bind : VantageClient.get().keybinds().all()) {
			Binding binding = new Binding(bind.name(), "Vantage · " + bind.group(), bind, null);
			bindings.add(binding);
			for (InputKey key : bind.keys()) {
				byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(binding);
			}
		}
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			Binding binding = new Binding(Component.translatable(mapping.getName()).getString(), mapping.getCategory().label().getString(), null, mapping);
			bindings.add(binding);
			InputKey key = toInputKey(KeyBindingHelper.getBoundKeyOf(mapping));
			if (key != null) {
				byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(binding);
			}
		}
		fillAssigned();
		fillActions();
	}

	private static @Nullable InputKey toInputKey(InputConstants.Key key) {
		if (key == null || key.getValue() == InputConstants.UNKNOWN.getValue()) {
			return null;
		}
		return switch (key.getType()) {
			case KEYSYM -> InputKey.key(key.getValue());
			case MOUSE -> InputKey.mouse(key.getValue());
			default -> null;
		};
	}

	private List<Binding> on(InputKey key) {
		return key == null ? List.of() : byKey.getOrDefault(key, List.of());
	}

	private void select(InputKey key) {
		selected = key;
		fillAssigned();
		fillActions();
	}

	private void bind(Binding binding) {
		if (selected == null) {
			return;
		}
		if (binding.vantage() != null) {
			binding.vantage().add(selected);
		} else {
			binding.vanilla().setKey(selected.toVanilla());
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		toast(binding.name() + " → " + selected.displayName());
		refresh();
	}

	private void unbind(Binding binding) {
		if (binding.vantage() != null) {
			binding.vantage().remove(selected);
		} else {
			binding.vanilla().setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		refresh();
	}

	private void resetAll() {
		VantageClient.get().keybinds().all().forEach(Keybind::reset);
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			mapping.setKey(mapping.getDefaultKey());
		}
		KeyMapping.resetMapping();
		minecraft.options.save();
		toast("All Vantage and Minecraft keys are back to their defaults.");
		refresh();
	}

	// ------------------------------------------------------------------------------------------------ panels

	private void fillAssigned() {
		assigned.clear();
		int rowWidth = assigned.width - 8;
		if (selected == null) {
			assigned.decorate((g, off) -> {
				for (int i = 0; i < 3; i++) {
					String line = switch (i) {
						case 0 -> "Click a key on the keyboard";
						case 1 -> "or press it to select it.";
						default -> "Then pick an action on the right.";
					};
					Text.draw(g, Text.ellipsize(line, rowWidth), 2, 4 + i * 11, Theme.MUTED);
				}
			});
			assigned.setContentHeight(40);
			return;
		}
		List<Binding> current = on(selected);
		if (current.isEmpty()) {
			assigned.decorate((g, off) -> {
				Text.draw(g, "Nothing is bound to this key.", 2, 4, Theme.MUTED);
				Text.draw(g, Text.ellipsize("Pick an action on the right.", rowWidth), 2, 15, Theme.FAINT);
			});
			assigned.setContentHeight(30);
			return;
		}
		int y = 2;
		for (Binding binding : current) {
			int rowY = y;
			assigned.decorate((g, off) -> {
				Render2D.rounded(g, 0, rowY, rowWidth, 24, 4, Theme.surface());
				Text.draw(g, Text.ellipsize(binding.name(), rowWidth - 60), 5, rowY + 3, Theme.TEXT);
				Text.draw(g, Text.ellipsize(binding.group(), rowWidth - 60), 5, rowY + 13, Theme.FAINT);
			});
			assigned.add(new Button(rowWidth - 52, rowY + 4, 48, 16, () -> "Unbind", Button.Style.GHOST, () -> unbind(binding)));
			y += 26;
		}
		if (current.size() > 1) {
			int warnY = y + 2;
			assigned.decorate((g, off) -> Text.draw(g, Text.ellipsize("Pressing this key triggers all of them.", rowWidth), 2, warnY, Theme.WARNING));
			y += 14;
		}
		assigned.setContentHeight(y + 4);
	}

	private void fillActions() {
		actions.clear();
		String q = query.toLowerCase(Locale.ROOT).trim();
		int rowWidth = actions.width - 8;
		int y = 2;
		for (Binding binding : bindings) {
			if (filter == Filter.VANTAGE && !binding.isVantage() || filter == Filter.MINECRAFT && binding.isVantage()) {
				continue;
			}
			if (!q.isEmpty() && !binding.name().toLowerCase(Locale.ROOT).contains(q) && !binding.group().toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			boolean onSelected = on(selected).contains(binding);
			int rowY = y;
			int keysWidth = Math.min(110, rowWidth / 3);
			actions.decorate((g, off) -> {
				Render2D.rounded(g, 0, rowY, rowWidth, 22, 4, onSelected ? Colors.withAlpha(Theme.accent(), 70) : Theme.surface());
				Text.draw(g, Text.ellipsize(binding.name(), rowWidth - keysWidth - 60), 5, rowY + 2, Theme.TEXT);
				Text.draw(g, Text.ellipsize(binding.group(), rowWidth - keysWidth - 60), 5, rowY + 12, Theme.FAINT);
				Text.draw(g, Text.ellipsize(binding.keysText(), keysWidth), rowWidth - keysWidth - 54, rowY + 7, Theme.MUTED);
			});
			String label = onSelected ? "Bound" : binding.isVantage() ? "Add" : "Set";
			Button button = actions.add(new Button(rowWidth - 48, rowY + 3, 44, 16, () -> label, Button.Style.PRIMARY, () -> bind(binding)));
			button.enabled = selected != null && !onSelected;
			button.tooltip = binding.isVantage() ? "Adds the key; the action keeps its other keys." : "Replaces the action's key.";
			y += 24;
		}
		actions.setContentHeight(y + 4);
	}

	// ------------------------------------------------------------------------------------------------ input

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search != null && search.isFocused() || event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_UNKNOWN) {
			return super.keyPressed(event);
		}
		select(InputKey.key(event.key()));
		return true;
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, compactHeader() ? 8 : 12, "Keyboard", compactHeader() ? null
			: "Click or press a key, then choose what it should do · Vantage, Minecraft and mod actions");
		int top = keyboard.y + keyboard.height + 4;
		String title = selected == null ? "NO KEY SELECTED" : "KEY: " + selected.displayName().toUpperCase(Locale.ROOT);
		Text.draw(g, Text.ellipsize(title, assigned.width), 20, top + 3, selected == null ? Theme.FAINT : Theme.accent());
		// Legend in the footer, between the left and right buttons.
		int legendWidth = Text.width("free") + Text.width("one action") + Text.width("several") + 3 * 18;
		int lx = Math.max(212 + legendWidth, (width + legendWidth) / 2);
		if (lx < width - 126) {
			int ly = height - 21;
			lx = legend(g, lx, ly, Colors.withAlpha(Theme.WARNING, 170), "several");
			lx = legend(g, lx, ly, Colors.withAlpha(Theme.accent(), 150), "one action");
			legend(g, lx, ly, Theme.surface(), "free");
		}
	}

	/** Small windows (or a large GUI scale) drop the subtitle to leave room for the keyboard. */
	private boolean compactHeader() {
		return height < 300;
	}

	private static int legend(GuiGraphics g, int right, int y, int color, String text) {
		int w = Text.width(text);
		int x = right - w;
		Text.draw(g, text, x, y, Theme.MUTED);
		Render2D.rounded(g, x - 11, y, 8, 8, 2, color);
		return x - 18;
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float delta) {
		KeyboardLayout.Cap cap = keyboard.capAt(mouseX, mouseY);
		if (cap == null) {
			return;
		}
		List<Binding> current = on(cap.key());
		List<String> lines = new ArrayList<>();
		lines.add(cap.key().displayName());
		if (current.isEmpty()) {
			lines.add("Free");
		} else {
			current.forEach(b -> lines.add("• " + b.name()));
		}
		int w = lines.stream().mapToInt(Text::width).max().orElse(40) + 10;
		int h = lines.size() * 11 + 6;
		int x = Math.min(mouseX + 10, width - w - 4);
		int y = Math.min(mouseY + 10, height - h - 4);
		g.nextStratum();
		Render2D.panel(g, x, y, w, h, 4, 0xF0181C25, Theme.border());
		for (int i = 0; i < lines.size(); i++) {
			Text.draw(g, lines.get(i), x + 5, y + 4 + i * 11, i == 0 ? Theme.accent() : Theme.TEXT);
		}
	}

	/** Draws the keyboard and selects keys on click. */
	private final class KeyboardWidget extends Widget {
		private final int unit;
		private final List<KeyboardLayout.Cap> caps;

		KeyboardWidget(int x, int y, int unit, boolean numpad) {
			super(x, y, (int) ((numpad ? KeyboardLayout.WIDTH_FULL : KeyboardLayout.WIDTH_COMPACT) * unit), (int) (KeyboardLayout.HEIGHT * unit));
			this.unit = unit;
			this.caps = KeyboardLayout.caps(numpad);
		}

		KeyboardLayout.@Nullable Cap capAt(double mx, double my) {
			for (KeyboardLayout.Cap cap : caps) {
				int px = x + (int) (cap.x() * unit);
				int py = y + (int) (cap.y() * unit);
				if (Render2D.inside(mx, my, px, py, (int) (cap.w() * unit) - 2, (int) (cap.h() * unit) - 2)) {
					return cap;
				}
			}
			return null;
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			KeyboardLayout.Cap hovered = capAt(mouseX, mouseY);
			Text.draw(g, "MOUSE", x, y + (int) (6.55f * unit), Theme.FAINT);
			for (KeyboardLayout.Cap cap : caps) {
				int px = x + (int) (cap.x() * unit);
				int py = y + (int) (cap.y() * unit);
				int pw = (int) (cap.w() * unit) - 2;
				int ph = (int) (cap.h() * unit) - 2;
				int count = on(cap.key()).size();
				int color = count == 0 ? Theme.surface() : count == 1 ? Colors.withAlpha(Theme.accent(), 150) : Colors.withAlpha(Theme.WARNING, 170);
				if (cap == hovered) {
					color = count == 0 ? Theme.surfaceHover() : Colors.shade(color, 0.15f);
				}
				Render2D.rounded(g, px, py, pw, ph, 3, color);
				if (cap.key().equals(selected)) {
					Render2D.roundedBorder(g, px, py, pw, ph, 3, 1, Theme.TEXT);
				} else if (cap.key().isDown()) {
					Render2D.roundedBorder(g, px, py, pw, ph, 3, 1, Theme.secondary());
				}
				String label = cap.label() != null ? cap.label() : cap.key().displayName();
				int textWidth = Text.width(label);
				float scale = textWidth > pw - 3 ? Math.max(0.5f, (pw - 3) / (float) textWidth) : 1f;
				float tx = px + (pw - textWidth * scale) / 2f;
				float ty = py + (ph - 8 * scale) / 2f;
				Text.drawScaled(g, label, tx, ty, scale, count == 0 ? Theme.MUTED : Theme.TEXT, false, Text.uiFace());
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			KeyboardLayout.Cap cap = capAt(mx, my);
			if (cap == null) {
				return false;
			}
			select(cap.key());
			return true;
		}
	}
}
