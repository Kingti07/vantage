package dev.vantage.client.module.pvp;

import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.NumberSetting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Smaller PvP visual modules. */
public final class PvpModules {
	private PvpModules() {
	}

	/** More or always-on hit particles. Particles are spawned locally and only on your own screen. */
	public static final class HitParticles extends Module {
		public final NumberSetting multiplier = add(new NumberSetting("multiplier", "Multiplier", "Extra particle bursts per hit.", 2, 1, 10, 1, "x"));
		public final BooleanSetting alwaysCrit = add(new BooleanSetting("alwaysCrit", "Always show crit particles", "Show crit particles on every hit, not only critical hits.", false));
		public final BooleanSetting sharpness = add(new BooleanSetting("sharpness", "Always show sharpness particles", "Show enchanted-hit particles on every hit.", false));

		public HitParticles() {
			super("hitparticles", "Hit Particles", "Customise the particles shown when you hit something.", Category.PVP, false);
		}

		/** Called when you attack an entity. */
		public void onAttack(Entity target) {
			if (!isEnabled() || MC.player == null || !(target instanceof LivingEntity) || MC.level == null) {
				return;
			}
			var player = MC.player;
			boolean vanillaCrit = player.fallDistance > 0 && !player.onGround() && !player.onClimbable() && !player.isInWater()
				&& !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger() && !player.isSprinting()
				&& player.getAttackStrengthScale(0.5f) > 0.9f;
			int bursts = multiplier.intValue() - 1;
			if (vanillaCrit || alwaysCrit.enabled()) {
				for (int i = 0; i < bursts + (vanillaCrit ? 0 : 1); i++) {
					MC.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
				}
			}
			if (sharpness.enabled()) {
				for (int i = 0; i < Math.max(1, bursts); i++) {
					MC.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
				}
			}
		}
	}

	/** Held item position, scale and swing speed ("custom animations"). Purely visual. */
	public static final class ItemAnimations extends Module {
		public final NumberSetting scale = add(new NumberSetting("scale", "Item scale", "Size of the item in your hand.", 1.0, 0.3, 1.5, 0.05, "x"));
		public final NumberSetting offsetX = add(new NumberSetting("offsetX", "Offset X", "Move the held item left/right.", 0, -1, 1, 0.05));
		public final NumberSetting offsetY = add(new NumberSetting("offsetY", "Offset Y", "Move the held item up/down.", 0, -1, 1, 0.05));
		public final NumberSetting offsetZ = add(new NumberSetting("offsetZ", "Offset Z", "Move the held item forward/back.", 0, -1, 1, 0.05));
		public final NumberSetting swingSpeed = add(new NumberSetting("swingSpeed", "Swing duration", "Higher values make the arm swing slower.", 1.0, 0.4, 2.0, 0.05, "x"));
		public final BooleanSetting noReequip = add(new BooleanSetting("noReequip", "No re-equip animation", "Items do not dip down when switching or after hitting.", false));

		public ItemAnimations() {
			super("animations", "Custom Animations", "Change held item position, size and swing speed.", Category.PVP, false);
		}
	}
}
