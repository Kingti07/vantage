package dev.vantage.launcher.ui;

import dev.vantage.common.util.Http;
import dev.vantage.common.util.ImageDecoder;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Rectangle;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Asynchronous image loading for Modrinth icons and screenshots (PNG/JPEG/GIF/WebP) with a bounded in-memory
 * LRU cache, decoded via {@link ImageDecoder} because JavaFX itself cannot read WebP.
 */
public final class WebImages {
	private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
		Thread thread = new Thread(r, "vantage-images");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<String, Image> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
			return size() > 300;
		}
	};

	private WebImages() {
	}

	/** Loads {@code url} (scaled to at most {@code maxSize}px) into the view; keeps the placeholder on failure. */
	public static void load(ImageView view, String url, int maxSize) {
		if (url == null || url.isBlank()) {
			return;
		}
		view.setUserData(url);
		fetch(url, maxSize, image -> {
			if (url.equals(view.getUserData())) {
				view.setImage(image);
			}
		});
	}

	public static void fetch(String url, int maxSize, Consumer<Image> callback) {
		String key = url + "@" + maxSize;
		Image cached;
		synchronized (CACHE) {
			cached = CACHE.get(key);
		}
		if (cached != null) {
			callback.accept(cached);
			return;
		}
		POOL.submit(() -> {
			try {
				byte[] bytes;
				if (url.startsWith("file:")) {
					// Local icons, e.g. of profiles imported from the Modrinth App.
					bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(URI.create(url)));
				} else {
					HttpRequest request = Http.request(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
					HttpResponse<byte[]> response = Http.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
					if (response.statusCode() != 200) {
						return;
					}
					bytes = response.body();
				}
				Image image = toFx(ImageDecoder.decode(bytes, maxSize));
				synchronized (CACHE) {
					CACHE.put(key, image);
				}
				Platform.runLater(() -> callback.accept(image));
			} catch (Exception | LinkageError ignored) {
				// Missing or undecodable images simply keep their placeholder.
			}
		});
	}

	public static Image toFx(ImageDecoder.Decoded decoded) {
		WritableImage image = new WritableImage(decoded.width(), decoded.height());
		image.getPixelWriter().setPixels(0, 0, decoded.width(), decoded.height(), PixelFormat.getIntArgbInstance(),
			decoded.argb(), 0, decoded.width());
		return image;
	}

	/** Square image view with rounded corners and a neutral placeholder background. */
	public static ImageView rounded(double size, double radius) {
		ImageView view = new ImageView();
		view.setFitWidth(size);
		view.setFitHeight(size);
		view.setPreserveRatio(true);
		view.setSmooth(true);
		Rectangle clip = new Rectangle(size, size);
		clip.setArcWidth(radius * 2);
		clip.setArcHeight(radius * 2);
		view.setClip(clip);
		return view;
	}
}
