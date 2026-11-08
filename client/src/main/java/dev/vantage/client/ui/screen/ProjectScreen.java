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
import dev.vantage.client.ui.widget.Widget;
import dev.vantage.common.VantageInfo;
import dev.vantage.common.modrinth.model.ModrinthModels.GalleryImage;
import dev.vantage.common.modrinth.model.ModrinthModels.Project;
import dev.vantage.common.modrinth.model.ModrinthModels.TeamMember;
import dev.vantage.common.modrinth.model.ModrinthModels.Version;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

/** Modrinth project page: description, screenshots, authors, statistics and compatible versions. */
public final class ProjectScreen extends VScreen {
	private enum Tab {DESCRIPTION, GALLERY, VERSIONS}

	private record Loaded(Project project, List<TeamMember> members, List<Version> versions, boolean installed) {
	}

	private final String projectId;
	private final String title;
	private Loaded data;
	private String error;
	private Tab tab = Tab.DESCRIPTION;
	private ScrollPanel body;

	public ProjectScreen(Screen parent, String projectId, String title) {
		super(title, parent);
		this.projectId = projectId;
		this.title = title;
		ModService mods = VantageClient.get().mods();
		mods.run(() -> {
			Project project = mods.api().project(projectId);
			List<TeamMember> members = mods.api().members(projectId);
			List<Version> versions = mods.api().projectVersions(projectId, VantageInfo.LOADER, mods.gameVersion());
			boolean installed = mods.manager().installed().stream().anyMatch(m -> project.id().equals(m.modrinthProjectId()));
			return new Loaded(project, members, versions, installed);
		}, loaded -> {
			data = loaded;
			rebuildWidgets();
		}, err -> error = ModService.message(err));
	}

	@Override
	protected void build() {
		add(new Button(width - 120, height - 28, 100, 18, () -> "Back", Button.Style.SECONDARY, this::onClose));
		if (data == null) {
			return;
		}
		Project project = data.project();
		boolean compatible = !data.versions().isEmpty();
		Button install = add(new Button(width - 120, 20, 100, 20, () -> data.installed() ? "Installed" : compatible ? "Install" : "Unavailable",
			Button.Style.PRIMARY, () -> InstallPopup.open(this, project.id(), null, project.title(), null)));
		install.enabled = compatible && !data.installed();
		add(new Button(width - 120, 44, 100, 16, () -> "Open on Modrinth", Button.Style.GHOST,
			() -> Util.getPlatform().openUri(URI.create("https://modrinth.com/mod/" + project.slug()))));
		int tx = 20;
		for (Tab t : Tab.values()) {
			String label = switch (t) {
				case DESCRIPTION -> "Description";
				case GALLERY -> "Gallery (" + (project.gallery() == null ? 0 : project.gallery().size()) + ")";
				case VERSIONS -> "Versions (" + data.versions().size() + ")";
			};
			int w = Text.width(label) + 20;
			add(new Button(tx, 78, w, 18, () -> label, t == tab ? Button.Style.PRIMARY : Button.Style.SECONDARY, () -> {
				tab = t;
				rebuildWidgets();
			}));
			tx += w + 6;
		}
		body = add(new ScrollPanel(20, 102, width - 40, height - 138));
		switch (tab) {
			case DESCRIPTION -> fillDescription(project);
			case GALLERY -> fillGallery(project);
			case VERSIONS -> fillVersions(project);
		}
	}

	private void fillDescription(Project project) {
		String text = stripMarkdown(project.body() == null ? project.description() : project.body());
		List<String> lines = Text.wrap(text, body.width - 16);
		int y = 2;
		for (String line : lines) {
			int ly = y;
			body.decorate((g, off) -> Text.draw(g, line, 4, ly, Theme.MUTED));
			y += 11;
		}
		body.setContentHeight(y + 4);
	}

	/** Reduces Markdown/HTML to readable plain text (links keep their label, images are dropped). */
	static String stripMarkdown(String markdown) {
		if (markdown == null) {
			return "";
		}
		return markdown
			.replaceAll("(?s)<!--.*?-->", "")
			.replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
			.replaceAll("<img[^>]*>", "")
			.replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
			.replaceAll("<br\\s*/?>", "\n")
			.replaceAll("<[^>]+>", "")
			.replaceAll("(?m)^#{1,6}\\s*", "")
			.replaceAll("\\*\\*|__|`", "")
			.replaceAll("(?m)^\\s*[-*+]\\s+", "• ")
			.replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
			.replaceAll("\n{3,}", "\n\n")
			.trim();
	}

