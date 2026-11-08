package dev.vantage.client.ui.screen;

import dev.vantage.client.VantageClient;
import dev.vantage.client.modrinth.ModService;
import dev.vantage.client.modrinth.RemoteTextures;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.Theme;
import dev.vantage.client.ui.VScreen;
import dev.vantage.client.ui.widget.Button;
import dev.vantage.client.ui.widget.ScrollPanel;
import dev.vantage.client.ui.widget.TextField;
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.client.util.Anim;
import dev.vantage.client.util.Colors;
import dev.vantage.common.modrinth.SearchQuery;
import dev.vantage.common.modrinth.model.ModrinthModels.Category;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchHit;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchResponse;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game Modrinth browser: search, loader / version / category filters, sorting, paging and one-click install
 * with dependency resolution. Only versions for this game's Minecraft version and Fabric can be installed.
 */
public final class ModBrowserScreen extends VScreen {
	private static final String[] LOADERS = {"fabric", "forge", "neoforge", "quilt", ""};
	private static final String[] LOADER_LABELS = {"Fabric", "Forge", "NeoForge", "Quilt", "Any loader"};

	private TextField search;
	private ScrollPanel results;
	private String query = "";
	private int loaderIndex;
	private boolean anyVersion;
	private SearchQuery.Sort sort = SearchQuery.Sort.RELEVANCE;
	private final List<String> categories = new ArrayList<>();
	private int categoryIndex = -1;
	private final List<SearchHit> hits = new ArrayList<>();
	private int total;
	private int generation;
	private String status = "";
	private long lastEdit;
	private boolean pendingSearch = true;

	public ModBrowserScreen(Screen parent) {
		super("Modrinth", parent);
	}

	private ModService mods() {
		return VantageClient.get().mods();
	}

	@Override
	protected void build() {
		search = add(new TextField("Search Modrinth…"));
		int bw = Math.max(64, Math.min(98, (width - 40 - 140) / 4 - 6));
		search.bounds(20, 40, width - 40 - 4 * (bw + 6), 18);
		search.text(query).onChange(text -> {
			query = text;
			lastEdit = System.currentTimeMillis();
			pendingSearch = true;
		});
		int fx = search.x + search.width + 6;
		add(new Button(fx, 40, bw, 18, () -> LOADER_LABELS[loaderIndex], Button.Style.SECONDARY, () -> {
			loaderIndex = (loaderIndex + 1) % LOADERS.length;
			research();
		}));
		add(new Button(fx + bw + 6, 40, bw, 18, () -> anyVersion ? "Any version" : mods().gameVersion(), Button.Style.SECONDARY, () -> {
			anyVersion = !anyVersion;
			research();
		}));
		add(new Button(fx + 2 * (bw + 6), 40, bw, 18, () -> sort.label, Button.Style.SECONDARY, () -> {
			sort = SearchQuery.Sort.values()[(sort.ordinal() + 1) % SearchQuery.Sort.values().length];
			research();
		}));
		add(new Button(fx + 3 * (bw + 6), 40, bw, 18, () -> categoryIndex < 0 ? "All categories" : pretty(categories.get(categoryIndex)), Button.Style.SECONDARY, () -> {
			if (categories.isEmpty()) {
				return;
			}
			categoryIndex = categoryIndex + 1 >= categories.size() ? -1 : categoryIndex + 1;
			research();
		}));
		results = add(new ScrollPanel(20, 66, width - 40, height - 102));
		add(new Button(20, height - 28, 110, 18, () -> "Installed mods", Button.Style.GHOST, () -> minecraft.setScreen(new InstalledModsScreen(this))));
		add(new Button(136, height - 28, 130, 18, () -> "Performance pack", Button.Style.GHOST, () -> minecraft.setScreen(new PerformancePackScreen(this))));
		add(new Button(width - 120, height - 28, 100, 18, () -> "Done", Button.Style.SECONDARY, this::onClose));
		if (categories.isEmpty()) {
			mods().run(() -> mods().api().categories(), list -> {
				for (Category category : list) {
					if ("mod".equals(category.projectType()) && "categories".equals(category.header())) {
						categories.add(category.name());
					}
				}
			}, error -> {
			});
		}
		renderResults();
	}

