package dev.vantage.client.module.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws entity hitboxes and gives the ones you can hit right now a different colour. "Can hit" uses the same
 * reach check the server uses for attacks ({@link Player#isWithinAttackRange}), so it follows creative mode and
 * weapons with their own reach. While enabled it replaces the vanilla F3+B boxes, so they are not drawn twice.
 * Like F3+B it shows nothing on servers that switch on "reduced debug info".
 */
public final class HitboxesModule extends Module {
	/** When a hitbox counts as hittable. */
	public enum Highlight {
		IN_REACH("In reach"),
		AIMED("In reach and aimed at");

		private final String label;

		Highlight(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public final ColorSetting color = add(new ColorSetting("color", "Hitbox colour",
		"Colour of hitboxes you cannot hit right now.", 0xFFFFFFFF));
	public final ColorSetting hittableColor = add(new ColorSetting("hittableColor", "Hittable colour",
		"Colour of hitboxes you can hit right now.", 0xFF3DDC97));
	public final EnumSetting<Highlight> highlight = add(new EnumSetting<>("highlight", "Hittable means",
		"In reach: close enough to hit. Aimed at: close enough and under your crosshair.", Highlight.IN_REACH));
	public final NumberSetting lineWidth = add(new NumberSetting("lineWidth", "Line width", "Line width.", 2, 1, 6, 0.5, "px"));
	public final BooleanSetting players = add(new BooleanSetting("players", "Players", "Show hitboxes of players.", true));
	public final BooleanSetting mobs = add(new BooleanSetting("mobs", "Mobs", "Show hitboxes of animals, monsters and other living entities.", true));
	public final BooleanSetting others = add(new BooleanSetting("others", "Other entities",
		"Show hitboxes of items, arrows, minecarts and other entities.", false));
	public final BooleanSetting lookDirection = add(new BooleanSetting("lookDirection", "Eye line and look direction",
		"Like F3+B: a line at eye height and an arrow where the entity looks.", false));
	public final BooleanSetting onlyWithF3b = add(new BooleanSetting("onlyWithF3b", "Only with F3+B",
		"Show the boxes only while vanilla hitboxes are switched on with F3+B.", false));

	public HitboxesModule() {
		super("hitboxes", "Hitboxes", "Entity hitboxes that change colour when you can hit them.", Category.PVP, false);
	}

	/** True while Vantage draws the hitboxes itself; the vanilla F3+B renderer then stays quiet. */
	public boolean replacesVanilla() {
		return isEnabled();
	}

	/** Draws the boxes (called from the world render event, after entities). */
	public void render(WorldRenderContext context) {
		if (!isEnabled() || MC.level == null || MC.player == null || MC.player.isReducedDebugInfo()) {
			return;
		}
		if (onlyWithF3b.enabled() && !MC.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
			return;
		}
		Vec3 camera = MC.gameRenderer.getMainCamera().position();
		float partial = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		PoseStack.Pose pose = context.matrices().last();
		VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
		float width = lineWidth.floatValue();
		Entity camEntity = MC.getCameraEntity();
		boolean firstPerson = MC.options.getCameraType() == CameraType.FIRST_PERSON;
		for (Entity entity : MC.level.entitiesForRendering()) {
			if (entity.isInvisible() || entity.isRemoved() || !shows(entity) || (entity == camEntity && firstPerson)) {
				continue;
			}
			Vec3 offset = entity.getPosition(partial).subtract(entity.position());
			AABB box = entity.getBoundingBox().move(offset).move(-camera.x, -camera.y, -camera.z);
			int boxColor = canHit(entity) ? hittableColor.color() : color.color();
			box(lines, pose, box, boxColor, width);
			if (lookDirection.enabled()) {
				if (entity instanceof LivingEntity) {
					double eye = box.minY + entity.getEyeHeight();
					box(lines, pose, new AABB(box.minX, eye - 0.01, box.minZ, box.maxX, eye + 0.01, box.maxZ), 0xFFFF0000, width);
				}
				Vec3 from = entity.getPosition(partial).add(0, entity.getEyeHeight(), 0).subtract(camera);
				Vec3 to = from.add(entity.getViewVector(partial).scale(2));
				line(lines, pose, from, to, 0xFF0000FF, width);
			}
		}
	}

	private boolean shows(Entity entity) {
		if (entity instanceof Player) {
			return players.enabled();
		}
		if (entity instanceof LivingEntity) {
			return mobs.enabled();
		}
		return others.enabled();
	}

	/** Whether a left click would reach this entity now, by the same rule the server checks. */
	public boolean canHit(Entity entity) {
		Player player = MC.player;
		if (player == null || entity == player || !entity.isAttackable() || !entity.isAlive()) {
			return false;
		}
		if (!player.isWithinAttackRange(entity.getBoundingBox(), 0)) {
			return false;
		}
		return highlight.get() == Highlight.IN_REACH || MC.crosshairPickEntity == entity;
	}

	private static void box(VertexConsumer c, PoseStack.Pose pose, AABB b, int color, float width) {
		float x0 = (float) b.minX;
		float y0 = (float) b.minY;
		float z0 = (float) b.minZ;
		float x1 = (float) b.maxX;
		float y1 = (float) b.maxY;
		float z1 = (float) b.maxZ;
		// bottom and top rectangles
		edge(c, pose, x0, y0, z0, x1, y0, z0, color, width, 1, 0, 0);
		edge(c, pose, x1, y0, z0, x1, y0, z1, color, width, 0, 0, 1);
		edge(c, pose, x1, y0, z1, x0, y0, z1, color, width, -1, 0, 0);
		edge(c, pose, x0, y0, z1, x0, y0, z0, color, width, 0, 0, -1);
		edge(c, pose, x0, y1, z0, x1, y1, z0, color, width, 1, 0, 0);
		edge(c, pose, x1, y1, z0, x1, y1, z1, color, width, 0, 0, 1);
		edge(c, pose, x1, y1, z1, x0, y1, z1, color, width, -1, 0, 0);
		edge(c, pose, x0, y1, z1, x0, y1, z0, color, width, 0, 0, -1);
		// vertical edges
		edge(c, pose, x0, y0, z0, x0, y1, z0, color, width, 0, 1, 0);
		edge(c, pose, x1, y0, z0, x1, y1, z0, color, width, 0, 1, 0);
		edge(c, pose, x1, y0, z1, x1, y1, z1, color, width, 0, 1, 0);
		edge(c, pose, x0, y0, z1, x0, y1, z1, color, width, 0, 1, 0);
	}

	private static void line(VertexConsumer c, PoseStack.Pose pose, Vec3 from, Vec3 to, int color, float width) {
		Vec3 n = to.subtract(from).normalize();
		edge(c, pose, (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z, color, width,
			(float) n.x, (float) n.y, (float) n.z);
	}

	private static void edge(VertexConsumer c, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
			int color, float width, float nx, float ny, float nz) {
		c.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		c.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}
}
