package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.SettingsPanel;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.SettingWidgets;
import dev.vantage.client.ui.widget.TextField;
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.client.util.Anim;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;

/**
 * Module manager: category sidebar, searchable grid of module cards with on/off switches, and a settings panel
 * for the selected module. Also links to the HUD editor, keybinds and the Modrinth mod browser.
 */
public final class ModulesScreen extends VScreen {
	private Category category;
	private String query = "";
	private Module selected;
	private ScrollPanel grid;
	private ScrollPanel settings;
	private TextField search;
	private final Anim panelAnim = new Anim(0, 14);

	public ModulesScreen(Screen parent) {
		this(parent, null);
	}

	public ModulesScreen(Screen parent, Category category) {
		super("Mods", parent);
		this.category = category;
	}

	private int sidebarWidth() {
		return 120;
	}

	private int panelWidth() {
		return Math.min(240, width / 3);
	}

	@Override
	protected void build() {
		// Sidebar: categories and shortcuts in a scroll panel so it works at any window height.
		ScrollPanel sidebar = add(new ScrollPanel(10, 42, sidebarWidth() - 10, height - 42 - 34));
		int bw = sidebarWidth() - 16;
		int y = 0;
		sidebar.add(new Button(0, y, bw, 18, () -> "All modules", category == null ? Button.Style.PRIMARY : Button.Style.SECONDARY, () -> select(null)));
		y += 21;
		for (Category c : Category.values()) {
			sidebar.add(new Button(0, y, bw, 18, () -> c.label, c == category ? Button.Style.PRIMARY : Button.Style.SECONDARY, () -> select(c)));
			y += 21;
		}
		y += 8;
		sidebar.add(new Button(0, y, bw, 18, () -> "HUD Editor", Button.Style.GHOST, () -> minecraft.setScreen(new HudEditorScreen(this))));
		y += 21;
		sidebar.add(new Button(0, y, bw, 18, () -> "Keybinds", Button.Style.GHOST, () -> minecraft.setScreen(new KeyboardScreen(this))));
		y += 21;
		sidebar.add(new Button(0, y, bw, 18, () -> "Modrinth", Button.Style.GHOST, () -> minecraft.setScreen(new ModBrowserScreen(this))));
		y += 21;
		sidebar.add(new Button(0, y, bw, 18, () -> "Installed mods", Button.Style.GHOST, () -> minecraft.setScreen(new InstalledModsScreen(this))));
		sidebar.setContentHeight(y + 20);
		add(new Button(10, height - 28, sidebarWidth() - 16, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));

		int gx = sidebarWidth() + 10;
		search = add(new TextField("Search modules…"));
		search.bounds(width - 10 - 180 - (selected != null ? panelWidth() + 10 : 0), 14, 180, 18);
		search.text(query).onChange(text -> {
			query = text;
			rebuildGrid();
		});
		int gridWidth = width - gx - 10 - (selected != null ? panelWidth() + 10 : 0);
		grid = add(new ScrollPanel(gx, 40, gridWidth, height - 50));
		settings = add(new ScrollPanel(width - panelWidth() - 10, 66, panelWidth(), height - 76));
		settings.visible = selected != null;
		rebuildGrid();
		rebuildSettings();
	}

	private void select(Category c) {
		category = c;
		rebuildWidgets();
	}

