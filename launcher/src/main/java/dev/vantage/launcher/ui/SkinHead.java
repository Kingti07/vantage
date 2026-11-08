package dev.vantage.launcher.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Rectangle;

/**
 * Renders a player's face (base layer + hat layer) from their official skin texture on textures.minecraft.net,
 * scaled with nearest-neighbour so pixels stay crisp.
 */
public final class SkinHead {
	private SkinHead() {
	}

	public static ImageView create(String skinUrl, double size) {
		ImageView view = new ImageView();
		view.setFitWidth(size);
		view.setFitHeight(size);
		view.setSmooth(false);
		Rectangle clip = new Rectangle(size, size);
		clip.setArcWidth(size / 3);
		clip.setArcHeight(size / 3);
		view.setClip(clip);
		view.setStyle("-fx-background-color: #1e2330;");
		if (skinUrl != null && skinUrl.startsWith("https://textures.minecraft.net/") || skinUrl != null && skinUrl.startsWith("http://textures.minecraft.net/")) {
			String secure = skinUrl.replace("http://", "https://");
			WebImages.fetch(secure, 0, skin -> view.setImage(face(skin, (int) size)));
		}
		return view;
	}

	private static Image face(Image skin, int size) {
		int scale = Math.max(1, size / 8);
		WritableImage out = new WritableImage(8 * scale, 8 * scale);
		PixelReader reader = skin.getPixelReader();
		if (reader == null || skin.getWidth() < 64) {
			return out;
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				int base = reader.getArgb(8 + x, 8 + y);
				int hat = reader.getArgb(40 + x, 8 + y);
				int color = (hat >>> 24) > 0x40 ? hat : base;
				for (int dy = 0; dy < scale; dy++) {
					for (int dx = 0; dx < scale; dx++) {
						out.getPixelWriter().setArgb(x * scale + dx, y * scale + dy, color | 0xFF000000);
					}
				}
			}
		}
		return out;
	}
}
