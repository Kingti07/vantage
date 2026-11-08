package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.visual.WorldVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water: pushes the underwater fog far away. */
@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogEnvironmentMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void vantage$clearWater(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!VantageClient.isReady()) {
			return;
		}
		WorldVisuals.ClearWater module = VantageClient.get().modules().get(WorldVisuals.ClearWater.class);
		if (module != null && module.isEnabled()) {
			float distance = module.visibility.floatValue();
			fog.environmentalStart = Math.max(fog.environmentalStart, distance * 0.25f);
			fog.environmentalEnd = Math.max(fog.environmentalEnd, distance);
			fog.skyEnd = fog.environmentalEnd;
			fog.cloudEnd = fog.environmentalEnd;
		}
	}
}
