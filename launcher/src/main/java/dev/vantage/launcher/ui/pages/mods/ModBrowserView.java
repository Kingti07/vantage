package dev.vantage.launcher.ui.pages.mods;

import dev.vantage.common.modrinth.SearchQuery;
import dev.vantage.common.modrinth.model.ModrinthModels.Category;
import dev.vantage.common.modrinth.model.ModrinthModels.GameVersionTag;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchHit;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchResponse;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.ToggleSwitch;
import dev.vantage.launcher.ui.Ui;
import dev.vantage.launcher.ui.WebImages;
import dev.vantage.launcher.ui.pages.ModsPage;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modrinth search with loader / game version / category filters and sorting. Results show compatibility with
 * the selected installation; only compatible mods can be installed.
 */
public final class ModBrowserView {
	private static final String ANY_VERSION = "Any version";
	private static final List<String> LOADERS = List.of("Fabric", "Forge", "NeoForge", "Quilt", "Any loader");

	private final ModsPage page;
	private final VBox root = new VBox(12);
	private final TextField search = new TextField();
	private final ComboBox<String> loader = new ComboBox<>(FXCollections.observableArrayList(LOADERS));
	private final ComboBox<String> version = new ComboBox<>();
	private final ComboBox<SearchQuery.Sort> sort = new ComboBox<>(FXCollections.observableArrayList(SearchQuery.Sort.values()));
	private final ToggleSwitch clientOnly = new ToggleSwitch(true);
	private final FlowPane categories = new FlowPane(6, 6);
	private final Set<String> selectedCategories = new LinkedHashSet<>();
	private final VBox results = new VBox(10);
	private final Label status = Ui.label("", "muted");
	private final Button more = Ui.button("Load more", Icons.ADD, "ghost");
	private final PauseTransition debounce = new PauseTransition(Duration.millis(350));
	private int offset;
	private int generation;
	private boolean categoriesLoaded;
	private String lastInstanceVersion;

	public ModBrowserView(ModsPage page) {
		this.page = page;
		search.setPromptText("Search mods on Modrinth");
		search.getStyleClass().add("search-field");
		HBox.setHgrow(search, Priority.ALWAYS);
		Node searchIcon = Icons.of(Icons.SEARCH, 18);
		searchIcon.setMouseTransparent(true);
		StackPane searchBox = new StackPane(search, searchIcon);
		StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
		searchIcon.setTranslateX(12);
		HBox.setHgrow(searchBox, Priority.ALWAYS);

		loader.setValue("Fabric");
		sort.setValue(SearchQuery.Sort.RELEVANCE);
		version.setPrefWidth(140);
		debounce.setOnFinished(e -> runSearch(true));
		search.textProperty().addListener((o, a, b) -> debounce.playFromStart());
		loader.setOnAction(e -> runSearch(true));
		version.setOnAction(e -> runSearch(true));
		sort.setOnAction(e -> runSearch(true));
		clientOnly.selectedProperty().addListener((o, a, b) -> runSearch(true));
		more.setOnAction(e -> runSearch(false));

		HBox filters = Ui.row(10, Ui.label("Loader", "small", "muted"), loader, Ui.label("Version", "small", "muted"), version,
			Ui.label("Sort", "small", "muted"), sort, Ui.label("Client mods only", "small", "muted"), clientOnly, Ui.spacer(), status);
		VBox content = new VBox(10, results, more);
		content.setAlignment(Pos.TOP_CENTER);
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		VBox.setVgrow(scroll, Priority.ALWAYS);
		root.getChildren().addAll(searchBox, filters, categories, scroll);
	}

	public Node view() {
		return root;
	}

	public void onShow() {
		String instanceVersion = page.instance().minecraftVersion;
		if (!categoriesLoaded) {
			categoriesLoaded = true;
			loadFilters();
		}
		if (!instanceVersion.equals(lastInstanceVersion)) {
			lastInstanceVersion = instanceVersion;
			List<String> items = new ArrayList<>(version.getItems());
			if (!items.contains(instanceVersion)) {
				items.addFirst(instanceVersion);
			}
			if (!items.contains(ANY_VERSION)) {
				items.add(1, ANY_VERSION);
			}
			version.setItems(FXCollections.observableArrayList(items));
			version.setValue(instanceVersion);
			runSearch(true);
		}
	}

