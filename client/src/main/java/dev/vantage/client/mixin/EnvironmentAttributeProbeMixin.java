package dev.vantage.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.visual.WorldVisuals;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Sky: overrides the sky, fog and cloud colours the camera reads (render thread only). */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class EnvironmentAttributeProbeMixin {
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void vantage$customSky(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> cir) {
		if (!VantageClient.isReady() || !RenderSystem.isOnRenderThread()) {
			return;
		}
		WorldVisuals.CustomSky sky = VantageClient.get().modules().get(WorldVisuals.CustomSky.class);
		if (sky == null || !sky.isEnabled()) {
			return;
		}
		if (attribute == EnvironmentAttributes.SKY_COLOR) {
			cir.setReturnValue(sky.skyColor.color() & 0xFFFFFF | 0xFF000000);
		} else if (attribute == EnvironmentAttributes.FOG_COLOR && sky.fog.enabled()) {
			cir.setReturnValue(sky.fogColor.color() & 0xFFFFFF | 0xFF000000);
		} else if (attribute == EnvironmentAttributes.CLOUD_COLOR && sky.clouds.enabled()) {
			cir.setReturnValue(sky.cloudColor.color());
		}
	}
}
