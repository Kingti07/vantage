package dev.vantage.common.util;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Decodes PNG, JPEG, GIF and WebP into ARGB pixels, optionally downscaled. Used for Modrinth icons and
 * screenshots in both the launcher (JavaFX) and the game (Minecraft textures).
 * <p>
 * The WebP reader is instantiated directly instead of through the ImageIO service registry, because plugin
 * discovery does not see jars loaded by Fabric's class loader.
 */
public final class ImageDecoder {
	public record Decoded(int width, int height, int[] argb) {
	}

	private ImageDecoder() {
	}

	public static Decoded decode(byte[] data, int maxDimension) throws IOException {
		BufferedImage image = isWebP(data) ? readWebP(data) : ImageIO.read(new ByteArrayInputStream(data));
		if (image == null) {
			throw new IOException("Unsupported image format");
		}
		image = scale(image, maxDimension);
		int width = image.getWidth();
		int height = image.getHeight();
		int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
		return new Decoded(width, height, argb);
	}

	private static boolean isWebP(byte[] data) {
		return data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
			&& data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
	}

	private static BufferedImage readWebP(byte[] data) throws IOException {
		ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			reader.setInput(input);
			return reader.read(0);
		} finally {
			reader.dispose();
		}
	}

	private static BufferedImage scale(BufferedImage image, int maxDimension) {
		int width = image.getWidth();
		int height = image.getHeight();
		if (maxDimension <= 0 || (width <= maxDimension && height <= maxDimension)) {
			if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
				return image;
			}
			BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			converted.createGraphics().drawImage(image, 0, 0, null);
			return converted;
		}
		double factor = Math.min(maxDimension / (double) width, maxDimension / (double) height);
		int newWidth = Math.max(1, (int) Math.round(width * factor));
		int newHeight = Math.max(1, (int) Math.round(height * factor));
		BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		var graphics = scaled.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(image, 0, 0, newWidth, newHeight, null);
		graphics.dispose();
		return scaled;
	}
}
