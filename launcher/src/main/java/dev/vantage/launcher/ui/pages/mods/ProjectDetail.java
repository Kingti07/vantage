package dev.vantage.launcher.ui.pages.mods;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.modrinth.ModrinthApi;
import dev.vantage.common.modrinth.model.ModrinthModels.GalleryImage;
import dev.vantage.common.modrinth.model.ModrinthModels.Project;
import dev.vantage.common.modrinth.model.ModrinthModels.TeamMember;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import dev.vantage.launcher.ui.Icons;
import dev.vantage.launcher.ui.MainWindow;
import dev.vantage.launcher.ui.Markdown;
import dev.vantage.launcher.ui.Overlay;
import dev.vantage.launcher.ui.Ui;
import dev.vantage.launcher.ui.WebImages;
import dev.vantage.launcher.ui.pages.ModsPage;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Full project page: header, stats, authors, gallery, description and compatible versions. */
final class ProjectDetail {
	private final ModsPage page;
	private final String projectId;
	private final String title;
	private final VBox body = new VBox(16);
	private final StackPane tabContent = new StackPane();
	private Runnable close;

	ProjectDetail(ModsPage page, String projectId, String title) {
		this.page = page;
		this.projectId = projectId;
		this.title = title;
	}

	void show() {
		body.getChildren().add(Ui.label("Loading " + title + "…", "muted"));
		body.setPrefWidth(860);
		ScrollPane scroll = new ScrollPane(body);
		scroll.setFitToWidth(true);
		scroll.setPrefViewportHeight(620);
		VBox dialog = new VBox(scroll);
		dialog.getStyleClass().add("dialog");
		close = Overlay.show(dialog, 920);
		ModrinthApi api = page.window().context().modrinth;
		String gameVersion = page.instance().minecraftVersion;
		Ui.async(() -> {
			Project project = api.project(projectId);
			List<TeamMember> members = api.members(projectId);
			List<Version> versions = api.projectVersions(projectId, VantageInfo.LOADER, gameVersion);
			boolean installed = page.manager().installed().stream().anyMatch(m -> projectId.equals(m.modrinthProjectId())
				|| project.id().equals(m.modrinthProjectId()));
			return new Loaded(project, members, versions, installed);
		}, loaded -> render(loaded.project(), loaded.members(), loaded.versions(), loaded.installed()),
			err -> body.getChildren().setAll(Ui.label("Could not load project: " + Ui.message(err), "danger-text")));
	}

	private record Loaded(Project project, List<TeamMember> members, List<Version> versions, boolean installed) {
	}

	private void render(Project project, List<TeamMember> members, List<Version> versions, boolean installed) {
		body.getChildren().clear();
		String gameVersion = page.instance().minecraftVersion;

		ImageView icon = WebImages.rounded(84, 18);
		WebImages.load(icon, project.iconUrl(), 168);
		Label name = Ui.label(project.title(), "h1");
		Label summary = Ui.label(project.description(), "muted");
		summary.setWrapText(true);
		String authors = String.join(", ", members.stream().sorted((a, b) -> Integer.compare(a.ordering(), b.ordering()))
			.map(m -> m.user().username() + (m.role() != null && !m.role().isBlank() ? " (" + m.role() + ")" : "")).toList());
		Label authorLabel = Ui.label("by " + authors, "small", "muted");
		authorLabel.setWrapText(true);

		HBox stats = new HBox(8, MainWindow.badge(Ui.compactNumber(project.downloads()) + " downloads", null),
			MainWindow.badge(Ui.compactNumber(project.followers()) + " followers", null),
			MainWindow.badge("Updated " + Ui.relativeTime(project.updated()), null));
		if (project.license() != null && project.license().id() != null) {
			stats.getChildren().add(MainWindow.badge(project.license().id(), null));
		}
		stats.getChildren().add(MainWindow.badge("Modrinth has no ratings; followers indicate popularity", null));

		boolean compatible = !versions.isEmpty();
		Button action;
		if (installed) {
			action = Ui.button("Installed", Icons.CHECK, "ghost");
			action.setDisable(true);
		} else {
			action = Ui.button(compatible ? "Install" : "Not available for Fabric " + gameVersion, Icons.DOWNLOAD, "primary");
			action.setDisable(!compatible);
			action.setOnAction(e -> {
				close.run();
				page.install(project.id(), null, project.title(), null);
			});
		}
		Button closeButton = Ui.iconButton(Icons.CLOSE, "Close", "ghost");
		closeButton.setOnAction(e -> close.run());
		VBox headerText = new VBox(6, name, authorLabel, summary, stats);
		HBox.setHgrow(headerText, Priority.ALWAYS);
		HBox header = new HBox(18, icon, headerText, new VBox(10, closeButton, action));
		header.setAlignment(Pos.TOP_LEFT);

		HBox links = new HBox(14);
		addLink(links, "Modrinth page", "https://modrinth.com/mod/" + project.slug());
		addLink(links, "Source", project.sourceUrl());
		addLink(links, "Issues", project.issuesUrl());
		addLink(links, "Wiki", project.wikiUrl());
		addLink(links, "Discord", project.discordUrl());

		HBox tabs = new HBox();
		tabs.getStyleClass().add("segmented");
		tabs.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		Button description = tab("Description", tabs);
		Button gallery = tab("Gallery (" + (project.gallery() == null ? 0 : project.gallery().size()) + ")", tabs);
		Button versionTab = tab("Versions (" + versions.size() + " for " + gameVersion + ")", tabs);
		description.setOnAction(e -> select(tabs, description, Markdown.render(project.body(), 820)));
		gallery.setOnAction(e -> select(tabs, gallery, gallery(project.gallery())));
		versionTab.setOnAction(e -> select(tabs, versionTab, versions(project, versions, installed)));
		select(tabs, description, Markdown.render(project.body(), 820));

		body.getChildren().addAll(header, links, tabs, tabContent);
	}