	private static String pretty(String name) {
		String spaced = name.replace('-', ' ');
		return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private void research() {
		pendingSearch = false;
		hits.clear();
		runSearch(0);
	}

	@Override
	public void tick() {
		super.tick();
		if (pendingSearch && System.currentTimeMillis() - lastEdit > 350) {
			research();
		}
	}

	private void runSearch(int offset) {
		int token = ++generation;
		SearchQuery q = new SearchQuery().query(query).sort(sort).page(offset, 20).clientSideOnly(true);
		if (!LOADERS[loaderIndex].isEmpty()) {
			q.loader(LOADERS[loaderIndex]);
		}
		if (!anyVersion) {
			q.gameVersion(mods().gameVersion());
		}
		if (categoryIndex >= 0) {
			q.category(categories.get(categoryIndex));
		}
		status = "Searching…";
		mods().run(() -> mods().api().search(q), (SearchResponse response) -> {
			if (token != generation) {
				return;
			}
			hits.addAll(response.hits());
			total = response.totalHits();
			status = String.format(Locale.ROOT, "%,d results", total);
			renderResults();
		}, error -> {
			if (token == generation) {
				status = "Search failed: " + ModService.message(error);
			}
		});
	}

	private void renderResults() {
		if (results == null) {
			return;
		}
		results.clear();
		int y = 0;
		int w = results.width - 8;
		for (SearchHit hit : hits) {
			results.add(new ResultCard(hit, 0, y, w, 52));
			y += 56;
		}
		if (hits.size() < total) {
			int offset = hits.size();
			results.add(new Button(w / 2 - 50, y + 4, 100, 18, () -> "Load more", Button.Style.GHOST, () -> runSearch(offset)));
			y += 28;
		}
		results.setContentHeight(y);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		header(g, 20, 10, "Modrinth", "Mods for Minecraft " + mods().gameVersion() + " · Fabric");
		Text.draw(g, status, width - 20 - Text.width(status), 26, Theme.FAINT);
		if (mods().restartRequired()) {
			String text = "Restart Minecraft to apply mod changes.";
			int w = Text.width(text) + 16;
			Render2D.rounded(g, (width - w) / 2, height - 30, w, 20, 6, Colors.withAlpha(Theme.WARNING, 60));
			Text.draw(g, text, (width - w) / 2 + 8, height - 24, Theme.WARNING);
		}
		if (hits.isEmpty() && !status.startsWith("Searching")) {
			Text.drawCentered(g, "No mods found for these filters.", width / 2, 100, Theme.MUTED);
		}
	}

	static String relative(String iso) {
		try {
			Duration ago = Duration.between(OffsetDateTime.parse(iso).toInstant(), Instant.now());
			long days = ago.toDays();
			if (days >= 365) {
				return days / 365 + "y ago";
			}
			if (days >= 30) {
				return days / 30 + "mo ago";
			}
			return days >= 1 ? days + "d ago" : "today";
		} catch (RuntimeException e) {
			return "";
		}
	}

	static String compact(long value) {
		if (value >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", value / 1e6);
		}
		if (value >= 1_000) {
			return String.format(Locale.ROOT, "%.1fK", value / 1e3);
		}
		return Long.toString(value);
	}

	private final class ResultCard extends Widget {
		private final SearchHit hit;
		private final Button install;
		private final boolean compatible;
		private final Anim hover = new Anim(0, 16);

		ResultCard(SearchHit hit, int x, int y, int w, int h) {
			super(x, y, w, h);
			this.hit = hit;
			compatible = hit.categories() != null && hit.categories().contains("fabric")
				&& hit.versions() != null && hit.versions().contains(mods().gameVersion());
			install = new Button(x + w - 76, y + 17, 68, 18, () -> compatible ? "Install" : "N/A", Button.Style.PRIMARY,
				() -> InstallPopup.open(ModBrowserScreen.this, hit.projectId(), null, hit.title(), null));
			install.enabled = compatible;
			install.tooltip = compatible ? null : "No version for Fabric " + mods().gameVersion();
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean hovered = isHovered(mouseX, mouseY);
			float h = hover.target(hovered ? 1 : 0).get();
			Render2D.rounded(g, x, y, width, height, 8, Colors.lerp(Colors.withAlpha(Theme.panel(), 220), Theme.surfaceHover(), h));
			RemoteTextures.draw(g, hit.iconUrl(), x + 8, y + 8, 36, 36, 96);
			int tx = x + 52;
			int textWidth = width - 52 - 90;
			Text.draw(g, Text.ellipsize(hit.title(), textWidth / 2), tx, y + 7, Theme.TEXT);
			Text.draw(g, Text.ellipsize("by " + hit.author(), textWidth / 2), tx + Math.min(Text.width(hit.title()), textWidth / 2) + 6, y + 7, Theme.FAINT);
			Text.draw(g, Text.ellipsize(hit.description(), textWidth), tx, y + 19, Theme.MUTED);
			String stats = "⬇ " + compact(hit.downloads()) + "   ♥ " + compact(hit.follows()) + "   ⟳ " + relative(hit.dateModified());
			Text.draw(g, stats, tx, y + 33, Theme.FAINT);
			String badge = compatible ? "Compatible" : (hit.categories() != null && hit.categories().contains("fabric") ? "No " + mods().gameVersion() + " version" : "Not for Fabric");
			Text.draw(g, badge, x + width - 8 - Text.width(badge), y + 40, compatible ? Theme.SUCCESS : Theme.WARNING);
			install.bounds(x + width - 76, y + 8, 68, 18);
			install.render(g, mouseX, mouseY, delta);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!isHovered(mx, my)) {
				return false;
			}
			if (install.mouseClicked(mx, my, button)) {
				return true;
			}
			minecraft.setScreen(new ProjectScreen(ModBrowserScreen.this, hit.projectId(), hit.title()));
			return true;
		}
	}
}
