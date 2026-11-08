package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.performance.PerformanceModules;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particle Control: drops disabled particle types and enforces the per-second particle budget. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void vantage$filter(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
		CallbackInfoReturnable<Particle> cir) {
		if (!VantageClient.isReady()) {
			return;
		}
		PerformanceModules.Particles module = VantageClient.get().modules().get(PerformanceModules.Particles.class);
		if (module != null && module.shouldBlock(options)) {
			cir.setReturnValue(null);
		}
	}
}
