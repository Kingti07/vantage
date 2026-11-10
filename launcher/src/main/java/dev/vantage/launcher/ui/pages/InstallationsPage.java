package dev.vantage.launcher.ui.pages;

import dev.vantage.common.VantageInfo;
import dev.vantage.launcher.game.GameLauncher;
import dev.vantage.launcher.game.GameMeta;
import dev.vantage.launcher.game.JavaRuntimeManager;
import dev.vantage.launcher.game.MojangModels;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Overlay;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.ToggleSwitch;
import dev.vantage.launcher.ui.Ui;
import dev.vantage.common.util.ProgressListener;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Lists installations and edits their version, loader, memory, Java and directory settings. */
public final class InstallationsPage implements Page {
	private static final String LATEST_LOADER = "Latest stable";
	/** releaseTime of Minecraft 1.7.10 in Mojang's manifest; older versions are not offered. */
	static final String OLDEST_SUPPORTED_RELEASE = "2014-05-14T17:29:23+00:00";

	private final MainWindow window;
	private final VBox root = new VBox();
	private final FlowPane grid = new FlowPane(16, 16);

	public InstallationsPage(MainWindow window) {
		this.window = window;
		root.getStyleClass().add("page");
		Button create = Ui.button("New installation", Icons.ADD, "primary");
		create.setOnAction(e -> createNew());
		HBox header = Ui.row(12, new VBox(4, Ui.label("Installations", "h1"),
			Ui.label("Each installation has its own Minecraft version, Fabric loader, mods, worlds and settings.", "muted")), Ui.spacer(), create);
		root.getChildren().addAll(header, grid);
	}

	/** Opens the editor for a new installation. */
	public void createNew() {
		Instance instance = new Instance();
		instance.id = null;
		instance.name = "New installation";
		edit(instance);
	}

	@Override
	public Node view() {
		ScrollPane scroll = new ScrollPane(root);
		scroll.setFitToWidth(true);
		return scroll;
	}

	@Override
	public void onShow() {
		grid.getChildren().clear();
		String selected = window.context().settings.selectedInstance().id;
		for (Instance instance : window.context().settings.instances()) {
			grid.getChildren().add(card(instance, instance.id.equals(selected)));
		}
	}

	private Node card(Instance instance, boolean selected) {
		VBox card = new VBox(10);
		card.getStyleClass().addAll("card", "card-hover");
		if (selected) {
			card.getStyleClass().add("card-selected");
		}
		card.setPrefWidth(300);
		Node icon = Icons.of(Icons.LAYERS, 26);
		icon.lookupAll(".icon").forEach(n -> n.setStyle("-fx-fill: -v-accent;"));
		Label name = Ui.label(instance.name, "h3");
		HBox badges = new HBox(6, MainWindow.badge(instance.minecraftVersion, null),
			MainWindow.badge("Fabric " + (instance.loaderVersion.isBlank() ? "latest" : instance.loaderVersion), null));
		if (instance.vantageClient && instance.supportsVantageClient()) {
			badges.getChildren().add(MainWindow.badge("Vantage", "accent"));
		}
		Label played = Ui.label(instance.lastPlayed > 0 ? "Last played " + Ui.relativeTime(Instant.ofEpochMilli(instance.lastPlayed)) : "Never played", "small", "muted");
		Button play = Ui.button("Play", Icons.PLAY, "primary");
		play.setOnAction(e -> window.play(instance, GameLauncher.QuickPlay.NONE));
		Button edit = Ui.iconButton(Icons.EDIT, "Edit installation", "ghost");
		edit.setOnAction(e -> edit(instance.copy()));
		Button folder = Ui.iconButton(Icons.FOLDER, "Open game folder", "ghost");
		folder.setOnAction(e -> Ui.openFolder(instance.resolveGameDirectory(window.context().paths.instances())));
		HBox actions = new HBox(8, play, Ui.spacer(), folder, edit);
		actions.setAlignment(Pos.CENTER_LEFT);
		card.getChildren().addAll(Ui.row(10, icon, name), badges, played, actions);
		card.setOnMouseClicked(e -> {
			if (e.getClickCount() == 1 && e.getTarget() == card) {
				window.context().settings.select(instance);
				onShow();
			}
		});
		return card;
	}

