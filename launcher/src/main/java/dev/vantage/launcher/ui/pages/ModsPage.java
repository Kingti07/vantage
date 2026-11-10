package dev.vantage.launcher.ui.pages;

import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.mods.ModProblem;
import dev.vantage.common.util.FileUtil;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Overlay;
import dev.vantage.launcher.ui.Toasts;
import dev.vantage.launcher.ui.Ui;
import dev.vantage.launcher.ui.WebImages;
import dev.vantage.launcher.ui.pages.mods.InstalledModsView;
import dev.vantage.launcher.ui.pages.mods.ModBrowserView;
import dev.vantage.launcher.ui.pages.mods.PerformancePackView;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mod management for the selected installation: installed mods, the Modrinth browser and the performance pack.
 * Also hosts the shared "plan → confirm → install" flow used by every install button.
 */
public final class ModsPage implements Page {
	private final MainWindow window;
	private final VBox root = new VBox();
	private final ComboBox<Instance> instancePicker = new ComboBox<>();
	private final StackPane body = new StackPane();
	private final Map<String, Button> segments = new LinkedHashMap<>();
	private final InstalledModsView installed;
	private final ModBrowserView browse;
	private final PerformancePackView performance;
	private String active = "installed";

	public ModsPage(MainWindow window) {
		this.window = window;
		root.getStyleClass().add("page");
		installed = new InstalledModsView(this);
		browse = new ModBrowserView(this);
		performance = new PerformancePackView(this);

		instancePicker.setPrefWidth(240);
		instancePicker.setOnAction(e -> {
			if (instancePicker.getValue() != null) {
				window.context().settings.select(instancePicker.getValue());
				refreshActive();
			}
		});
		HBox segmented = new HBox();
		segmented.getStyleClass().add("segmented");
		segmented.getChildren().addAll(segment("installed", "Installed"), segment("browse", "Browse Modrinth"), segment("performance", "Performance pack"));
		HBox header = Ui.row(12, new VBox(4, Ui.label("Mods", "h1"), Ui.label("Install, update and manage mods for an installation.", "muted")),
			Ui.spacer(), new VBox(6, Ui.label("Installation", "small", "muted"), instancePicker));
		VBox.setVgrow(body, Priority.ALWAYS);
		root.getChildren().addAll(header, segmented, body);
		select("installed");
	}

	private Button segment(String id, String text) {
		Button button = new Button(text);
		button.getStyleClass().setAll("segment");
		button.setOnAction(e -> select(id));
		segments.put(id, button);
		return button;
	}

	private void select(String id) {
		active = id;
		segments.forEach((key, button) -> {
			button.getStyleClass().remove("active");
			if (key.equals(id)) {
				button.getStyleClass().add("active");
			}
		});
		Node view = switch (id) {
			case "browse" -> browse.view();
			case "performance" -> performance.view();
			default -> installed.view();
		};
		body.getChildren().setAll(view);
		refreshActive();
	}

	private void refreshActive() {
		switch (active) {
			case "browse" -> browse.onShow();
			case "performance" -> performance.onShow();
			default -> installed.onShow();
		}
	}

	public void showBrowse() {
		select("browse");
	}

	public void showPerformance() {
		select("performance");
	}

	public void showInstalled() {
		select("installed");
	}

	public MainWindow window() {
		return window;
	}

	public Instance instance() {
		Instance value = instancePicker.getValue();
		return value != null ? value : window.context().settings.selectedInstance();
	}

	public ModManager manager() {
		return window.context().modManager(instance());
	}

	@Override
	public Node view() {
		return root;
	}

	@Override
	public void onShow() {
		var settings = window.context().settings;
		instancePicker.setItems(FXCollections.observableArrayList(settings.instances()));
		instancePicker.setValue(settings.selectedInstance());
		refreshActive();
	}

	// ------------------------------------------------------------------------------------------------ install flow

	/** Resolves dependencies, shows the plan and installs on confirmation. */
	public void install(String projectId, String versionId, String title, Runnable afterInstall) {
		ModManager manager = manager();
		var progress = window.taskBar().begin("Resolving " + title);
		Ui.async(() -> manager.plan(projectId, versionId), plan -> {
			progress.finish();
			showPlan(manager, plan, afterInstall);
		}, err -> {
			progress.finish();
			Overlay.error("Cannot install " + title, err);
		});
		progress.status("Resolving dependencies for " + title);
	}

