package dev.vantage.client.module.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Custom outline (colour and width) and optional translucent fill for the block you are looking at. */
public final class BlockOverlayModule extends Module {
	public final ColorSetting outlineColor = add(new ColorSetting("outlineColor", "Outline colour", "Colour of the block outline.", 0xCC7C5CFF));
	public final NumberSetting outlineWidth = add(new NumberSetting("outlineWidth", "Outline width", "Line width.", 2.5, 1, 8, 0.5, "px"));
	public final BooleanSetting fill = add(new BooleanSetting("fill", "Fill", "Fill the block faces.", true));
	public final ColorSetting fillColor = add(new ColorSetting("fillColor", "Fill colour", "Fill colour; alpha controls transparency.", 0x307C5CFF));

	public BlockOverlayModule() {
		super("blockoverlay", "Block Overlay", "Customise the outline of the block you are looking at.", Category.PVP, false);
		fillColor.visibleWhen(fill::enabled);
	}

	/** Draws the overlay. Returns false so the vanilla outline is skipped. */
	public boolean render(WorldRenderContext context, BlockOutlineRenderState state) {
		if (!isEnabled()) {
			return true;
		}
		Vec3 camera = MC.gameRenderer.getMainCamera().position();
		BlockPos pos = state.pos();
		double x = pos.getX() - camera.x;
		double y = pos.getY() - camera.y;
		double z = pos.getZ() - camera.z;
		PoseStack poses = context.matrices();
		if (fill.enabled()) {
			VertexConsumer quads = context.consumers().getBuffer(RenderTypes.debugFilledBox());
			int color = fillColor.color();
			Matrix4f matrix = poses.last().pose();
			state.shape().forAllBoxes((x0, y0, z0, x1, y1, z1) -> box(quads, matrix,
				(float) (x + x0), (float) (y + y0), (float) (z + z0), (float) (x + x1), (float) (y + y1), (float) (z + z1), color));
		}
		VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
		ShapeRenderer.renderShape(poses, lines, state.shape(), x, y, z, outlineColor.color(), outlineWidth.floatValue());
		return false;
	}

	private static void box(VertexConsumer c, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		float e = 0.002f;
		x0 -= e;
		y0 -= e;
		z0 -= e;
		x1 += e;
		y1 += e;
		z1 += e;
		// down
		c.addVertex(m, x0, y0, z0).setColor(color);
		c.addVertex(m, x1, y0, z0).setColor(color);
		c.addVertex(m, x1, y0, z1).setColor(color);
		c.addVertex(m, x0, y0, z1).setColor(color);
		// up
		c.addVertex(m, x0, y1, z0).setColor(color);
		c.addVertex(m, x0, y1, z1).setColor(color);
		c.addVertex(m, x1, y1, z1).setColor(color);
		c.addVertex(m, x1, y1, z0).setColor(color);
		// north
		c.addVertex(m, x0, y0, z0).setColor(color);
		c.addVertex(m, x0, y1, z0).setColor(color);
		c.addVertex(m, x1, y1, z0).setColor(color);
		c.addVertex(m, x1, y0, z0).setColor(color);
		// south
		c.addVertex(m, x0, y0, z1).setColor(color);
		c.addVertex(m, x1, y0, z1).setColor(color);
		c.addVertex(m, x1, y1, z1).setColor(color);
		c.addVertex(m, x0, y1, z1).setColor(color);
		// west
		c.addVertex(m, x0, y0, z0).setColor(color);
		c.addVertex(m, x0, y0, z1).setColor(color);
		c.addVertex(m, x0, y1, z1).setColor(color);
		c.addVertex(m, x0, y1, z0).setColor(color);
		// east
		c.addVertex(m, x1, y0, z0).setColor(color);
		c.addVertex(m, x1, y1, z0).setColor(color);
		c.addVertex(m, x1, y1, z1).setColor(color);
		c.addVertex(m, x1, y0, z1).setColor(color);
	}
}