	private static Button tab(String text, HBox tabs) {
		Button button = new Button(text);
		button.getStyleClass().setAll("segment");
		tabs.getChildren().add(button);
		return button;
	}

	private void select(HBox tabs, Button active, Node content) {
		tabs.getChildren().forEach(n -> n.getStyleClass().remove("active"));
		active.getStyleClass().add("active");
		tabContent.getChildren().setAll(content);
	}

	private static void addLink(HBox links, String text, String url) {
		if (url == null || url.isBlank()) {
			return;
		}
		Hyperlink link = new Hyperlink(text);
		link.setOnAction(e -> Ui.openUrl(url));
		links.getChildren().add(link);
	}

	private Node gallery(List<GalleryImage> images) {
		VBox box = new VBox(14);
		if (images == null || images.isEmpty()) {
			box.getChildren().add(Ui.label("This project has no screenshots.", "muted"));
			return box;
		}
		javafx.scene.layout.FlowPane grid = new javafx.scene.layout.FlowPane(12, 12);
		for (GalleryImage image : images) {
			ImageView view = new ImageView();
			view.setFitWidth(260);
			view.setFitHeight(150);
			view.setPreserveRatio(true);
			WebImages.load(view, image.url(), 520);
			VBox tile = new VBox(6, view, Ui.label(image.title() != null ? image.title() : "", "small", "muted"));
			tile.getStyleClass().add("inset");
			tile.setStyle("-fx-cursor: hand;");
			tile.setOnMouseClicked(e -> enlarge(image));
			grid.getChildren().add(tile);
		}
		box.getChildren().add(grid);
		return box;
	}

	private void enlarge(GalleryImage image) {
		ImageView view = new ImageView();
		view.setPreserveRatio(true);
		view.setFitWidth(1000);
		view.setFitHeight(620);
		WebImages.load(view, image.rawUrl() != null ? image.rawUrl() : image.url(), 1400);
		Label caption = Ui.label((image.title() != null ? image.title() : "") + (image.description() != null ? " – " + image.description() : ""), "muted");
		caption.setWrapText(true);
		VBox box = new VBox(10, view, caption);
		box.getStyleClass().add("dialog");
		Overlay.show(box, 1060);
	}

	private Node versions(Project project, List<Version> versions, boolean installed) {
		VBox list = new VBox(8);
		if (versions.isEmpty()) {
			list.getChildren().add(Ui.label("No version of this mod supports Fabric on Minecraft " + page.instance().minecraftVersion
				+ ". Supported loaders: " + String.join(", ", project.loaders() == null ? List.of() : project.loaders()) + ".", "muted"));
			return list;
		}
		for (Version version : versions.stream().limit(40).toList()) {
			Label name = Ui.label(version.versionNumber() + (version.name() != null && !version.name().equals(version.versionNumber()) ? " · " + version.name() : ""), "h3");
			Label meta = Ui.label(String.join(", ", version.loaders()) + " · " + Ui.relativeTime(version.datePublished())
				+ " · " + Ui.compactNumber(version.downloads()) + " downloads", "small", "muted");
			Label type = MainWindow.badge(version.versionType(), "release".equals(version.versionType()) ? "success" : "warning");
			Button install = Ui.button("Install", Icons.DOWNLOAD, "ghost");
			install.setDisable(installed);
			install.setOnAction(e -> {
				close.run();
				page.install(project.id(), version.id(), project.title(), null);
			});
			HBox row = Ui.row(12, new VBox(3, name, meta), Ui.spacer(), type, install);
			row.getStyleClass().add("inset");
			list.getChildren().add(row);
		}
		return list;
	}
}
