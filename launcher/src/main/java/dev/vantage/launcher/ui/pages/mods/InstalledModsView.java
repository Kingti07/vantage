package dev.vantage.launcher.ui.pages.mods;

import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.mods.InstalledMod;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.mods.ModProblem;
import dev.vantage.common.mods.ModScanner;
import dev.vantage.common.mods.PendingOperations;
import dev.vantage.common.util.FileUtil;
import dev.vantage.common.util.ImageDecoder;
import dev.vantage.launcher.game.LaunchService;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Overlay;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.ToggleSwitch;
import dev.vantage.launcher.ui.Ui;
import dev.vantage.launcher.ui.WebImages;
import dev.vantage.launcher.ui.pages.ModsPage;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Installed mods of the selected installation with enable/disable, updates, removal and problem detection. */
public final class InstalledModsView {
	private final ModsPage page;
	private final VBox root = new VBox(14);
	private final VBox problems = new VBox(6);
	private final VBox list = new VBox(8);
	private final Label summary = Ui.label("", "muted");
	private final TextField filter = new TextField();
	private final Button updateAll = Ui.button("Update all", Icons.UPGRADE, "primary");
	private List<InstalledMod> mods = List.of();
	private Map<InstalledMod, Version> updates = new HashMap<>();

	public InstalledModsView(ModsPage page) {
		this.page = page;
		Button refresh = Ui.iconButton(Icons.REFRESH, "Rescan mods folder", "ghost");
		refresh.setOnAction(e -> onShow());
		Button check = Ui.button("Check for updates", Icons.REFRESH, "ghost");
		check.setOnAction(e -> checkUpdates());
		updateAll.setVisible(false);
		updateAll.setManaged(false);
		updateAll.setOnAction(e -> updateAll());
		Button folder = Ui.button("Open folder", Icons.FOLDER, "ghost");
		folder.setOnAction(e -> Ui.openFolder(page.manager().modsDir()));
		Button add = Ui.button("Add jar", Icons.ADD, "ghost");
		add.setOnAction(e -> addJar());
		filter.setPromptText("Filter installed mods");
		filter.textProperty().addListener((o, a, b) -> render());
		filter.setPrefWidth(220);
		HBox toolbar = Ui.row(10, summary, Ui.spacer(), filter, refresh, add, folder, check, updateAll);
		ScrollPane scroll = new ScrollPane(list);
		scroll.setFitToWidth(true);
		VBox.setVgrow(scroll, Priority.ALWAYS);
		root.getChildren().addAll(toolbar, problems, scroll);
	}

	public Node view() {
		return root;
	}

	public void onShow() {
		ModManager manager = page.manager();
		summary.setText("Scanning…");
		Ui.async(() -> {
			List<InstalledMod> found = manager.installed();
			if (manager.identify(found) > 0) {
				found = manager.installed();
			}
			return found;
		}, found -> {
			mods = found;
			updates = new HashMap<>();
			updateAll.setVisible(false);
			updateAll.setManaged(false);
			render();
			checkUpdatesSilently();
		}, err -> {
			summary.setText("Could not read the mods folder");
			Toasts.error(Ui.message(err));
		});
	}

