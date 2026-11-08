package dev.vantage.launcher.ui;

import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Markdown renderer for Modrinth project descriptions: headings, paragraphs, bullet lists, bold,
 * inline code, links and images. HTML tags are stripped (images in {@code <img src>} are kept).
 * It intentionally renders untrusted content as plain JavaFX nodes – nothing is ever executed.
 */
public final class Markdown {
	private static final Pattern IMAGE = Pattern.compile("!\\[([^]]*)]\\(([^)\\s]+)[^)]*\\)");
	private static final Pattern HTML_IMAGE = Pattern.compile("<img[^>]*src=\"([^\"]+)\"[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern INLINE = Pattern.compile("\\[([^]]+)]\\(([^)\\s]+)[^)]*\\)|\\*\\*([^*]+)\\*\\*|__([^_]+)__|`([^`]+)`|\\*([^*]+)\\*");
	private static final Pattern TAG = Pattern.compile("<[^>]+>");

	private Markdown() {
	}

	public static Node render(String markdown, double width) {
		VBox box = new VBox(8);
		if (markdown == null) {
			return box;
		}
		String source = HTML_IMAGE.matcher(markdown).replaceAll("\n![]($1)\n");
		List<String> paragraph = new ArrayList<>();
		boolean inCode = false;
		StringBuilder code = new StringBuilder();
		for (String raw : source.split("\\R")) {
			String line = raw.stripTrailing();
			if (line.trim().startsWith("```")) {
				if (inCode) {
					Text text = new Text(code.toString());
					text.getStyleClass().add("mono");
					text.setStyle("-fx-fill: #c9cfdc;");
					TextFlow flow = new TextFlow(text);
					flow.getStyleClass().add("inset");
					box.getChildren().add(flow);
					code.setLength(0);
				} else {
					flush(box, paragraph, width);
				}
				inCode = !inCode;
				continue;
			}
			if (inCode) {
				code.append(line).append('\n');
				continue;
			}
			String stripped = TAG.matcher(line).replaceAll("").trim();
			Matcher image = IMAGE.matcher(stripped);
			if (image.matches()) {
				flush(box, paragraph, width);
				ImageView view = new ImageView();
				view.setPreserveRatio(true);
				view.setFitWidth(Math.min(width, 640));
				WebImages.fetch(image.group(2), 900, img -> {
					view.setImage(img);
					view.setFitWidth(Math.min(Math.min(width, 640), img.getWidth()));
				});
				box.getChildren().add(view);
				continue;
			}
			if (stripped.isEmpty()) {
				flush(box, paragraph, width);
				continue;
			}
			if (stripped.startsWith("#")) {
				flush(box, paragraph, width);
				int level = 0;
				while (level < stripped.length() && stripped.charAt(level) == '#') {
					level++;
				}
				TextFlow heading = inline(stripped.substring(level).trim(), width);
				String style = "-fx-font-size: " + (level <= 1 ? 20 : level == 2 ? 17 : 15) + "px; -fx-font-weight: bold; -fx-fill: #e8eaf0;";
				heading.getChildren().forEach(n -> n.setStyle(style));
				box.getChildren().add(heading);
				continue;
			}
			if (stripped.matches("^([-*+]|\\d+\\.)\\s+.*")) {
				flush(box, paragraph, width);
				String item = stripped.replaceFirst("^([-*+]|\\d+\\.)\\s+", "");
				TextFlow flow = inline("•  " + item, width);
				flow.setStyle("-fx-padding: 0 0 0 10;");
				box.getChildren().add(flow);
				continue;
			}
			if (stripped.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
				flush(box, paragraph, width);
				continue;
			}
			paragraph.add(stripped.startsWith(">") ? stripped.substring(1).trim() : stripped);
		}
		flush(box, paragraph, width);
		return box;
	}

	private static void flush(VBox box, List<String> paragraph, double width) {
		if (paragraph.isEmpty()) {
			return;
		}
		box.getChildren().add(inline(String.join(" ", paragraph), width));
		paragraph.clear();
	}

	private static TextFlow inline(String text, double width) {
		TextFlow flow = new TextFlow();
		flow.setMaxWidth(width);
		flow.setLineSpacing(3);
		Matcher matcher = INLINE.matcher(text);
		int last = 0;
		while (matcher.find()) {
			if (matcher.start() > last) {
				flow.getChildren().add(plain(text.substring(last, matcher.start())));
			}
			if (matcher.group(1) != null) {
				String url = matcher.group(2);
				Hyperlink link = new Hyperlink(matcher.group(1));
				link.setOnAction(e -> Ui.openUrl(url));
				flow.getChildren().add(link);
			} else if (matcher.group(3) != null || matcher.group(4) != null) {
				Text bold = plain(matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
				bold.setStyle("-fx-font-weight: bold; -fx-fill: #e8eaf0;");
				flow.getChildren().add(bold);
			} else if (matcher.group(5) != null) {
				Text code = plain(matcher.group(5));
				code.getStyleClass().add("mono");
				code.setStyle("-fx-fill: #c3b5ff;");
				flow.getChildren().add(code);
			} else if (matcher.group(6) != null) {
				Text italic = plain(matcher.group(6));
				italic.setStyle("-fx-font-style: italic; -fx-fill: #c9cfdc;");
				flow.getChildren().add(italic);
			}
			last = matcher.end();
		}
		if (last < text.length()) {
			flow.getChildren().add(plain(text.substring(last)));
		}
		return flow;
	}

	private static Text plain(String text) {
		Text node = new Text(text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"));
		node.setStyle("-fx-fill: #c9cfdc; -fx-font-size: 13px;");
		return node;
	}
}
