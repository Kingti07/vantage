package dev.vantage.launcher.ui.pages;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.modpack.MrPack;
import dev.vantage.common.modrinth.SearchQuery;
import dev.vantage.common.modrinth.model.ModrinthModels.Category;
import dev.vantage.common.modrinth.model.ModrinthModels.GameVersionTag;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchHit;
import dev.vantage.common.modrinth.model.ModrinthModels.SearchResponse;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.util.CancelledException;
import dev.vantage.common.util.FileUtil;
import dev.vantage.launcher.game.ModpackInstaller;
import dev.vantage.launcher.game.ModrinthAppImporter;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Overlay;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.ToggleSwitch;
import dev.vantage.launcher.ui.Ui;
import dev.vantage.launcher.ui.WebImages;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Browse and install complete Modrinth modpacks (mods, configs, options, resource and shader packs) as a new
 * installation, or import a {@code .mrpack} file from disk.
 */
public final class ModpacksPage implements Page {
	private static final String ANY_VERSION = "Any version";

	/** Where a pack came from; Modrinth packs remember their project so the installation can show it. */
	private record Source(String projectId, String versionId, String versionNumber, String iconUrl, Path file, boolean deleteAfter) {
	}

	private record VersionChoice(Version version) {
		@Override
		public String toString() {
			String games = version.gameVersions() == null || version.gameVersions().isEmpty() ? "?" : version.gameVersions().getFirst();
			return version.versionNumber() + "  ·  Minecraft " + games + "  ·  " + Ui.relativeTime(version.datePublished());
		}
	}

	private final MainWindow window;
	private final ModpackInstaller installer;
	private final VBox root = new VBox(12);
	private final TextField search = new TextField();
	private final ComboBox<String> version = new ComboBox<>();
	private final ComboBox<SearchQuery.Sort> sort = new ComboBox<>(FXCollections.observableArrayList(SearchQuery.Sort.values()));
	private final ToggleSwitch allLoaders = new ToggleSwitch(false);
	private final FlowPane categories = new FlowPane(6, 6);
	private final Set<String> selectedCategories = new LinkedHashSet<>();
	private final VBox results = new VBox(10);
	private final Label status = Ui.label("", "muted");
	private final Button more = Ui.button("Load more", Icons.ADD, "ghost");
	private final PauseTransition debounce = new PauseTransition(Duration.millis(350));
	private int offset;
	private int generation;
	private boolean loaded;

	public ModpacksPage(MainWindow window) {
		this.window = window;
		this.installer = new ModpackInstaller(window.context().paths);
		root.getStyleClass().add("page");

		Button importFile = Ui.button("Import .mrpack file", Icons.FOLDER, "ghost");
		importFile.setOnAction(e -> importFile());
		Button fromApp = Ui.button("Import from Modrinth App", Icons.LAYERS, "ghost");
		fromApp.setOnAction(e -> importFromModrinthApp());
		HBox header = Ui.row(12, new VBox(4, Ui.label("Modpacks", "h1"),
				Ui.label("Install a complete modpack from Modrinth: mods, configs, settings, resource and shader packs.", "muted")),
			Ui.spacer(), fromApp, importFile);

		search.setPromptText("Search modpacks on Modrinth");
		search.getStyleClass().add("search-field");
		Node searchIcon = Icons.of(Icons.SEARCH, 18);
		searchIcon.setMouseTransparent(true);
		StackPane searchBox = new StackPane(search, searchIcon);
		StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
		searchIcon.setTranslateX(12);

		version.setPrefWidth(140);
		version.setItems(FXCollections.observableArrayList(ANY_VERSION, VantageInfo.CLIENT_MINECRAFT_VERSION));
		version.setValue(ANY_VERSION);
		sort.setValue(SearchQuery.Sort.RELEVANCE);
		debounce.setOnFinished(e -> runSearch(true));
		search.textProperty().addListener((o, a, b) -> debounce.playFromStart());
		version.setOnAction(e -> runSearch(true));
		sort.setOnAction(e -> runSearch(true));
		allLoaders.selectedProperty().addListener((o, a, b) -> runSearch(true));
		more.setOnAction(e -> runSearch(false));

		HBox filters = Ui.row(10, Ui.label("Minecraft", "small", "muted"), version, Ui.label("Sort", "small", "muted"), sort,
			Ui.label("Also show Forge / NeoForge / Quilt packs", "small", "muted"), allLoaders, Ui.spacer(), status);
		VBox content = new VBox(10, results, more);
		content.setAlignment(Pos.TOP_CENTER);
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		VBox.setVgrow(scroll, Priority.ALWAYS);
		root.getChildren().addAll(header, searchBox, filters, categories, scroll);
	}