	private void fillGallery(Project project) {
		List<GalleryImage> images = project.gallery() == null ? List.of() : project.gallery().stream()
			.sorted(Comparator.comparingInt(GalleryImage::ordering)).toList();
		if (images.isEmpty()) {
			body.decorate((g, off) -> Text.draw(g, "This project has no screenshots.", 4, 4, Theme.MUTED));
			return;
		}
		int tileW = 180;
		int tileH = 110;
		int columns = Math.max(1, (body.width - 8) / (tileW + 8));
		for (int i = 0; i < images.size(); i++) {
			GalleryImage image = images.get(i);
			int x = (i % columns) * (tileW + 8);
			int y = (i / columns) * (tileH + 22);
			body.add(new Widget(x, y, tileW, tileH + 16) {
				@Override
				public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
					Render2D.rounded(g, x, y, width, height, 6, isHovered(mouseX, mouseY) ? Theme.surfaceHover() : Theme.surface());
					RemoteTextures.draw(g, image.url(), x + 4, y + 4, width - 8, tileH - 8, 360);
					Text.draw(g, Text.ellipsize(image.title() == null ? "" : image.title(), width - 8), x + 4, y + tileH, Theme.MUTED);
				}

				@Override
				public boolean mouseClicked(double mx, double my, int button) {
					if (isHovered(mx, my)) {
						openPopup(new ImageViewer(image));
						return true;
					}
					return false;
				}
			});
		}
	}

	private final class ImageViewer extends Widget {
		private final GalleryImage image;

		ImageViewer(GalleryImage image) {
			super(10, 10, ProjectScreen.this.width - 20, ProjectScreen.this.height - 20);
			this.image = image;
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			g.fill(0, 0, ProjectScreen.this.width, ProjectScreen.this.height, 0xD0000000);
			String url = image.rawUrl() != null ? image.rawUrl() : image.url();
			RemoteTextures.draw(g, url, x, y, width, height - 24, 1600);
			String caption = (image.title() != null ? image.title() : "") + (image.description() != null ? " – " + image.description() : "");
			Text.drawCentered(g, Text.ellipsize(caption, width - 20), ProjectScreen.this.width / 2, y + height - 14, Theme.TEXT);
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			closePopup();
			return true;
		}
	}

	private void fillVersions(Project project) {
		ModService mods = VantageClient.get().mods();
		if (data.versions().isEmpty()) {
			String loaders = project.loaders() == null ? "?" : String.join(", ", project.loaders());
			body.decorate((g, off) -> {
				Text.draw(g, "No version supports Fabric on Minecraft " + mods.gameVersion() + ".", 4, 4, Theme.WARNING);
				Text.draw(g, "Supported loaders: " + loaders, 4, 16, Theme.MUTED);
			});
			return;
		}
		int y = 0;
		int w = body.width - 8;
		for (Version version : data.versions().stream().limit(40).toList()) {
			int rowY = y;
			body.decorate((g, off) -> {
				Render2D.rounded(g, 0, rowY, w, 26, 5, Theme.surface());
				Text.draw(g, Text.ellipsize(version.versionNumber() + "  " + (version.name() == null ? "" : version.name()), w - 140), 6, rowY + 4, Theme.TEXT);
				Text.draw(g, version.versionType() + " · " + ModBrowserScreen.relative(version.datePublished()) + " · "
					+ ModBrowserScreen.compact(version.downloads()) + " downloads", 6, rowY + 15, Theme.FAINT);
			});
			Button install = new Button(w - 66, rowY + 4, 60, 18, () -> "Install", Button.Style.GHOST,
				() -> InstallPopup.open(this, project.id(), version.id(), project.title(), null));
			install.enabled = !data.installed();
			body.add(install);
			y += 30;
		}
		body.setContentHeight(y);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (error != null) {
			header(g, 20, 16, title, null);
			Text.draw(g, "Could not load project: " + error, 20, 50, Theme.DANGER);
			return;
		}
		if (data == null) {
			header(g, 20, 16, title, "Loading…");
			return;
		}
		Project project = data.project();
		RemoteTextures.draw(g, project.iconUrl(), 20, 16, 52, 52, 128);
		Text.drawScaled(g, Text.ellipsize(project.title(), (width - 220) / 2), 80, 16, 1.5f, Theme.TEXT, false, Text.uiFace());
		String authors = String.join(", ", data.members().stream().sorted(Comparator.comparingInt(TeamMember::ordering))
			.map(m -> m.user().username()).toList());
		Text.draw(g, Text.ellipsize("by " + authors, width - 220), 80, 32, Theme.MUTED);
		Text.draw(g, Text.ellipsize(project.description(), width - 220), 80, 44, Theme.MUTED);
		String stats = "⬇ " + ModBrowserScreen.compact(project.downloads()) + " downloads   ♥ " + ModBrowserScreen.compact(project.followers())
			+ " followers   Updated " + ModBrowserScreen.relative(project.updated())
			+ (project.license() != null && project.license().id() != null ? "   " + project.license().id() : "");
		Text.draw(g, stats, 80, 58, Theme.FAINT);
		Text.draw(g, "Modrinth has no ratings; followers show popularity.", 20, height - 22, Theme.FAINT);
	}
}