	/** Opens the editor; an instance with a null id is created on save. */
	private void edit(Instance instance) {
		boolean creating = instance.id == null;
		VBox dialog = new VBox(16);
		dialog.getStyleClass().add("dialog");
		dialog.setPrefWidth(720);

		TextField name = new TextField(instance.name);
		ComboBox<String> version = new ComboBox<>();
		version.setPrefWidth(220);
		version.setValue(instance.minecraftVersion);
		ComboBox<String> loader = new ComboBox<>();
		loader.setPrefWidth(220);
		loader.setValue(instance.loaderVersion.isBlank() ? LATEST_LOADER : instance.loaderVersion);
		ToggleSwitch snapshots = new ToggleSwitch(window.context().settings.settings().showSnapshots);

		long totalMb = totalMemoryMb();
		Slider memory = new Slider(1024, Math.max(2048, Math.min(totalMb - 1024, 32768)), Math.min(instance.memoryMb, Math.max(2048, totalMb - 1024)));
		memory.setMajorTickUnit(1024);
		memory.setBlockIncrement(512);
		memory.setSnapToTicks(false);
		HBox.setHgrow(memory, Priority.ALWAYS);
		Label memoryLabel = Ui.label("", "h3");
		memoryLabel.setMinWidth(80);
		Runnable updateMemory = () -> {
			long mb = Math.round(memory.getValue() / 256) * 256;
			memoryLabel.setText(String.format("%.2f GB", mb / 1024.0));
		};
		memory.valueProperty().addListener((o, a, b) -> updateMemory.run());
		updateMemory.run();

		TextField javaPath = new TextField(instance.javaPath);
		javaPath.setPromptText("Automatic – Mojang Java runtime");
		HBox.setHgrow(javaPath, Priority.ALWAYS);
		Button browseJava = Ui.button("Browse", Icons.FOLDER, "ghost");
		browseJava.setOnAction(e -> {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Select a Java executable");
			File file = chooser.showOpenDialog(window.stage());
			if (file != null) {
				javaPath.setText(file.getAbsolutePath());
			}
		});
		Button testJava = Ui.button("Test", Icons.CHECK, "ghost");
		testJava.setOnAction(e -> {
			if (javaPath.getText().isBlank()) {
				Toasts.info("Automatic mode downloads the Java version Mojang specifies for this Minecraft version.");
				return;
			}
			Ui.async(() -> JavaRuntimeManager.probeMajorVersion(Path.of(javaPath.getText().trim())),
				major -> {
					if (major < 0) {
						Toasts.error("That file is not a working Java executable.");
					} else if (major < 21 && version.getValue() != null && version.getValue().startsWith("1.21")) {
						Toasts.error("Java " + major + " found, but Minecraft " + version.getValue() + " needs Java 21 or newer.");
					} else {
						Toasts.success("Java " + major + " works.");
					}
				},
				err -> Toasts.error(Ui.message(err)));
		});
		TextField jvmArgs = new TextField(instance.extraJvmArgs);
		jvmArgs.setPromptText("Extra JVM arguments (optional)");

		ToggleSwitch customResolution = new ToggleSwitch(instance.customResolution);
		Spinner<Integer> width = new Spinner<>(640, 7680, instance.width, 10);
		Spinner<Integer> height = new Spinner<>(360, 4320, instance.height, 10);
		width.setEditable(true);
		height.setEditable(true);
		width.setPrefWidth(110);
		height.setPrefWidth(110);
		ToggleSwitch fullscreen = new ToggleSwitch(instance.fullscreen);

		TextField gameDir = new TextField(instance.gameDirectory);
		gameDir.setPromptText("Default – managed by Vantage");
		HBox.setHgrow(gameDir, Priority.ALWAYS);
		Button browseDir = Ui.button("Browse", Icons.FOLDER, "ghost");
		browseDir.setOnAction(e -> {
			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle("Select game directory");
			File dir = chooser.showDialog(window.stage());
			if (dir != null) {
				gameDir.setText(dir.getAbsolutePath());
			}
		});

		ToggleSwitch vantage = new ToggleSwitch(instance.vantageClient);
		Label vantageNote = Ui.label("", "small", "muted");
		vantageNote.setWrapText(true);
		Runnable updateVantage = () -> {
			boolean supported = VantageInfo.CLIENT_MINECRAFT_VERSION.equals(version.getValue());
			vantage.setDisable(!supported);
			vantageNote.setText(supported ? "Adds the Vantage main menu, HUD editor, PvP modules and in-game mod browser."
				: "The Vantage client is built for Minecraft " + VantageInfo.CLIENT_MINECRAFT_VERSION + ". This installation runs plain Fabric with your mods.");
		};

		Runnable loadLoaders = () -> {
			String v = version.getValue();
			if (v == null) {
				return;
			}
			Ui.async(() -> window.context().meta.loaderVersions(v), list -> {
				List<String> items = new ArrayList<>();
				items.add(LATEST_LOADER);
				list.forEach(l -> items.add(l.version()));
				String previous = loader.getValue();
				loader.setItems(FXCollections.observableArrayList(items));
				loader.setValue(items.contains(previous) ? previous : LATEST_LOADER);
				if (list.isEmpty()) {
					Toasts.error("Fabric does not support Minecraft " + v + ".");
				}
			}, err -> Toasts.error("Could not load Fabric versions: " + Ui.message(err)));
		};
		Runnable loadVersions = () -> Ui.async(() -> window.context().meta.manifest(), manifest -> {
			List<String> ids = new ArrayList<>();
			for (MojangModels.ManifestEntry entry : manifest.versions()) {
				boolean wanted = "release".equals(entry.type()) || (snapshots.isSelected() && "snapshot".equals(entry.type()));
				// Versions before 1.7.10 use an asset layout this launcher does not install (decision in AUDIT.md, F8).
				boolean supported = entry.releaseTime() == null || entry.releaseTime().compareTo(OLDEST_SUPPORTED_RELEASE) >= 0;
				if (wanted && supported) {
					ids.add(entry.id());
				}
			}
			String previous = version.getValue();
			version.setItems(FXCollections.observableArrayList(ids));
			version.setValue(previous);
		}, err -> Toasts.error("Could not load Minecraft versions: " + Ui.message(err)));
		version.setOnAction(e -> {
			loadLoaders.run();
			updateVantage.run();
		});
		snapshots.selectedProperty().addListener((o, a, b) -> {
			window.context().settings.settings().showSnapshots = b;
			window.context().settings.save();
			loadVersions.run();
		});
		loadVersions.run();
		loadLoaders.run();
		updateVantage.run();

		GridPane grid = new GridPane();
		grid.setHgap(14);
		grid.setVgap(12);
		int row = 0;
		grid.addRow(row++, Ui.label("Name", "muted"), name);
		grid.addRow(row++, Ui.label("Minecraft", "muted"), Ui.row(12, version, Ui.label("Show snapshots", "small", "muted"), snapshots));
		grid.addRow(row++, Ui.label("Fabric loader", "muted"), loader);
		grid.addRow(row++, Ui.label("Memory", "muted"), Ui.row(12, memory, memoryLabel));
		grid.addRow(row++, Ui.label("Java", "muted"), Ui.row(8, javaPath, browseJava, testJava));
		grid.addRow(row++, Ui.label("JVM arguments", "muted"), jvmArgs);
		grid.addRow(row++, Ui.label("Resolution", "muted"), Ui.row(10, customResolution, width, Ui.label("×", "muted"), height,
			Ui.label("Fullscreen", "small", "muted"), fullscreen));
		grid.addRow(row++, Ui.label("Game directory", "muted"), Ui.row(8, gameDir, browseDir));
		grid.addRow(row, Ui.label("Vantage client", "muted"), Ui.row(10, vantage, vantageNote));
		GridPane.setHgrow(name, Priority.ALWAYS);

		Button cancel = Ui.button("Cancel", null, "ghost");
		Button save = Ui.button(creating ? "Create" : "Save", Icons.CHECK, "primary");
		HBox buttons = new HBox(10);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		if (!creating) {
			Button delete = Ui.button("Delete", Icons.DELETE, "danger");
			delete.setOnAction(e -> Overlay.confirm("Delete installation?",
				"\"" + instance.name + "\" is removed from the launcher. Its game folder (worlds, mods, settings) stays on disk.",
				"Delete", true, () -> {
					try {
						window.context().settings.deleteInstance(instance);
						Toasts.success("Installation deleted.");
						onShow();
					} catch (IllegalStateException ex) {
						Toasts.error(ex.getMessage());
					}
				}));
			Button repair = Ui.button("Repair files", Icons.REFRESH, "ghost");
			repair.setOnAction(e -> repair(instance));
			Button folder = Ui.button("Open folder", Icons.FOLDER, "ghost");
			folder.setOnAction(e -> Ui.openFolder(instance.resolveGameDirectory(window.context().paths.instances())));
			buttons.getChildren().addAll(delete, repair, folder);
		}
		buttons.getChildren().addAll(Ui.spacer(), cancel, save);

		dialog.getChildren().addAll(Ui.label(creating ? "New installation" : "Edit installation", "h2"), grid, buttons);
		Runnable close = Overlay.show(dialog, 760);
		cancel.setOnAction(e -> close.run());
		save.setOnAction(e -> {
			if (name.getText().isBlank() || version.getValue() == null) {
				Toasts.error("Name and Minecraft version are required.");
				return;
			}
			String folderProblem = checkGameDirectory(gameDir.getText().trim());
			if (folderProblem != null) {
				Toasts.error(folderProblem);
				return;
			}
			String sharedWith = sharedGameDirectory(instance, gameDir.getText().trim());
			if (sharedWith != null) {
				Toasts.info("Note: \"" + sharedWith + "\" uses the same game folder, so both share worlds, mods and settings.");
			}
			long memoryMb = Math.round(memory.getValue() / 256) * 256;
			if (memoryMb > totalMemoryMb() * 3 / 4) {
				Toasts.info("More than 75% of your computer's memory is assigned to Minecraft. If the system gets slow, lower it.");
			}
			Instance target = creating ? window.context().settings.createInstance(name.getText().trim(), version.getValue()) : instance;
			target.name = name.getText().trim();
			target.minecraftVersion = version.getValue();
			target.loaderVersion = LATEST_LOADER.equals(loader.getValue()) || loader.getValue() == null ? "" : loader.getValue();
			target.memoryMb = (int) (Math.round(memory.getValue() / 256) * 256);
			target.javaPath = javaPath.getText().trim();
			target.extraJvmArgs = jvmArgs.getText().trim();
			target.customResolution = customResolution.isSelected();
			target.width = width.getValue();
			target.height = height.getValue();
			target.fullscreen = fullscreen.isSelected();
			target.gameDirectory = gameDir.getText().trim();
			target.vantageClient = vantage.isSelected() && target.supportsVantageClient();
			window.context().settings.updateInstance(target);
			close.run();
			Toasts.success(creating ? "Installation created." : "Installation saved.");
			onShow();
		});
	}