	@Override
	public Node view() {
		return root;
	}

	@Override
	public void onShow() {
		if (!loaded) {
			loaded = true;
			loadFilters();
			runSearch(true);
		}
	}

	private void loadFilters() {
		Ui.async(() -> window.context().modrinth.categories(), list -> {
			categories.getChildren().clear();
			for (Category category : list) {
				if (!"modpack".equals(category.projectType()) || !"categories".equals(category.header())) {
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
		Ui.async(() -> window.context().modrinth.gameVersions(), tags -> {
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
		SearchQuery query = new SearchQuery().projectType("modpack").query(search.getText()).sort(sort.getValue()).page(offset, 20);
		if (!allLoaders.isSelected()) {
			query.loader("fabric");
		}
		String versionValue = version.getValue();
		if (versionValue != null && !ANY_VERSION.equals(versionValue)) {
			query.gameVersion(versionValue);
		}
		selectedCategories.forEach(query::category);
		status.setText("Searching…");
		more.setDisable(true);
		Ui.async(() -> window.context().modrinth.search(query), response -> {
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
		status.setText(Ui.compactNumber(response.totalHits()) + " modpacks");
		boolean hasMore = offset < response.totalHits();
		more.setVisible(hasMore);
		more.setManaged(hasMore);
		more.setDisable(false);
		if (response.hits().isEmpty() && results.getChildren().isEmpty()) {
			results.getChildren().add(Ui.label("No modpacks match these filters.", "muted"));
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
		HBox stats = new HBox(14, stat(Icons.DOWNLOAD, Ui.compactNumber(hit.downloads())), stat(Icons.HEART, Ui.compactNumber(hit.follows())),
			stat(Icons.REFRESH, Ui.relativeTime(hit.dateModified())));
		stats.setAlignment(Pos.CENTER_LEFT);
		HBox tags = new HBox(6);
		if (hit.displayCategories() != null) {
			hit.displayCategories().stream().limit(4).forEach(c -> tags.getChildren().add(MainWindow.badge(pretty(c), null)));
		}
		boolean fabric = hit.categories() != null && hit.categories().contains("fabric");
		String newest = hit.versions() == null || hit.versions().isEmpty() ? "" : hit.versions().getLast();
		Label compatibility = fabric ? MainWindow.badge(newest.isEmpty() ? "Fabric" : "Fabric · up to " + newest, "success")
			: MainWindow.badge("Not available for Fabric", "warning");

		VBox text = new VBox(4, Ui.row(8, title, author), description, Ui.row(12, stats, tags));
		HBox.setHgrow(text, Priority.ALWAYS);
		Button install = Ui.button("Install", Icons.DOWNLOAD, "primary");
		install.setDisable(!fabric);
		install.setOnAction(e -> chooseVersion(hit));
		VBox side = new VBox(8, compatibility, install);
		side.setAlignment(Pos.CENTER_RIGHT);
		HBox card = new HBox(16, icon, text, side);
		card.setAlignment(Pos.CENTER_LEFT);
		card.getStyleClass().addAll("card", "card-hover");
		card.setOnMouseClicked(e -> {
			if (fabric && !(e.getTarget() instanceof Button)) {
				chooseVersion(hit);
			}
		});
		return card;
	}

	private static Node stat(String icon, String text) {
		HBox box = new HBox(4, Icons.of(icon, 13), Ui.label(text, "small", "muted"));
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	// ------------------------------------------------------------------------------------------------ install flow

	/** Step 1: pick the pack version (newest first). */
	private void chooseVersion(SearchHit hit) {
		var task = window.taskBar().begin("Loading versions of " + hit.title());
		Ui.async(() -> window.context().modrinth.projectVersions(hit.projectId(), "fabric", null), versions -> {
			task.finish();
			if (versions.isEmpty()) {
				Overlay.message("No Fabric version", hit.title() + " has no version for Fabric, so Vantage cannot install it.");
				return;
			}
			VBox dialog = new VBox(14);
			dialog.getStyleClass().add("dialog");
			ImageView icon = WebImages.rounded(48, 12);
			WebImages.load(icon, hit.iconUrl(), 96);
			dialog.getChildren().add(Ui.row(12, icon, new VBox(2, Ui.label("Install " + hit.title(), "h2"), Ui.label("by " + hit.author(), "muted"))));
			ComboBox<VersionChoice> choice = new ComboBox<>(FXCollections.observableArrayList(versions.stream().map(VersionChoice::new).toList()));
			choice.setValue(choice.getItems().getFirst());
			choice.setMaxWidth(Double.MAX_VALUE);
			dialog.getChildren().addAll(Ui.label("Version", "small", "muted"), choice,
				Ui.label("Vantage downloads the pack, shows what it contains and only then installs it as a new installation.", "small", "faint"));
			Button cancel = Ui.button("Cancel", null, "ghost");
			Button next = Ui.button("Continue", Icons.DOWNLOAD, "primary");
			HBox buttons = new HBox(10, Ui.spacer(), cancel, next);
			dialog.getChildren().add(buttons);
			Runnable close = Overlay.show(dialog, 560);
			cancel.setOnAction(e -> close.run());
			next.setOnAction(e -> {
				close.run();
				download(hit, choice.getValue().version());
			});
		}, err -> {
			task.finish();
			Overlay.error("Cannot load " + hit.title(), err);
		});
	}

	/** Step 2: download the .mrpack and read it. */
	private void download(SearchHit hit, Version packVersion) {
		var task = window.taskBar().begin("Downloading " + hit.title(), true);
		Ui.async(() -> {
			Path file = installer.download(packVersion, task);
			return MrPack.open(file);
		}, pack -> {
			task.finish();
			confirm(pack, new Source(hit.projectId(), packVersion.id(), packVersion.versionNumber(), hit.iconUrl(), pack.file(), true));
		}, err -> {
			task.finish();
			if (!(err instanceof CancelledException)) {
				Overlay.error("Cannot download " + hit.title(), err);
			}
		});
	}

	private void importFile() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Choose a Modrinth modpack");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Modrinth modpack (*.mrpack)", "*.mrpack"));
		File file = chooser.showOpenDialog(window.stage());
		if (file == null) {
			return;
		}
		Ui.async(() -> MrPack.open(file.toPath()), pack -> confirm(pack, new Source("", "", pack.versionId(), "", pack.file(), false)),
			err -> Overlay.error("Cannot read " + file.getName(), err));
	}

	/** Step 3: show what the pack contains and how it will be installed. */
	private void confirm(MrPack pack, Source source) {
		Ui.async(pack::overrideCount, overrides -> showConfirm(pack, source, overrides), err -> showConfirm(pack, source, 0));
	}

	private void showConfirm(MrPack pack, Source source, int overrides) {
		VBox dialog = new VBox(14);
		dialog.getStyleClass().add("dialog");
		ImageView icon = WebImages.rounded(48, 12);
		if (!source.iconUrl().isBlank()) {
			WebImages.load(icon, source.iconUrl(), 96);
		}
		String versionText = source.versionNumber() == null || source.versionNumber().isBlank() ? "" : " " + source.versionNumber();
		dialog.getChildren().add(Ui.row(12, icon, new VBox(2, Ui.label(pack.name() + versionText, "h2"),
			Ui.label("Minecraft " + pack.minecraftVersion() + " · " + pack.loader().label
				+ (pack.loaderVersion() == null ? "" : " " + pack.loaderVersion()), "muted"))));
		if (!pack.summary().isBlank()) {
			Label summary = Ui.label(pack.summary(), "muted");
			summary.setWrapText(true);
			dialog.getChildren().add(summary);
		}

		List<MrPack.PackFile> required = pack.clientFiles(false);
		List<MrPack.PackFile> optional = pack.clientFiles(true).stream().filter(MrPack.PackFile::optional).toList();
		long mods = required.stream().filter(f -> f.path().startsWith("mods/")).count();
		dialog.getChildren().add(Ui.label(mods + " mods, " + (required.size() - mods) + " other files to download ("
			+ FileUtil.humanSize(pack.downloadSize(false)) + ") and " + overrides + " configs, settings and packs included.", "small"));

		TextField name = new TextField(pack.name());
		dialog.getChildren().addAll(Ui.label("Installation name", "small", "muted"), name);

		ToggleSwitch includeOptional = new ToggleSwitch(true);
		if (!optional.isEmpty()) {
			Label list = Ui.label(String.join(", ", optional.stream().map(MrPack.PackFile::fileName).limit(12).toList())
				+ (optional.size() > 12 ? " …" : ""), "small", "faint");
			list.setWrapText(true);
			dialog.getChildren().addAll(Ui.row(10, Ui.label("Install " + optional.size() + " optional file" + (optional.size() == 1 ? "" : "s"), "small"),
				Ui.spacer(), includeOptional), list);
		}

		boolean vantagePossible = VantageInfo.CLIENT_MINECRAFT_VERSION.equals(pack.minecraftVersion());
		ToggleSwitch vantage = new ToggleSwitch(vantagePossible);
		if (vantagePossible) {
			dialog.getChildren().add(Ui.row(10, Ui.label("Add the Vantage PvP client (HUD, zoom, keybinds …)", "small"), Ui.spacer(), vantage));
		} else {
			Label note = Ui.label("The Vantage PvP features need Minecraft " + VantageInfo.CLIENT_MINECRAFT_VERSION
				+ ". This pack runs without them.", "small", "warning-text");
			note.setWrapText(true);
			dialog.getChildren().add(note);
		}

		long totalMb = totalMemoryMb();
		Slider memory = new Slider(2048, Math.max(4096, Math.min(totalMb - 1024, 32768)), defaultMemory(totalMb));
		HBox.setHgrow(memory, Priority.ALWAYS);
		Label memoryLabel = Ui.label("", "h3");
		memoryLabel.setMinWidth(80);
		Runnable updateMemory = () -> memoryLabel.setText(String.format(Locale.ROOT, "%.2f GB", Math.round(memory.getValue() / 256) * 256 / 1024.0));
		memory.valueProperty().addListener((o, a, b) -> updateMemory.run());
		updateMemory.run();
		dialog.getChildren().addAll(Ui.label("Memory (modpacks usually need more than plain Minecraft)", "small", "muted"), Ui.row(12, memory, memoryLabel));

		boolean supported = pack.isSupported();
		if (!supported) {
			Label error = Ui.label(ModpackInstaller.unsupportedMessage(pack), "danger-text");
			error.setWrapText(true);
			dialog.getChildren().add(error);
		}
		Button cancel = Ui.button("Cancel", null, "ghost");
		Button install = Ui.button("Install", Icons.DOWNLOAD, "primary");
		install.setDisable(!supported);
		dialog.getChildren().add(new HBox(10, Ui.spacer(), cancel, install));
		ScrollPane scroll = new ScrollPane(dialog);
		scroll.setFitToWidth(true);
		scroll.setMaxHeight(640);
		VBox wrapper = new VBox(scroll);
		wrapper.getStyleClass().add("dialog");
		wrapper.setStyle("-fx-padding: 0;");
		dialog.setStyle("-fx-effect: null; -fx-border-width: 0;");
		Runnable close = Overlay.show(wrapper, 600);
		cancel.setOnAction(e -> {
			close.run();
			cleanUp(source);
		});
		install.setOnAction(e -> {
			close.run();
			String chosenName = name.getText().isBlank() ? pack.name() : name.getText().strip();
			int memoryMb = (int) (Math.round(memory.getValue() / 256) * 256);
			install(pack, source, chosenName, includeOptional.isSelected(), vantagePossible && vantage.isSelected(), memoryMb);
		});
	}

	/** Step 4: create the installation and fill it; on failure nothing is left behind. */
	private void install(MrPack pack, Source source, String name, boolean includeOptional, boolean vantageClient, int memoryMb) {
		var settings = window.context().settings;
		Instance instance = settings.createInstance(name, pack.minecraftVersion());
		instance.vantageClient = vantageClient && instance.supportsVantageClient();
		instance.loaderVersion = ModpackInstaller.loaderVersion(pack, instance.vantageClient);
		// The pack decides its own mods; Vantage's performance pack would add or duplicate mods.
		instance.performanceMods = false;
		instance.performanceModsInstalled = false;
		instance.offerPerformanceMods = false;
		instance.memoryMb = memoryMb;
		instance.modpackProjectId = source.projectId();
		instance.modpackVersionId = source.versionId();
		instance.modpackName = pack.name();
		instance.modpackVersion = source.versionNumber() == null ? "" : source.versionNumber();
		instance.iconUrl = source.iconUrl();
		settings.updateInstance(instance);
		Path gameDir = instance.resolveGameDirectory(window.context().paths.instances());
		int threads = settings.settings().downloadThreads;
		var task = window.taskBar().begin("Installing " + name, true);
		Ui.async(() -> installer.installFiles(pack, gameDir, includeOptional, threads, task), overrides -> {
			task.finish();
			cleanUp(source);
			settings.select(instance);
			Toasts.success("Installed " + name + ". Press Play to start it.");
			window.navigate("installations");
		}, err -> {
			task.finish();
			settings.deleteInstance(instance);
			Ui.background(() -> {
				try {
					FileUtil.deleteRecursively(gameDir);
				} catch (IOException e) {
					// A leftover folder is harmless; it is not listed as an installation any more.
				}
			});
			cleanUp(source);
			if (!(err instanceof CancelledException)) {
				Overlay.message("Installation failed", Ui.message(err) + "\n\nThe unfinished installation was removed.");
			}
		});
	}

	// ------------------------------------------------------------------------------------------------ Modrinth App

	/** Opens the list of Modrinth App profiles (also used by the UI snapshots). */
	public void showModrinthAppImport() {
		importFromModrinthApp();
	}

	/** Lists the profiles of the Modrinth App on this computer. */
	private void importFromModrinthApp() {
		var task = window.taskBar().begin("Looking for Modrinth App profiles");
		Ui.async(() -> {
			Path dir = ModrinthAppImporter.findDataDir()
				.orElseThrow(() -> new IOException("The Modrinth App was not found on this computer."));
			return ModrinthAppImporter.profiles(dir);
		}, profiles -> {
			task.finish();
			showProfiles(profiles);
		}, err -> {
			task.finish();
			Overlay.error("Cannot read the Modrinth App", err);
		});
	}

	private static Node profileIcon(ModrinthAppImporter.Profile profile, int size) {
		if (profile.icon() != null) {
			ImageView icon = WebImages.rounded(size, size / 4.0);
			WebImages.load(icon, profile.icon().toUri().toString(), size * 2);
			return icon;
		}
		// Profiles without their own picture get the same placeholder as installations.
		Node symbol = Icons.of(Icons.LAYERS, size * 0.55);
		symbol.lookupAll(".icon").forEach(n -> n.setStyle("-fx-fill: -v-muted;"));
		StackPane box = new StackPane(symbol);
		box.setMinSize(size, size);
		box.setPrefSize(size, size);
		box.setMaxSize(size, size);
		box.getStyleClass().add("inset");
		return box;
	}

	private void showProfiles(List<ModrinthAppImporter.Profile> profiles) {
		VBox dialog = new VBox(12);
		dialog.getStyleClass().add("dialog");
		dialog.getChildren().addAll(Ui.label("Import from the Modrinth App", "h2"),
			wrapped(Ui.label("Vantage copies the profile into a new installation. Your Modrinth App profiles stay exactly as they are.", "muted")));
		Runnable[] close = new Runnable[1];
		if (profiles.isEmpty()) {
			dialog.getChildren().add(Ui.label("The Modrinth App has no profiles yet.", "muted"));
		}
		for (ModrinthAppImporter.Profile profile : profiles) {
			Label details = Ui.label(profile.gameVersion() == null ? "Minecraft version unknown"
				: "Minecraft " + profile.gameVersion() + " · " + profile.loaderLabel() + (profile.loaderVersion() == null ? "" : " " + profile.loaderVersion()),
				"small", "muted");
			VBox text = new VBox(2, Ui.label(profile.name(), "h3"), details);
			if (profile.problem() != null) {
				text.getChildren().add(wrapped(Ui.label(profile.problem(), "small", "warning-text")));
			}
			HBox.setHgrow(text, Priority.ALWAYS);
			Button importButton = Ui.button("Import", Icons.DOWNLOAD, "primary");
			importButton.setDisable(profile.problem() != null);
			importButton.setOnAction(e -> {
				close[0].run();
				confirmProfile(profile);
			});
			HBox row = new HBox(12, profileIcon(profile, 40), text, importButton);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("inset");
			dialog.getChildren().add(row);
		}
		Button cancel = Ui.button("Close", null, "ghost");
		dialog.getChildren().add(new HBox(10, Ui.spacer(), cancel));
		close[0] = Overlay.show(scrolling(dialog), 620);
		cancel.setOnAction(e -> close[0].run());
	}

	private record ProfileSizes(long withoutWorlds, long withWorlds) {
	}

	private void confirmProfile(ModrinthAppImporter.Profile profile) {
		Ui.async(() -> new ProfileSizes(ModrinthAppImporter.size(profile, false), ModrinthAppImporter.size(profile, true)),
			sizes -> showProfileConfirm(profile, sizes), err -> showProfileConfirm(profile, new ProfileSizes(0, 0)));
	}

	private void showProfileConfirm(ModrinthAppImporter.Profile profile, ProfileSizes sizes) {
		VBox dialog = new VBox(14);
		dialog.getStyleClass().add("dialog");
		dialog.getChildren().add(Ui.row(12, profileIcon(profile, 48), new VBox(2, Ui.label("Import " + profile.name(), "h2"),
			Ui.label("Minecraft " + profile.gameVersion() + " · " + profile.loaderLabel()
				+ (profile.loaderVersion() == null ? "" : " " + profile.loaderVersion()) + " · from the Modrinth App", "muted"))));
		dialog.getChildren().add(wrapped(Ui.label("Copies mods, configs, settings, resource packs, shader packs, servers and screenshots ("
			+ FileUtil.humanSize(sizes.withoutWorlds()) + "). Logs and caches are left out.", "small")));

		TextField name = new TextField(profile.name());
		dialog.getChildren().addAll(Ui.label("Installation name", "small", "muted"), name);

		long worldsSize = sizes.withWorlds() - sizes.withoutWorlds();
		ToggleSwitch worlds = new ToggleSwitch(false);
		if (worldsSize > 0) {
			dialog.getChildren().add(Ui.row(10, Ui.label("Also copy the worlds (" + FileUtil.humanSize(worldsSize) + " more)", "small"), Ui.spacer(), worlds));
		}

		boolean vantagePossible = VantageInfo.CLIENT_MINECRAFT_VERSION.equals(profile.gameVersion());
		ToggleSwitch vantage = new ToggleSwitch(vantagePossible);
		if (vantagePossible) {
			dialog.getChildren().add(Ui.row(10, Ui.label("Add the Vantage PvP client (HUD, zoom, keybinds …)", "small"), Ui.spacer(), vantage));
		} else {
			dialog.getChildren().add(wrapped(Ui.label("The Vantage PvP features need Minecraft " + VantageInfo.CLIENT_MINECRAFT_VERSION
				+ ". This profile runs without them.", "small", "warning-text")));
		}

		long totalMb = totalMemoryMb();
		Slider memory = new Slider(2048, Math.max(4096, Math.min(totalMb - 1024, 32768)), defaultMemory(totalMb));
		HBox.setHgrow(memory, Priority.ALWAYS);
		Label memoryLabel = Ui.label("", "h3");
		memoryLabel.setMinWidth(80);
		Runnable updateMemory = () -> memoryLabel.setText(String.format(Locale.ROOT, "%.2f GB", Math.round(memory.getValue() / 256) * 256 / 1024.0));
		memory.valueProperty().addListener((o, a, b) -> updateMemory.run());
		updateMemory.run();
		dialog.getChildren().addAll(Ui.label("Memory", "small", "muted"), Ui.row(12, memory, memoryLabel));

		Button cancel = Ui.button("Cancel", null, "ghost");
		Button install = Ui.button("Import", Icons.DOWNLOAD, "primary");
		dialog.getChildren().add(new HBox(10, Ui.spacer(), cancel, install));
		Runnable close = Overlay.show(scrolling(dialog), 600);
		cancel.setOnAction(e -> close.run());
		install.setOnAction(e -> {
			close.run();
			String chosenName = name.getText().isBlank() ? profile.name() : name.getText().strip();
			installProfile(profile, chosenName, worlds.isSelected(), vantagePossible && vantage.isSelected(),
				(int) (Math.round(memory.getValue() / 256) * 256));
		});
	}

	/** Creates the installation and copies the profile into it; on failure nothing is left behind. */
	private void installProfile(ModrinthAppImporter.Profile profile, String name, boolean worlds, boolean vantageClient, int memoryMb) {
		var settings = window.context().settings;
		Instance instance = settings.createInstance(name, profile.gameVersion());
		instance.vantageClient = vantageClient && instance.supportsVantageClient();
		instance.loaderVersion = "fabric".equals(profile.loader()) ? ModpackInstaller.loaderVersion(profile.loaderVersion(), instance.vantageClient) : "";
		// The profile keeps exactly its own mods.
		instance.performanceMods = false;
		instance.performanceModsInstalled = false;
		instance.offerPerformanceMods = false;
		instance.memoryMb = memoryMb;
		Path iconCopy = profile.icon() == null ? null : window.context().paths.root().resolve("icons").resolve(instance.id + ".png");
		if (iconCopy != null) {
			instance.iconUrl = iconCopy.toUri().toString();
		}
		settings.updateInstance(instance);
		Path gameDir = instance.resolveGameDirectory(window.context().paths.instances());
		var task = window.taskBar().begin("Importing " + name, true);
		Ui.async(() -> {
			int files = ModrinthAppImporter.copy(profile, gameDir, worlds, task);
			if (iconCopy != null) {
				Files.createDirectories(iconCopy.getParent());
				Files.copy(profile.icon(), iconCopy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			return files;
		}, files -> {
			task.finish();
			settings.select(instance);
			Toasts.success("Imported " + name + " (" + files + " files). Press Play to start it.");
			window.navigate("installations");
		}, err -> {
			task.finish();
			settings.deleteInstance(instance);
			Ui.background(() -> {
				try {
					FileUtil.deleteRecursively(gameDir);
				} catch (IOException e) {
					// A leftover folder is harmless; it is not listed as an installation any more.
				}
			});
			if (!(err instanceof CancelledException)) {
				Overlay.message("Import failed", Ui.message(err) + "\n\nThe unfinished installation was removed.");
			}
		});
	}

	private static Label wrapped(Label label) {
		label.setWrapText(true);
		label.setMaxWidth(540);
		return label;
	}

	private static Node scrolling(VBox dialog) {
		ScrollPane scroll = new ScrollPane(dialog);
		scroll.setFitToWidth(true);
		scroll.setMaxHeight(640);
		VBox wrapper = new VBox(scroll);
		wrapper.getStyleClass().add("dialog");
		wrapper.setStyle("-fx-padding: 0;");
		dialog.setStyle("-fx-effect: null; -fx-border-width: 0;");
		return wrapper;
	}

	/** Deletes a downloaded pack file from the cache (never a file the user picked). */
	private static void cleanUp(Source source) {
		if (source.deleteAfter()) {
			Ui.background(() -> {
				try {
					Files.deleteIfExists(source.file());
				} catch (IOException ignored) {
					// only a cache file
				}
			});
		}
	}

	private static int defaultMemory(long totalMb) {
		if (totalMb >= 16_384) {
			return 8192;
		}
		return totalMb >= 12_288 ? 6144 : 4096;
	}

	private static long totalMemoryMb() {
		try {
			var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			return os.getTotalMemorySize() / (1024 * 1024);
		} catch (ClassCastException | LinkageError e) {
			return 8192;
		}
	}

	/** Used by the installation cards. */
	public static String describe(Instance instance) {
		return instance.modpackName + (instance.modpackVersion.isBlank() ? "" : " " + instance.modpackVersion);
	}
}
