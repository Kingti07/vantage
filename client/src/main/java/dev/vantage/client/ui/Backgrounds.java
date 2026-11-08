package dev.vantage.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.InterfaceModule;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.util.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Menu backgrounds: an animated accent gradient, the vanilla panorama, or a user image from
 * {@code config/vantage/backgrounds/} (PNG/JPG). In-world, a translucent dim is drawn instead so the game stays
 * visible behind menus.
 */
public final class Backgrounds {
	private static final Logger LOGGER = LoggerFactory.getLogger("Vantage/Backgrounds");
	private static final Identifier IMAGE_ID = Identifier.fromNamespaceAndPath("vantage", "background/custom");
	private static String loadedImage;
	private static int imageWidth;
	private static int imageHeight;
	private static boolean imageLoaded;

	private Backgrounds() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("vantage").resolve("backgrounds");
	}

	public static List<Path> images() {
		List<Path> result = new ArrayList<>();
		try {
			Files.createDirectories(folder());
			try (Stream<Path> files = Files.list(folder())) {
				files.filter(p -> {
					String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
					return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
				}).sorted().forEach(result::add);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not list backgrounds: {}", e.getMessage());
		}
		return result;
	}

	public static void render(VScreen screen, GuiGraphics g, float delta) {
		Minecraft mc = Minecraft.getInstance();
		int w = screen.width;
		int h = screen.height;
		if (mc.level != null) {
			g.fillGradient(0, 0, w, h, 0xB0080A0E, 0xD0080A0E);
			return;
		}
		InterfaceModule ui = VantageClient.get().interfaceModule();
		switch (ui.background.get()) {
			case PANORAMA -> {
				screen.drawPanorama(g, delta);
				g.fillGradient(0, 0, w, h, 0x40000000, 0xA0000000);
			}
			case IMAGE -> {
				if (!drawImage(g, w, h, ui.backgroundImage.intValue())) {
					gradient(g, w, h);
				}
			}
			default -> gradient(g, w, h);
		}
	}

	/** Slowly drifting colour blobs over a dark base, tinted with the theme colours. */
	public static void gradient(GuiGraphics g, int w, int h) {
		g.fill(0, 0, w, h, 0xFF07080C);
		double t = (System.currentTimeMillis() % 600_000L) / 1000.0;
		int accent = Theme.accent();
		int secondary = Theme.secondary();
		blob(g, (int) (w * (0.25 + 0.12 * Math.sin(t * 0.13))), (int) (h * (0.35 + 0.15 * Math.cos(t * 0.11))), (int) (Math.max(w, h) * 0.55), accent);
		blob(g, (int) (w * (0.78 + 0.1 * Math.cos(t * 0.09))), (int) (h * (0.7 + 0.12 * Math.sin(t * 0.07))), (int) (Math.max(w, h) * 0.5), secondary);
		blob(g, (int) (w * (0.6 + 0.2 * Math.sin(t * 0.05))), (int) (h * (0.15 + 0.1 * Math.cos(t * 0.06))), (int) (Math.max(w, h) * 0.35), Colors.lerp(accent, secondary, 0.5f));
		g.fillGradient(0, 0, w, h, 0x10000000, 0x90000000);
	}

	/** A soft radial glow: one tinted blit of a pre-computed falloff texture (cheap at any size). */
	private static void blob(GuiGraphics g, int cx, int cy, int radius, int color) {
		ensureGlowTexture();
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW_ID, cx - radius, cy - radius, 0, 0, radius * 2, radius * 2, GLOW_SIZE, GLOW_SIZE,
			GLOW_SIZE, GLOW_SIZE, Colors.withAlpha(color, 120));
	}

	private static final Identifier GLOW_ID = Identifier.fromNamespaceAndPath("vantage", "ui/glow");
	private static final int GLOW_SIZE = 128;
	private static boolean glowReady;

	private static void ensureGlowTexture() {
		if (glowReady) {
			return;
		}
		NativeImage image = new NativeImage(GLOW_SIZE, GLOW_SIZE, false);
		float c = (GLOW_SIZE - 1) / 2f;
		for (int y = 0; y < GLOW_SIZE; y++) {
			for (int x = 0; x < GLOW_SIZE; x++) {
				float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
				float a = Math.max(0, 1 - d);
				a = a * a * (3 - 2 * a);
				image.setPixel(x, y, Colors.withAlpha(0xFFFFFF, Math.round(a * 255)));
			}
		}
		Minecraft.getInstance().getTextureManager().register(GLOW_ID, new DynamicTexture(() -> "Vantage glow", image));
		glowReady = true;
	}

	private static boolean drawImage(GuiGraphics g, int w, int h, int index) {
		List<Path> images = images();
		if (images.isEmpty()) {
			return false;
		}
		Path file = images.get(Math.floorMod(index, images.size()));
		if (!file.toString().equals(loadedImage)) {
			load(file);
		}
		if (!imageLoaded) {
			return false;
		}
		// Cover the screen while keeping the aspect ratio.
		float scale = Math.max(w / (float) imageWidth, h / (float) imageHeight);
		int drawW = Math.round(imageWidth * scale);
		int drawH = Math.round(imageHeight * scale);
		int x = (w - drawW) / 2;
		int y = (h - drawH) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, IMAGE_ID, x, y, 0, 0, drawW, drawH, imageWidth, imageHeight, imageWidth, imageHeight);
		g.fillGradient(0, 0, w, h, 0x30000000, 0x90000000);
		return true;
	}

	private static void load(Path file) {
		loadedImage = file.toString();
		imageLoaded = false;
		try (InputStream in = Files.newInputStream(file)) {
			byte[] bytes = in.readAllBytes();
			var decoded = dev.vantage.common.util.ImageDecoder.decode(bytes, 2560);
			NativeImage image = new NativeImage(decoded.width(), decoded.height(), false);
			for (int y = 0; y < decoded.height(); y++) {
				for (int x = 0; x < decoded.width(); x++) {
					image.setPixel(x, y, decoded.argb()[y * decoded.width() + x]);
				}
			}
			Minecraft.getInstance().getTextureManager().register(IMAGE_ID, new DynamicTexture(() -> "Vantage background", image));
			imageWidth = decoded.width();
			imageHeight = decoded.height();
			imageLoaded = true;
		} catch (IOException | RuntimeException | LinkageError e) {
			LOGGER.warn("Could not load background {}: {}", file, e.toString());
		}
	}
}