	private List<Module> filtered() {
		String q = query.toLowerCase(Locale.ROOT).trim();
		return VantageClient.get().modules().all().stream()
			.filter(m -> category == null || m.category() == category)
			.filter(m -> q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q))
			.toList();
	}

	private void rebuildGrid() {
		grid.clear();
		int cardWidth = 150;
		int cardHeight = 62;
		int gap = 8;
		int columns = Math.max(1, (grid.width - 6 + gap) / (cardWidth + gap));
		cardWidth = (grid.width - 6 - gap * (columns - 1)) / columns;
		int i = 0;
		for (Module module : filtered()) {
			int col = i % columns;
			int row = i / columns;
			grid.add(new ModuleCard(module, col * (cardWidth + gap), row * (cardHeight + gap), cardWidth, cardHeight));
			i++;
		}
		if (i == 0) {
			grid.decorate((g, off) -> Text.draw(g, "No modules match \"" + query + "\".", 4, 8, Theme.MUTED));
		}
	}

	private void rebuildSettings() {
		if (selected == null) {
			return;
		}
		SettingsPanel.build(settings, selected, this, this::rebuildSettings);
	}

	private void open(Module module) {
		boolean layoutChanges = (selected == null) != (module == null);
		selected = selected == module ? null : module;
		if (layoutChanges || selected == null) {
			rebuildWidgets();
		} else {
			settings.scrollToTop();
			rebuildSettings();
		}
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Render2D.rect(g, 0, 0, sidebarWidth(), height, Colors.withAlpha(Theme.panel(), 210));
		Text.drawScaled(g, "VANTAGE", 12, 14, 1.6f, Theme.TEXT, false, Text.uiFace());
		Text.draw(g, "MODULES", 12, 30, Theme.accent());
		String title = category == null ? "All modules" : category.label;
		Text.drawScaled(g, title, sidebarWidth() + 10, 14, 1.4f, Theme.TEXT, false, Text.uiFace());
		Text.draw(g, filtered().size() + " modules", sidebarWidth() + 10, 28, Theme.FAINT);
		if (selected != null) {
			float t = Theme.animations() ? panelAnim.target(1).get() : 1;
			int px = width - panelWidth() - 10 + Math.round((1 - t) * 20);
			Render2D.panel(g, px - 6, 10, panelWidth() + 12, height - 20, 8, Theme.panel(), Theme.border());
			Text.draw(g, selected.name(), px, 18, Theme.TEXT);
			List<String> lines = Text.wrap(selected.description(), panelWidth() - 4);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				Text.draw(g, lines.get(i), px, 30 + i * 10, Theme.MUTED);
			}
		} else {
			panelAnim.snap(0);
		}
	}

	/** A module tile: click to open its settings, switch to toggle. */
	private final class ModuleCard extends Widget {
		private final Module module;
		private final SettingWidgets.Toggle toggle;
		private final Anim hover = new Anim(0, 16);

		ModuleCard(Module module, int x, int y, int w, int h) {
			super(x, y, w, h);
			this.module = module;
			this.toggle = new SettingWidgets.Toggle(module::isEnabled, module::setEnabled);
			this.toggle.enabled = module.canToggle();
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean hovered = isHovered(mouseX, mouseY);
			float h = hover.target(hovered ? 1 : 0).get();
			boolean active = module == selected;
			int bg = Colors.lerp(Colors.withAlpha(Theme.panel(), 220), Theme.surfaceHover(), h);
			Render2D.rounded(g, x, y, width, height, 8, bg);
			Render2D.roundedBorder(g, x, y, width, height, 8, 1, active ? Theme.accent() : module.isEnabled() ? Colors.withAlpha(Theme.accent(), 90) : Theme.border());
			Text.draw(g, Text.ellipsize(module.name(), width - 50), x + 8, y + 8, Theme.TEXT);
			Text.draw(g, module.category().label.toUpperCase(Locale.ROOT), x + 8, y + 19, Colors.withAlpha(Theme.accent(), 220));
			List<String> lines = Text.wrap(module.description(), width - 16);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				String line = i == 1 && lines.size() > 2 ? Text.ellipsize(lines.get(i) + "…", width - 16) : lines.get(i);
				Text.draw(g, line, x + 8, y + 32 + i * 10, Theme.FAINT);
			}
			toggle.bounds(x + width - 36, y + 7, 28, 14);
			if (module.canToggle()) {
				toggle.render(g, mouseX, mouseY, delta);
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my)) {
				return false;
			}
			if (module.canToggle() && toggle.isHovered(mx, my)) {
				return toggle.mouseClicked(mx, my, button);
			}
			if (button == 1 && module.canToggle()) {
				module.toggle();
				return true;
			}
			open(module);
			return true;
		}
	}
}