	private void loadFilters() {
		Ui.async(() -> page.window().context().modrinth.categories(), list -> {
			categories.getChildren().clear();
			for (Category category : list) {
				if (!"mod".equals(category.projectType()) || !"categories".equals(category.header())) {
					continue;
				}
				Label chip = new Label(pretty(category.name()));
				chip.getStyleClass().add("chip");
				chip.setOnMouseClicked(e -> {
					if (selectedCategories.remove(category.name())) {
						chip.getStyleClass().remove("selected");
					} else {
						selectedCategories.add(category.name());
						chip.getStyleClass().add("selected");
					}
					runSearch(true);
				});
				categories.getChildren().add(chip);
			}
		}, err -> {
		});
		Ui.async(() -> page.window().context().modrinth.gameVersions(), tags -> {
			List<String> items = new ArrayList<>(version.getItems());
			for (GameVersionTag tag : tags) {
				if ("release".equals(tag.versionType()) && !items.contains(tag.version())) {
					items.add(tag.version());
				}
			}
			String current = version.getValue();
			version.setItems(FXCollections.observableArrayList(items));
			version.setValue(current);
		}, err -> {
		});
	}

	private static String pretty(String name) {
		String spaced = name.replace('-', ' ');
		return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private void runSearch(boolean reset) {
		if (reset) {
			offset = 0;
		}
		int token = ++generation;
		SearchQuery query = new SearchQuery().query(search.getText()).sort(sort.getValue()).page(offset, 20).clientSideOnly(clientOnly.isSelected());
		String loaderValue = loader.getValue();
		if (loaderValue != null && !"Any loader".equals(loaderValue)) {
			query.loader(loaderValue.toLowerCase());
		}
		String versionValue = version.getValue();
		if (versionValue != null && !versionValue.equals(ANY_VERSION)) {
			query.gameVersion(versionValue);
		}
		selectedCategories.forEach(query::category);
		status.setText("Searching…");
		more.setDisable(true);
		Ui.async(() -> page.window().context().modrinth.search(query), response -> {
			if (token != generation) {
				return;
			}
			if (reset) {
				results.getChildren().clear();
			}
			render(response);
		}, err -> {
			if (token == generation) {
				status.setText("Search failed");
				Toasts.error("Modrinth search failed: " + Ui.message(err));
			}
		});
	}

	private void render(SearchResponse response) {
		for (SearchHit hit : response.hits()) {
			results.getChildren().add(card(hit));
		}
		offset = response.offset() + response.hits().size();
		status.setText(Ui.compactNumber(response.totalHits()) + " results");
		boolean hasMore = offset < response.totalHits();
		more.setVisible(hasMore);
		more.setManaged(hasMore);
		more.setDisable(false);
		if (response.hits().isEmpty() && results.getChildren().isEmpty()) {
			results.getChildren().add(Ui.label("No mods match these filters.", "muted"));
		}
	}

	private Node card(SearchHit hit) {
		ImageView icon = WebImages.rounded(64, 14);
		WebImages.load(icon, hit.iconUrl(), 128);
		Label title = Ui.label(hit.title(), "h3");
		Label author = Ui.label("by " + hit.author(), "small", "muted");
		Label description = Ui.label(hit.description(), "muted");
		description.setWrapText(true);
		description.setMaxHeight(40);

		HBox stats = new HBox(14);
		stats.setAlignment(Pos.CENTER_LEFT);
		stats.getChildren().addAll(stat(Icons.DOWNLOAD, Ui.compactNumber(hit.downloads())), stat(Icons.HEART, Ui.compactNumber(hit.follows())),
			stat(Icons.REFRESH, Ui.relativeTime(hit.dateModified())));
		HBox tags = new HBox(6);
		if (hit.displayCategories() != null) {
			hit.displayCategories().stream().limit(4).forEach(c -> tags.getChildren().add(MainWindow.badge(pretty(c), null)));
		}

		String instanceVersion = page.instance().minecraftVersion;
		boolean fabric = hit.categories() != null && hit.categories().contains("fabric");
		boolean versionOk = hit.versions() != null && hit.versions().contains(instanceVersion);
		Label compatibility = fabric && versionOk ? MainWindow.badge("Compatible with " + instanceVersion, "success")
			: MainWindow.badge(!fabric ? "Not available for Fabric" : "No version for " + instanceVersion, "warning");

		VBox text = new VBox(4, Ui.row(8, title, author), description, Ui.row(12, stats, tags));
		HBox.setHgrow(text, Priority.ALWAYS);
		Button install = Ui.button("Install", Icons.DOWNLOAD, "primary");
		install.setDisable(!(fabric && versionOk));
		install.setOnAction(e -> page.install(hit.projectId(), null, hit.title(), null));
		VBox side = new VBox(8, compatibility, install);
		side.setAlignment(Pos.CENTER_RIGHT);
		HBox card = new HBox(16, icon, text, side);
		card.setAlignment(Pos.CENTER_LEFT);
		card.getStyleClass().addAll("card", "card-hover");
		card.setOnMouseClicked(e -> {
			if (!(e.getTarget() instanceof Button)) {
				new ProjectDetail(page, hit.projectId(), hit.title()).show();
			}
		});
		return card;
	}

	private static Node stat(String icon, String text) {
		HBox box = new HBox(4, Icons.of(icon, 13), Ui.label(text, "small", "muted"));
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}
}
