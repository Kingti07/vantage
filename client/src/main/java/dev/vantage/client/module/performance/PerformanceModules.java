package dev.vantage.client.module.performance;

import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.NumberSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ParticleStatus;

/** Vantage's built-in performance features. Heavier optimisations come from the Modrinth performance pack. */
public final class PerformanceModules {
	private PerformanceModules() {
	}

	/** Lowers the frame rate when the window is unfocused or minimised to save power and heat. */
	public static final class DynamicFps extends Module {
		public final NumberSetting unfocused = add(new NumberSetting("unfocused", "Unfocused FPS", "Frame cap while the window is in the background.", 30, 1, 120, 1));
		public final NumberSetting minimized = add(new NumberSetting("minimized", "Minimised FPS", "Frame cap while the window is minimised.", 1, 1, 30, 1));
		public final BooleanSetting reduceInMenus = add(new BooleanSetting("menus", "Cap menus at 60 FPS", "Limit frame rate on the title screen and menus.", true));

		public DynamicFps() {
			super("dynamicfps", "Dynamic FPS", "Reduce FPS when Minecraft is in the background.", Category.PERFORMANCE, true);
		}

		public int adjust(int limit) {
			if (!isEnabled()) {
				return limit;
			}
			if (MC.getWindow().isIconified()) {
				return Math.min(limit, minimized.intValue());
			}
			if (!MC.isWindowActive()) {
				return Math.min(limit, unfocused.intValue());
			}
			if (reduceInMenus.enabled() && MC.level == null && MC.screen != null) {
				return Math.min(limit, 60);
			}
			return limit;
		}
	}

	/**
	 * Skips rendering entities beyond a configurable distance and optionally hides decorative entities.
	 * (For true occlusion culling install EntityCulling from the performance pack.)
	 */
	public static final class EntityCulling extends Module {
		public final NumberSetting maxDistance = add(new NumberSetting("distance", "Max entity distance", "Entities further away are not rendered.", 64, 16, 256, 8, " blocks"));
		public final BooleanSetting keepPlayers = add(new BooleanSetting("players", "Always render players", "Players are never culled by distance.", true));
		public final BooleanSetting hideArmorStands = add(new BooleanSetting("armorStands", "Hide armour stands", "Do not render armour stands.", false));
		public final BooleanSetting hideItemFrames = add(new BooleanSetting("itemFrames", "Hide item frames", "Do not render item frames.", false));

		public EntityCulling() {
			super("entityculling", "Entity Culling", "Skip rendering distant or decorative entities.", Category.PERFORMANCE, false);
		}

		public boolean shouldCull(Entity entity, double camX, double camY, double camZ) {
			if (!isEnabled() || entity == MC.getCameraEntity()) {
				return false;
			}
			EntityType<?> type = entity.getType();
			if (hideArmorStands.enabled() && type == EntityType.ARMOR_STAND) {
				return true;
			}
			if (hideItemFrames.enabled() && (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME)) {
				return true;
			}
			if (keepPlayers.enabled() && entity instanceof Player) {
				return false;
			}
			double max = maxDistance.get();
			return entity.distanceToSqr(camX, camY, camZ) > max * max;
		}
	}

	/** Caps the number of particles and lets you turn off noisy particle types. */
	public static final class Particles extends Module {
		public final NumberSetting limit = add(new NumberSetting("limit", "Particle limit", "Maximum particles spawned per second.", 2000, 100, 16000, 100));
		public final BooleanSetting explosions = add(new BooleanSetting("explosions", "Explosion particles", "Show explosion particles.", true));
		public final BooleanSetting potion = add(new BooleanSetting("potion", "Potion swirls", "Show potion effect particles.", true));
		public final BooleanSetting weather = add(new BooleanSetting("weather", "Rain splashes", "Show rain splash particles.", true));
		public final BooleanSetting blockBreak = add(new BooleanSetting("blockBreak", "Block break particles", "Show block breaking particles.", true));
		public final BooleanSetting damage = add(new BooleanSetting("damage", "Damage indicator particles", "Show the vanilla dark heart particles.", true));
		private int spawnedThisSecond;
		private long windowStart;

		public Particles() {
			super("particles", "Particle Control", "Limit particle counts and disable specific particle types.", Category.PERFORMANCE, false);
		}

		/** Returns true if the particle should be dropped. */
		public boolean shouldBlock(ParticleOptions options) {
			if (!isEnabled()) {
				return false;
			}
			var type = options.getType();
			if (!explosions.enabled() && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.POOF)) {
				return true;
			}
			if (!potion.enabled() && (type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.EFFECT || type == ParticleTypes.INSTANT_EFFECT
				|| type == ParticleTypes.WITCH)) {
				return true;
			}
			if (!weather.enabled() && (type == ParticleTypes.RAIN || type == ParticleTypes.SPLASH)) {
				return true;
			}
			if (!blockBreak.enabled() && type == ParticleTypes.BLOCK) {
				return true;
			}
			if (!damage.enabled() && type == ParticleTypes.DAMAGE_INDICATOR) {
				return true;
			}
			long now = System.currentTimeMillis();
			if (now - windowStart >= 1000) {
				windowStart = now;
				spawnedThisSecond = 0;
			}
			return ++spawnedThisSecond > limit.intValue();
		}
	}

	/** One-click competitive graphics preset using vanilla options only. */
	public static final class FpsBoost extends Module {
		public FpsBoost() {
			super("fpsboost", "FPS Boost Preset", "Use \"Apply\" in the settings to switch vanilla video options to a high-FPS preset.", Category.PERFORMANCE, false);
		}

		/** Applies the preset to vanilla options and saves them. */
		public static void applyPreset() {
			Options options = MC.options;
			options.cloudStatus().set(CloudStatus.OFF);
			options.particles().set(ParticleStatus.DECREASED);
			options.entityShadows().set(false);
			options.biomeBlendRadius().set(0);
			options.mipmapLevels().set(0);
			options.entityDistanceScaling().set(0.75);
			options.simulationDistance().set(Math.min(options.simulationDistance().get(), 8));
			options.renderDistance().set(Math.min(options.renderDistance().get(), 12));
			options.save();
		}
	}
}
