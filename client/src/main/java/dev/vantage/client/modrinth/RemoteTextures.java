package dev.vantage.client.modrinth;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vantage.client.render.Render2D;
import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.ImageDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads images (Modrinth icons and screenshots, including WebP) on worker threads and turns them into
 * GPU textures on the render thread. Keeps at most {@value #MAX_ENTRIES} textures and releases the least
 * recently used ones.
 */
public final class RemoteTextures {
	private static final int MAX_ENTRIES = 160;
	private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
		Thread thread = new Thread(r, "vantage-remote-textures");
		thread.setDaemon(true);
		return thread;
	});

	private enum State {LOADING, READY, FAILED}

	private static final class Entry {
		final Identifier id;
		volatile State state = State.LOADING;
		int width;
		int height;

		Entry(Identifier id) {
			this.id = id;
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>(64, 0.75f, true);

	private RemoteTextures() {
	}

	/** Source of image bytes (network or local file); returns null when there is no image. */
	@FunctionalInterface
	public interface Source {
		byte[] load() throws Exception;
	}

	/** Draws the image scaled to fit (w × h), or a placeholder while loading / on failure. */
	public static void draw(GuiGraphics g, String url, int x, int y, int w, int h, int maxSize) {
		draw(g, url, url == null ? null : () -> fetch(url), x, y, w, h, maxSize);
	}

	/** Draws an icon stored inside a mod jar (e.g. fabric.mod.json "icon"). */
	public static void drawJarIcon(GuiGraphics g, java.nio.file.Path jar, String entryPath, int x, int y, int w, int h, int maxSize) {
		String key = entryPath == null ? null : "jar:" + jar + "!" + entryPath;
		draw(g, key, key == null ? null : () -> dev.vantage.common.mods.ModScanner.readEntry(jar, entryPath), x, y, w, h, maxSize);
	}

	private static void draw(GuiGraphics g, String key, Source source, int x, int y, int w, int h, int maxSize) {
		Entry entry = key == null ? null : get(key, maxSize, source);
		if (entry == null || entry.state != State.READY) {
			Render2D.rounded(g, x, y, w, h, 4, 0x30FFFFFF);
			return;
		}
		float scale = Math.min(w / (float) entry.width, h / (float) entry.height);
		int dw = Math.max(1, Math.round(entry.width * scale));
		int dh = Math.max(1, Math.round(entry.height * scale));
		g.blit(RenderPipelines.GUI_TEXTURED, entry.id, x + (w - dw) / 2, y + (h - dh) / 2, 0, 0, dw, dh, entry.width, entry.height, entry.width, entry.height);
	}

	/** Natural size of a loaded image, or null while not ready. */
	public static int[] size(String url, int maxSize) {
		Entry entry = url == null ? null : get(url, maxSize, () -> fetch(url));
		return entry != null && entry.state == State.READY ? new int[]{entry.width, entry.height} : null;
	}

	private static Entry get(String id, int maxSize, Source source) {
		String key = id + "@" + maxSize;
		synchronized (ENTRIES) {
			Entry existing = ENTRIES.get(key);
			if (existing != null) {
				return existing;
			}
			String hash = HexFormat.of().formatHex(HashUtil.digest("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8)));
			Entry entry = new Entry(Identifier.fromNamespaceAndPath("vantage", "remote/" + hash));
			ENTRIES.put(key, entry);
			evict();
			POOL.submit(() -> load(source, maxSize, entry));
			return entry;
		}
	}

	private static byte[] fetch(String url) throws Exception {
		HttpRequest request = Http.request(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
		HttpResponse<byte[]> response = Http.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
		return response.statusCode() == 200 ? response.body() : null;
	}

	private static void load(Source source, int maxSize, Entry entry) {
		try {
			byte[] bytes = source.load();
			if (bytes == null) {
				entry.state = State.FAILED;
				return;
			}
			ImageDecoder.Decoded decoded = ImageDecoder.decode(bytes, maxSize);
			Minecraft.getInstance().execute(() -> {
				NativeImage image = new NativeImage(decoded.width(), decoded.height(), false);
				int[] pixels = decoded.argb();
				for (int py = 0; py < decoded.height(); py++) {
					for (int px = 0; px < decoded.width(); px++) {
						image.setPixel(px, py, pixels[py * decoded.width() + px]);
					}
				}
				Minecraft.getInstance().getTextureManager().register(entry.id, new DynamicTexture(() -> "Vantage remote image", image));
				entry.width = decoded.width();
				entry.height = decoded.height();
				entry.state = State.READY;
			});
		} catch (Exception | LinkageError e) {
			entry.state = State.FAILED;
		}
	}

	private static void evict() {
		if (ENTRIES.size() <= MAX_ENTRIES) {
			return;
		}
		Iterator<Map.Entry<String, Entry>> iterator = ENTRIES.entrySet().iterator();
		while (ENTRIES.size() > MAX_ENTRIES && iterator.hasNext()) {
			Entry entry = iterator.next().getValue();
			iterator.remove();
			if (entry.state == State.READY) {
				Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(entry.id));
			}
		}
	}
}