	private void showPlan(ModManager manager, InstallPlan plan, Runnable afterInstall) {
		VBox dialog = new VBox(14);
		dialog.getStyleClass().add("dialog");
		String title = plan.rootProject() != null ? plan.rootProject().title() : "mod";
		dialog.getChildren().add(Ui.label("Install " + title, "h2"));
		if (plan.root() != null) {
			dialog.getChildren().add(Ui.label("Version " + plan.root().versionNumber() + " for Fabric " + manager.gameVersion(), "muted"));
		}

		VBox files = new VBox(8);
		for (InstallPlan.PlannedFile file : plan.toInstall()) {
			ImageView icon = WebImages.rounded(32, 8);
			WebImages.load(icon, file.iconUrl(), 64);
			Label name = Ui.label(file.title(), "h3");
			Label version = Ui.label(file.version().versionNumber(), "small", "muted");
			HBox row = Ui.row(10, icon, new VBox(2, name, version), Ui.spacer(),
				MainWindow.badge(file.dependency() ? "Dependency" : "Requested", file.dependency() ? null : "accent"));
			row.getStyleClass().add("inset");
			files.getChildren().add(row);
		}
		if (!files.getChildren().isEmpty()) {
			dialog.getChildren().add(files);
		}
		if (!plan.satisfied().isEmpty()) {
			dialog.getChildren().add(Ui.label("Already installed: " + String.join(", ", plan.satisfied()), "small", "success-text"));
		}
		if (!plan.replaces().isEmpty()) {
			dialog.getChildren().add(Ui.label("Replaces: " + String.join(", ", plan.replaces().stream().map(m -> m.file().getFileName().toString()).toList()),
				"small", "muted"));
		}
		for (ModProblem problem : plan.problems()) {
			Node icon = Icons.of(Icons.WARNING, 16);
			icon.lookupAll(".icon").forEach(n -> n.setStyle(problem.isError() ? "-fx-fill: -v-danger;" : "-fx-fill: -v-warning;"));
			Label text = Ui.label(problem.message(), problem.isError() ? "danger-text" : "warning-text");
			text.setWrapText(true);
			text.setMaxWidth(480);
			dialog.getChildren().add(Ui.row(8, icon, text));
		}
		if (plan.canInstall() && !plan.toInstall().isEmpty()) {
			dialog.getChildren().add(Ui.label("Download size: " + FileUtil.humanSize(plan.downloadSize()), "small", "faint"));
		}

		Button cancel = Ui.button(plan.canInstall() && !plan.toInstall().isEmpty() ? "Cancel" : "Close", null, "ghost");
		Button install = Ui.button("Install", Icons.DOWNLOAD, "primary");
		install.setDisable(!plan.canInstall() || plan.toInstall().isEmpty());
		HBox buttons = new HBox(10, Ui.spacer(), cancel, install);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		dialog.getChildren().add(buttons);
		ScrollPane scroll = new ScrollPane(dialog);
		scroll.setFitToWidth(true);
		scroll.setMaxHeight(600);
		VBox wrapper = new VBox(scroll);
		wrapper.getStyleClass().add("dialog");
		wrapper.setStyle("-fx-padding: 0;");
		dialog.setStyle("-fx-effect: null; -fx-border-width: 0;");
		Runnable close = Overlay.show(wrapper, 560);
		cancel.setOnAction(e -> close.run());
		install.setOnAction(e -> {
			close.run();
			var progress = window.taskBar().begin("Installing " + title, true);
			Ui.async(() -> manager.execute(plan, progress), paths -> {
				progress.finish();
				Toasts.success("Installed " + title + (plan.toInstall().size() > 1 ? " and " + (plan.toInstall().size() - 1) + " dependenc"
					+ (plan.toInstall().size() == 2 ? "y" : "ies") : "") + ".");
				if (afterInstall != null) {
					afterInstall.run();
				}
			}, err -> {
				progress.finish();
				Overlay.message("Installation failed", Ui.message(err) + "\n\nNo files were left behind.");
			});
		});
	}
}
