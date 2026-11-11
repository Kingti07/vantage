package dev.vantage.launcher.ui.pages.mods;

import dev.vantage.common.mods.PackInstaller;
import dev.vantage.common.modrinth.SearchQuery;
import dev.vantage.common.modrinth.model.ModrinthModels.Category;
import dev.vantage.common.modrinth.model.ModrinthModels.GameVersionTag;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchHit;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchResponse;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Overlay;
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
 * Modrinth search for mods, resource packs and shaders with loader / game version / category filters and sorting.
 * Results show compatibility with the selected installation; only compatible mods can be installed. Packs go into
 * the installation's {@code resourcepacks/} or {@code shaderpacks/} folder; shaders offer to install Iris.
 */
public final class ModBrowserView {
	private static final String ANY_VERSION = "Any version";
	private static final List<String> LOADERS = List.of("Fabric", "Forge", "NeoForge", "Quilt", "Any loader");

	private final ModsPage page;
	private final VBox root = new VBox(12);
	private final TextField search = new TextField();
	private static final List<String> TYPES = List.of("Mods", "Resource packs", "Shaders");
	private final ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(TYPES));
	private final ComboBox<String> loader = new ComboBox<>(FXCollections.observableArrayList(LOADERS));
	private final Label loaderLabel = Ui.label("Loader", "small", "muted");
	private final Label clientOnlyLabel = Ui.label("Client mods only", "small", "muted");
	private List<Category> allCategories = List.of();
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

		type.setValue("Mods");
		type.setOnAction(e -> {
			boolean mods = kind() == null;
			for (Node node : List.of(loaderLabel, loader, clientOnlyLabel, clientOnly)) {
				node.setVisible(mods);
				node.setManaged(mods);
			}
			search.setPromptText(mods ? "Search mods on Modrinth" : kind() == PackInstaller.Kind.SHADER ? "Search shaders on Modrinth"
				: "Search resource packs on Modrinth");
			selectedCategories.clear();
			showCategories();
			runSearch(true);
		});
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

		HBox filters = Ui.row(10, type, loaderLabel, loader, Ui.label("Version", "small", "muted"), version,
			Ui.label("Sort", "small", "muted"), sort, clientOnlyLabel, clientOnly, Ui.spacer(), status);
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
			allCategories = list;
			showCategories();
		}, err -> {
		});
		loadVersions();
	}

	public void selectType(String value) {
		if (TYPES.contains(value) && !value.equals(type.getValue())) {
			type.setValue(value);
		}
	}

	/** The selected pack kind, or null for mods. */
	private PackInstaller.Kind kind() {
		return switch (type.getValue() == null ? "Mods" : type.getValue()) {
			case "Resource packs" -> PackInstaller.Kind.RESOURCE_PACK;
			case "Shaders" -> PackInstaller.Kind.SHADER;
			default -> null;
		};
	}

	private String projectType() {
		return kind() == null ? "mod" : kind().projectType;
	}

	private void showCategories() {
		categories.getChildren().clear();
		String projectType = projectType();
		{
			for (Category category : allCategories) {
				if (!projectType.equals(category.projectType()) || !"categories".equals(category.header())) {
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
		}
	}

	private void loadVersions() {
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
		PackInstaller.Kind kind = kind();
		SearchQuery query = new SearchQuery().projectType(projectType()).query(search.getText()).sort(sort.getValue()).page(offset, 20)
			.clientSideOnly(kind == null && clientOnly.isSelected());
		String loaderValue = loader.getValue();
		if (kind == PackInstaller.Kind.SHADER) {
			query.loader("iris");
		} else if (kind == null && loaderValue != null && !"Any loader".equals(loaderValue)) {
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
			results.getChildren().add(Ui.label("Nothing matches these filters.", "muted"));
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
		PackInstaller.Kind kind = kind();
		if (kind != null) {
			return packCard(hit, kind, icon, title, author, description, stats, tags, instanceVersion);
		}
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

	private Node packCard(SearchHit hit, PackInstaller.Kind kind, ImageView icon, Label title, Label author, Label description, HBox stats,
		HBox tags, String instanceVersion) {
		boolean versionOk = hit.versions() != null && hit.versions().contains(instanceVersion);
		Label compatibility = kind == PackInstaller.Kind.SHADER ? MainWindow.badge("Needs Iris", "accent")
			: versionOk ? MainWindow.badge("For " + instanceVersion, "success") : MainWindow.badge("Made for other versions", "warning");
		VBox text = new VBox(4, Ui.row(8, title, author), description, Ui.row(12, stats, tags));
		HBox.setHgrow(text, Priority.ALWAYS);
		Button install = Ui.button("Install", Icons.DOWNLOAD, "primary");
		install.setOnAction(e -> installPack(hit, kind));
		VBox side = new VBox(8, compatibility, install);
		side.setAlignment(Pos.CENTER_RIGHT);
		HBox card = new HBox(16, icon, text, side);
		card.setAlignment(Pos.CENTER_LEFT);
		card.getStyleClass().addAll("card", "card-hover");
		card.setOnMouseClicked(e -> {
			if (!(e.getTarget() instanceof Button)) {
				Ui.openUrl("https://modrinth.com/" + kind.projectType + "/" + hit.slug());
			}
		});
		return card;
	}

	private record PackResult(boolean exact, boolean irisMissing) {
	}

	/** Downloads a resource or shader pack into the installation and explains how to switch it on. */
	private void installPack(SearchHit hit, PackInstaller.Kind kind) {
		var instance = page.instance();
		var context = page.window().context();
		PackInstaller installer = new PackInstaller(context.modrinth, instance.resolveGameDirectory(context.paths.instances()), instance.minecraftVersion);
		var task = page.window().taskBar().begin("Installing " + hit.title(), true);
		Ui.async(() -> {
			PackInstaller.Choice choice = installer.choose(hit.projectId(), kind);
			installer.install(choice.version(), kind, task);
			boolean irisMissing = kind == PackInstaller.Kind.SHADER
				&& page.manager().installed().stream().noneMatch(m -> "iris".equals(m.modId()));
			return new PackResult(choice.exactGameVersion(), irisMissing);
		}, result -> {
			task.finish();
			String where = kind == PackInstaller.Kind.SHADER ? "Options > Video Settings > Shader Packs" : "Options > Resource Packs";
			Toasts.success("Installed " + hit.title() + ". Switch it on in Minecraft: " + where + "."
				+ (result.exact() ? "" : " It was made for another Minecraft version and may look different."));
			if (result.irisMissing()) {
				Overlay.confirm("Shaders need Iris", "Shader packs only work with the Iris mod, which is not installed in "
					+ instance.name + " yet. Install Iris (and what it needs) now?", "Install Iris", false,
					() -> page.install("iris", null, "Iris", null));
			}
		}, err -> {
			task.finish();
			if (!(err instanceof dev.vantage.common.util.CancelledException)) {
				Overlay.error("Cannot install " + hit.title(), err);
			}
		});
	}

	private static Node stat(String icon, String text) {
		HBox box = new HBox(4, Icons.of(icon, 13), Ui.label(text, "small", "muted"));
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}
}
