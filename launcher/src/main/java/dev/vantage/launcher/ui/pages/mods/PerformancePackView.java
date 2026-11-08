package dev.vantage.launcher.ui.pages.mods;

import dev.vantage.common.modrinth.model.ModrinthModels.Project;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.InstalledMod;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.mods.ModProblem;
import dev.vantage.common.mods.RecommendedMods;
import dev.vantage.common.util.ProgressListener;
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
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-click install of well-known performance mods from Modrinth. Each mod is only offered when Modrinth has a
 * version for the installation's Minecraft version and Fabric.
 */
public final class PerformancePackView {
	private record Status(Project project, Version version, boolean installed) {
	}

	private final ModsPage page;
	private final VBox root = new VBox(14);
	private final VBox list = new VBox(8);
	private final Map<RecommendedMods.Recommendation, ToggleSwitch> toggles = new LinkedHashMap<>();
	private final Map<RecommendedMods.Recommendation, Status> statuses = new LinkedHashMap<>();
	private final Button installSelected = Ui.button("Install selected", Icons.DOWNLOAD, "primary");

	public PerformancePackView(ModsPage page) {
		this.page = page;
		installSelected.setOnAction(e -> installSelected());
		Label intro = Ui.label("These independent mods are downloaded from Modrinth under their own licenses. Vantage's own performance "
			+ "modules (dynamic FPS, particle limits, entity distance culling, FPS/memory monitor) work with or without them.", "muted");
		intro.setWrapText(true);
		ScrollPane scroll = new ScrollPane(list);
		scroll.setFitToWidth(true);
		VBox.setVgrow(scroll, Priority.ALWAYS);
		HBox.setHgrow(intro, Priority.ALWAYS);
		intro.setMaxWidth(Double.MAX_VALUE);
		root.getChildren().addAll(Ui.row(12, intro, installSelected), scroll);
	}

	public Node view() {
		return root;
	}

	public void onShow() {
		list.getChildren().setAll(Ui.label("Checking compatibility with Minecraft " + page.instance().minecraftVersion + "…", "muted"));
		ModManager manager = page.manager();
		Ui.async(() -> {
			List<InstalledMod> installed = manager.installed();
			Map<RecommendedMods.Recommendation, Status> result = new LinkedHashMap<>();
			for (RecommendedMods.Recommendation rec : RecommendedMods.PERFORMANCE) {
				try {
					Project project = manager.api().project(rec.slug());
					Version version = manager.bestVersion(project.id());
					boolean present = installed.stream().anyMatch(m -> project.id().equals(m.modrinthProjectId()) && m.effectiveEnabled());
					result.put(rec, new Status(project, version, present));
				} catch (Exception e) {
					result.put(rec, new Status(null, null, false));
				}
			}
			return result;
		}, result -> {
			statuses.clear();
			statuses.putAll(result);
			render();
		}, err -> list.getChildren().setAll(Ui.label("Could not reach Modrinth: " + Ui.message(err), "danger-text")));
	}

	private void render() {
		list.getChildren().clear();
		toggles.clear();
		for (Map.Entry<RecommendedMods.Recommendation, Status> entry : statuses.entrySet()) {
			RecommendedMods.Recommendation rec = entry.getKey();
			Status status = entry.getValue();
			ImageView icon = WebImages.rounded(44, 10);
			if (status.project() != null) {
				WebImages.load(icon, status.project().iconUrl(), 88);
			}
			Label badge;
			ToggleSwitch toggle = new ToggleSwitch(false);
			if (status.installed()) {
				badge = MainWindow.badge("Installed", "success");
				toggle.setDisable(true);
			} else if (status.version() == null) {
				badge = MainWindow.badge("Not available for " + page.instance().minecraftVersion, "warning");
				toggle.setDisable(true);
			} else {
				badge = MainWindow.badge(status.version().versionNumber(), null);
				toggle.setSelected(rec.defaultSelected());
				toggles.put(rec, toggle);
			}
			Label purpose = Ui.label(rec.purpose(), "muted");
			HBox row = new HBox(14, icon, new VBox(3, Ui.label(rec.title(), "h3"), purpose), Ui.spacer(), badge, toggle);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("card");
			row.setStyle("-fx-padding: 12 16 12 16;");
			list.getChildren().add(row);
		}
		installSelected.setDisable(toggles.isEmpty());
	}

	private void installSelected() {
		List<Status> chosen = new ArrayList<>();
		toggles.forEach((rec, toggle) -> {
			if (toggle.isSelected()) {
				chosen.add(statuses.get(rec));
			}
		});
		if (chosen.isEmpty()) {
			Toasts.info("Select at least one mod.");
			return;
		}
		ModManager manager = page.manager();
		ProgressListener progress = page.window().taskBar().begin("Installing performance pack");
		Ui.async(() -> {
			List<String> installed = new ArrayList<>();
			List<String> skipped = new ArrayList<>();
			for (Status status : chosen) {
				InstallPlan plan = manager.plan(status.project().id(), null);
				if (plan.canInstall() && !plan.toInstall().isEmpty()) {
					manager.execute(plan, progress);
					installed.add(status.project().title());
				} else {
					skipped.add(status.project().title() + ": " + plan.problems().stream()
						.filter(ModProblem::isError).map(ModProblem::message).findFirst().orElse("already installed"));
				}
			}
			return Map.entry(installed, skipped);
		}, result -> {
			page.window().taskBar().finish();
			if (!result.getKey().isEmpty()) {
				Toasts.success("Installed " + String.join(", ", result.getKey()) + ".");
			}
			if (!result.getValue().isEmpty()) {
				Overlay.message("Some mods were skipped", String.join("\n", result.getValue()));
			}
			onShow();
		}, err -> {
			page.window().taskBar().finish();
			Overlay.message("Installation failed", Ui.message(err));
		});
	}
}