	private void render() {
		ModManager manager = page.manager();
		long enabled = mods.stream().filter(InstalledMod::effectiveEnabled).count();
		summary.setText(mods.size() + " mods · " + enabled + " enabled" + (updates.isEmpty() ? "" : " · " + updates.size() + " update(s)"));

		problems.getChildren().clear();
		List<ModProblem> found = manager.analyze(mods);
		if (!found.isEmpty()) {
			VBox card = new VBox(6);
			card.getStyleClass().add("card");
			long errors = found.stream().filter(ModProblem::isError).count();
			card.getChildren().add(Ui.label(errors > 0 ? errors + " problem(s) will prevent the game from starting" : "Warnings", "h3",
				errors > 0 ? "danger-text" : "warning-text"));
			for (ModProblem problem : found) {
				Label text = Ui.label((problem.fileName() != null ? problem.fileName() + ": " : "") + problem.message(),
					"small", problem.isError() ? "danger-text" : "warning-text");
				text.setWrapText(true);
				card.getChildren().add(text);
			}
			problems.getChildren().add(card);
		}

		list.getChildren().clear();
		String query = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
		for (InstalledMod mod : mods) {
			if (!query.isEmpty() && !mod.displayName().toLowerCase(Locale.ROOT).contains(query)
				&& !mod.baseFileName().toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			list.getChildren().add(row(mod));
		}
		if (mods.isEmpty()) {
			VBox empty = new VBox(8, Ui.label("No mods installed", "h3"),
				Ui.label("Browse Modrinth to add mods. The Vantage client and Fabric API are installed automatically on launch.", "muted"));
			Button browse = Ui.button("Browse Modrinth", Icons.SEARCH, "primary");
			browse.setOnAction(e -> page.showBrowse());
			empty.getChildren().add(browse);
			empty.getStyleClass().add("card");
			list.getChildren().add(empty);
		}
	}

	private Node row(InstalledMod mod) {
		ModManager manager = page.manager();
		boolean bundled = LaunchService.CLIENT_JAR_NAME.equals(mod.baseFileName());
		ImageView icon = WebImages.rounded(40, 10);
		if (mod.indexEntry() != null && mod.indexEntry().iconUrl() != null) {
			WebImages.load(icon, mod.indexEntry().iconUrl(), 80);
		} else if (mod.metadata() != null && mod.metadata().iconPath() != null) {
			Ui.async(() -> {
				byte[] bytes = ModScanner.readEntry(mod.file(), mod.metadata().iconPath());
				return bytes == null ? null : WebImages.toFx(ImageDecoder.decode(bytes, 80));
			}, image -> {
				if (image != null) {
					icon.setImage(image);
				}
			}, err -> {
			});
		}

		Label name = Ui.label(mod.displayName(), "h3");
		HBox badges = new HBox(6);
		badges.setAlignment(Pos.CENTER_LEFT);
		badges.getChildren().add(Ui.label(mod.displayVersion(), "small", "muted"));
		if (bundled) {
			badges.getChildren().add(MainWindow.badge("Vantage client", "accent"));
		} else if (mod.indexEntry() != null) {
			badges.getChildren().add(MainWindow.badge(mod.indexEntry().dependency() ? "Modrinth · dependency" : "Modrinth", null));
		} else {
			badges.getChildren().add(MainWindow.badge("Manual", null));
		}
		if (!"fabric".equals(mod.loader())) {
			badges.getChildren().add(MainWindow.badge(mod.loader().toUpperCase(Locale.ROOT), "danger"));
		}
		if (mod.pending() != null) {
			badges.getChildren().add(MainWindow.badge(switch (mod.pending()) {
				case DELETE -> "Removed on next launch";
				case ENABLE -> "Enabled on next launch";
				case DISABLE -> "Disabled on next launch";
			}, "warning"));
		}
		Label file = Ui.label(mod.file().getFileName() + " · " + FileUtil.humanSize(mod.size()), "small", "faint");

		HBox actions = new HBox(8);
		actions.setAlignment(Pos.CENTER_RIGHT);
		Version update = updates.get(mod);
		if (update != null && !bundled) {
			Button updateButton = Ui.button("Update to " + update.versionNumber(), Icons.UPGRADE, "primary");
			updateButton.setOnAction(e -> page.install(update.projectId(), update.id(), mod.displayName(), this::onShow));
			actions.getChildren().add(updateButton);
		}
		if (!bundled) {
			ToggleSwitch toggle = new ToggleSwitch(mod.effectiveEnabled());
			toggle.selectedProperty().addListener((o, was, now) -> Ui.async(() -> {
				manager.setEnabled(mod, now);
				return null;
			}, ok -> onShow(), err -> Toasts.error(Ui.message(err))));
			Button remove = Ui.iconButton(Icons.DELETE, "Remove mod", "ghost");
			remove.setDisable(mod.pending() == PendingOperations.Action.DELETE);
			remove.setOnAction(e -> {
				List<InstalledMod> dependents = manager.dependents(mod, mods);
				String warning = dependents.isEmpty() ? "" : "\n\nRequired by: " + String.join(", ", dependents.stream().map(InstalledMod::displayName).toList());
				Overlay.confirm("Remove " + mod.displayName() + "?", "The jar is deleted from the mods folder." + warning, "Remove", true,
					() -> Ui.async(() -> {
						manager.remove(mod);
						return null;
					}, ok -> {
						Toasts.success("Removed " + mod.displayName() + ".");
						onShow();
					}, err -> Toasts.error(Ui.message(err))));
			});
			actions.getChildren().addAll(toggle, remove);
		} else {
			actions.getChildren().add(Ui.label("Managed by the launcher", "small", "faint"));
		}

		VBox text = new VBox(3, name, badges, file);
		HBox row = new HBox(14, icon, text, Ui.spacer(), actions);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("card");
		row.setStyle("-fx-padding: 12 16 12 16;");
		if (!mod.effectiveEnabled()) {
			text.setOpacity(0.5);
			icon.setOpacity(0.5);
		}
		return row;
	}

	/** Automatic update detection whenever the list is opened; only speaks up when updates exist. */
	private void checkUpdatesSilently() {
		List<InstalledMod> snapshot = mods;
		if (snapshot.isEmpty()) {
			return;
		}
		Ui.async(() -> page.manager().findUpdates(snapshot), found -> {
			if (snapshot != mods || found.isEmpty()) {
				return;
			}
			updates = new HashMap<>(found);
			updateAll.setVisible(true);
			updateAll.setManaged(true);
			render();
		}, err -> {
		});
	}

	private void checkUpdates() {
		ModManager manager = page.manager();
		var progress = page.window().taskBar().begin("Checking for mod updates");
		Ui.async(() -> manager.findUpdates(mods), found -> {
			progress.finish();
			updates = new HashMap<>(found);
			boolean any = !updates.isEmpty();
			updateAll.setVisible(any);
			updateAll.setManaged(any);
			Toasts.info(any ? updates.size() + " update(s) available." : "All mods are up to date.");
			render();
		}, err -> {
			progress.finish();
			Toasts.error("Update check failed: " + Ui.message(err));
		});
		progress.progress(-1);
	}

	private void updateAll() {
		ModManager manager = page.manager();
		Map<InstalledMod, Version> todo = new HashMap<>(updates);
		var progress = page.window().taskBar().begin("Updating " + todo.size() + " mod(s)", true);
		Ui.async(() -> {
			int done = 0;
			StringBuilder failures = new StringBuilder();
			for (Map.Entry<InstalledMod, Version> entry : todo.entrySet()) {
				var plan = manager.plan(entry.getValue().projectId(), entry.getValue().id());
				if (plan.canInstall() && !plan.toInstall().isEmpty()) {
					manager.execute(plan, progress);
					done++;
				} else {
					failures.append(entry.getKey().displayName()).append(": ")
						.append(plan.problems().stream().filter(ModProblem::isError).map(ModProblem::message).findFirst().orElse("nothing to do"))
						.append('\n');
				}
			}
			return done + (failures.isEmpty() ? "" : "\n" + failures);
		}, result -> {
			progress.finish();
			String[] parts = result.split("\n", 2);
			Toasts.success("Updated " + parts[0] + " mod(s).");
			if (parts.length > 1 && !parts[1].isBlank()) {
				Overlay.message("Some mods were not updated", parts[1]);
			}
			onShow();
		}, err -> {
			progress.finish();
			Overlay.message("Update failed", Ui.message(err));
		});
	}

	private void addJar() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Add mod jar");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Mod jars", "*.jar"));
		List<File> files = chooser.showOpenMultipleDialog(page.window().stage());
		if (files == null || files.isEmpty()) {
			return;
		}
		Path modsDir = page.manager().modsDir();
		Ui.async(() -> {
			Files.createDirectories(modsDir);
			for (File file : files) {
				Files.copy(file.toPath(), modsDir.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
			}
			return files.size();
		}, count -> {
			Toasts.success("Added " + count + " jar(s). Compatibility is checked below.");
			onShow();
		}, err -> Toasts.error(Ui.message(err)));
	}
}