	/** Null if {@code text} is empty (default folder) or a usable folder; otherwise what is wrong with it. */
	static String checkGameDirectory(String text) {
		if (text.isEmpty()) {
			return null;
		}
		Path dir;
		try {
			dir = Path.of(text);
		} catch (java.nio.file.InvalidPathException e) {
			return "The game folder path is not valid: " + e.getReason() + ".";
		}
		if (!dir.isAbsolute()) {
			return "Choose a complete game folder path (for example with Browse).";
		}
		if (java.nio.file.Files.exists(dir) && !java.nio.file.Files.isDirectory(dir)) {
			return "The game folder path points to a file, not a folder.";
		}
		Path existing = dir;
		while (existing != null && !java.nio.file.Files.exists(existing)) {
			existing = existing.getParent();
		}
		if (existing == null || !java.nio.file.Files.isWritable(existing)) {
			return "Vantage cannot write to " + (existing == null ? dir : existing) + ". Choose another game folder.";
		}
		return null;
	}

	/** Name of another installation that would use the same game folder, or null. */
	private String sharedGameDirectory(Instance editing, String text) {
		Path instancesRoot = window.context().paths.instances();
		Instance probe = editing.copy();
		probe.gameDirectory = text;
		Path target = probe.resolveGameDirectory(instancesRoot).toAbsolutePath().normalize();
		for (Instance other : window.context().settings.instances()) {
			if (!other.id.equals(editing.id) && other.resolveGameDirectory(instancesRoot).toAbsolutePath().normalize().equals(target)) {
				return other.name;
			}
		}
		return null;
	}

	private void repair(Instance instance) {
		var progress = window.taskBar().begin("Verifying " + instance.name, true);
		Ui.async(() -> {
			GameMeta meta = window.context().meta;
			String loader = instance.loaderVersion.isBlank() ? meta.latestLoader(instance.minecraftVersion) : instance.loaderVersion;
			Path java = instance.javaPath.isBlank() ? null : Path.of(instance.javaPath);
			return window.context().installer.repair(instance.minecraftVersion, loader, java,
				window.context().settings.settings().downloadThreads, progress);
		}, game -> {
			progress.finish();
			Toasts.success("All game files verified.");
		}, err -> {
			progress.finish();
			if (err instanceof dev.vantage.common.util.CancelledException) {
				Toasts.info("Repair cancelled.");
				return;
			}
			Overlay.error("Repair failed", err);
		});
	}

	private static long totalMemoryMb() {
		try {
			var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			return os.getTotalMemorySize() / (1024 * 1024);
		} catch (ClassCastException | LinkageError e) {
			return 8192;
		}
	}
}
