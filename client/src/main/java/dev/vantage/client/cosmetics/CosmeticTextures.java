package dev.vantage.client.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vantage.client.cosmetics.PlayerCosmeticsModule.Cape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Paints the cosmetic textures in code (no image files, no third-party art) and registers them with the texture
 * manager the first time they are needed. Must be called on the render thread.
 */
public final class CosmeticTextures {
	private static final Map<Cape, ClientAsset.Texture> CAPES = new EnumMap<>(Cape.class);
	private static Identifier white;

	/** The 10 x 16 emblem on the Vantage cape ('#' = emblem pixel). */
	private static final String[] V_EMBLEM = {
		"..........",
		"..........",
		"..........",
		".#......#.",
		".#......#.",
		"..#....#..",
		"..#....#..",
		"..##..##..",
		"...#..#...",
		"...####...",
		"....##....",
		"..........",
		"..........",
		"..........",
		"..........",
		"..........",
	};

	private CosmeticTextures() {
	}

	/** A plain white texture; hats tint it per part. */
	public static Identifier white() {
		if (white == null) {
			Identifier id = Identifier.fromNamespaceAndPath("vantage", "cosmetics/white");
			NativeImage image = new NativeImage(16, 16, true);
			image.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			white = id;
		}
		return white;
	}

	public static ClientAsset.Texture cape(Cape cape) {
		return CAPES.computeIfAbsent(cape, CosmeticTextures::createCape);
	}

	private static ClientAsset.Texture createCape(Cape cape) {
		Identifier id = Identifier.fromNamespaceAndPath("vantage", "cosmetics/cape_" + cape.name().toLowerCase(Locale.ROOT));
		NativeImage image = new NativeImage(64, 32, true);
		paintCape(image, cape);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		return new ClientAsset.ResourceTexture(id, id);
	}

	/**
	 * Vanilla cape layout on a 64 x 32 texture: the two large faces at (1,1) and (12,1), 10 x 16 each, edges around
	 * them, and the elytra area at (22,0), 24 x 22.
	 */
	static void paintCape(NativeImage image, Cape cape) {
		int edge = darken(capeColor(cape, 0, 8, 10, 16, false), 0.6f);
		image.fillRect(0, 0, 64, 32, edge);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 10; x++) {
				int color = capeColor(cape, x, y, 10, 16, true);
				image.setPixel(1 + x, 1 + y, color);
				image.setPixel(12 + (9 - x), 1 + y, color);
			}
		}
		for (int y = 0; y < 22; y++) {
			for (int x = 0; x < 24; x++) {
				image.setPixel(22 + x, y, capeColor(cape, x, y, 24, 22, false));
			}
		}
	}

	static int capeColor(Cape cape, int x, int y, int width, int height, boolean emblem) {
		float v = height <= 1 ? 0 : y / (float) (height - 1);
		return switch (cape) {
			case VANTAGE -> emblem && V_EMBLEM[y].charAt(x) == '#' ? 0xFFF4F1FF : lerp(0xFF7C5CFF, 0xFF22C3A6, v);
			case OCEAN -> {
				int wave = (y + Math.round(1.5f * Mth.sin(x * 0.9f))) % 5;
				yield wave == 0 ? 0xFF9BE7FF : lerp(0xFF0B3D91, 0xFF3FA9F5, v);
			}
			case SUNSET -> {
				float dx = x - (width - 1) / 2f;
				float dy = y - height * 0.55f;
				if (emblem && dx * dx + dy * dy <= 7.5f) {
					yield 0xFFFFE08A;
				}
				yield v < 0.5f ? lerp(0xFFFFB547, 0xFFFF5C7A, v * 2) : lerp(0xFFFF5C7A, 0xFF5B2A86, (v - 0.5f) * 2);
			}
			case EMBER -> {
				float flame = height * (0.45f + 0.15f * Mth.sin(x * 1.3f) + 0.08f * Mth.sin(x * 3.1f + 1));
				float depth = y - (height - flame);
				if (depth < 0) {
					yield lerp(0xFF140808, 0xFF3A0E0E, v);
				}
				float heat = Math.min(1, depth / flame);
				yield heat < 0.5f ? lerp(0xFFFF3B1F, 0xFFFF8A1F, heat * 2) : lerp(0xFFFF8A1F, 0xFFFFD34D, (heat - 0.5f) * 2);
			}
			case MINT -> (x + y) % 4 == 0 ? 0xFF9DF5CF : lerp(0xFF3DDC97, 0xFF1A8F7A, v);
			case GALAXY -> {
				int hash = (x * 73_856_093) ^ (y * 19_349_663) ^ (width * 83_492_791);
				int star = Math.floorMod(hash, 17);
				if (star == 0) {
					yield 0xFFFFFFFF;
				}
				if (star == 1) {
					yield 0xFFA8C8FF;
				}
				yield lerp(0xFF0B0D2A, 0xFF3A1466, v);
			}
			case CHECKER -> ((x / 2) + (y / 2)) % 2 == 0 ? 0xFFF0F0F0 : 0xFF1C1C1C;
			case NONE -> 0xFF808080;
		};
	}

	private static int lerp(int from, int to, float t) {
		return ARGB.srgbLerp(Mth.clamp(t, 0, 1), from, to);
	}

	private static int darken(int color, float factor) {
		return ARGB.color(255, (int) (ARGB.red(color) * factor), (int) (ARGB.green(color) * factor), (int) (ARGB.blue(color) * factor));
	}
}
