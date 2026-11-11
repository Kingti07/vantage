package dev.vantage.client.cosmetics;

import dev.vantage.client.cosmetics.PlayerCosmeticsModule.Hat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.ARGB;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Hat models built from boxes in code. Coordinates are in model pixels relative to the head pivot: the head
 * spans x and z from -4 to 4 and y from -8 (top) to 0 (neck); its second layer reaches 0.5 further out.
 */
final class HatModels {
	/** How a piece gets its colour. */
	enum Tint {
		FIXED,
		ACCENT,
		ACCENT_DARK
	}

	/** One single-colour part of a hat. */
	record Piece(ModelPart part, Tint tint, int color, boolean glow) {
		int color(int accent) {
			return switch (tint) {
				case FIXED -> color;
				case ACCENT -> accent | 0xFF000000;
				case ACCENT_DARK -> ARGB.color(255, ARGB.red(accent) * 3 / 5, ARGB.green(accent) * 3 / 5, ARGB.blue(accent) * 3 / 5);
			};
		}
	}

	private static final Map<Hat, List<Piece>> CACHE = new EnumMap<>(Hat.class);

	private HatModels() {
	}

	static List<Piece> pieces(Hat hat) {
		return CACHE.computeIfAbsent(hat, HatModels::build);
	}

	private static List<Piece> build(Hat hat) {
		return switch (hat) {
			case TOP_HAT -> List.of(
				fixed(0xFF1E1E26, box(-6, -9, -6, 12, 1, 12), box(-4, -16, -4, 8, 7, 8)),
				accent(box(-4.25f, -10.75f, -4.25f, 8.5f, 1.5f, 8.5f)));
			case CROWN -> List.of(
				fixed(0xFFF2C14E,
					box(-4.75f, -11, -4.75f, 9.5f, 2.5f, 0.75f), box(-4.75f, -11, 4, 9.5f, 2.5f, 0.75f),
					box(-4.75f, -11, -4, 0.75f, 2.5f, 8), box(4, -11, -4, 0.75f, 2.5f, 8),
					box(-4.75f, -12.5f, -4.75f, 1, 1.5f, 0.75f), box(-0.5f, -12.5f, -4.75f, 1, 1.5f, 0.75f),
					box(3.75f, -12.5f, -4.75f, 1, 1.5f, 0.75f), box(-4.75f, -12.5f, 4, 1, 1.5f, 0.75f),
					box(-0.5f, -12.5f, 4, 1, 1.5f, 0.75f), box(3.75f, -12.5f, 4, 1, 1.5f, 0.75f),
					box(-4.75f, -12.5f, -0.5f, 0.75f, 1.5f, 1), box(4, -12.5f, -0.5f, 0.75f, 1.5f, 1)),
				accent(box(-0.6f, -10.4f, -5, 1.2f, 1.2f, 0.3f), box(-0.6f, -10.4f, 4.7f, 1.2f, 1.2f, 0.3f),
					box(-5, -10.4f, -0.6f, 0.3f, 1.2f, 1.2f), box(4.7f, -10.4f, -0.6f, 0.3f, 1.2f, 1.2f)));
			case HALO -> List.of(new Piece(part(
				box(-4, -12, -4, 8, 0.75f, 0.75f), box(-4, -12, 3.25f, 8, 0.75f, 0.75f),
				box(-4, -12, -3.25f, 0.75f, 0.75f, 6.5f), box(3.25f, -12, -3.25f, 0.75f, 0.75f, 6.5f)), Tint.FIXED, 0xFFFFF0A0, true));
			case PARTY_HAT -> List.of(
				accent(box(-3.5f, -10.5f, -3.5f, 7, 2, 7), box(-2, -14.5f, -2, 4, 2, 4)),
				fixed(0xFFFFFFFF, box(-2.75f, -12.5f, -2.75f, 5.5f, 2, 5.5f), box(-1.25f, -16.5f, -1.25f, 2.5f, 2, 2.5f)),
				fixed(0xFFFFD34D, box(-1, -18.5f, -1, 2, 2, 2)));
			case BEANIE -> List.of(
				accent(box(-4.75f, -9.75f, -4.75f, 9.5f, 3, 9.5f)),
				new Piece(part(box(-5, -7.25f, -5, 10, 1.5f, 10)), Tint.ACCENT_DARK, 0, false),
				fixed(0xFFF4F4F8, box(-1.25f, -11.75f, -1.25f, 2.5f, 2, 2.5f)));
			case NONE -> List.of();
		};
	}

	private static float[] box(float x, float y, float z, float width, float height, float depth) {
		return new float[] {x, y, z, width, height, depth};
	}

	private static Piece fixed(int color, float[]... boxes) {
		return new Piece(part(boxes), Tint.FIXED, color, false);
	}

	private static Piece accent(float[]... boxes) {
		return new Piece(part(boxes), Tint.ACCENT, 0, false);
	}

	private static ModelPart part(float[]... boxes) {
		MeshDefinition mesh = new MeshDefinition();
		CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 0);
		for (float[] b : boxes) {
			cubes.addBox(b[0], b[1], b[2], b[3], b[4], b[5]);
		}
		mesh.getRoot().addOrReplaceChild("cubes", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16).bakeRoot();
	}
}
