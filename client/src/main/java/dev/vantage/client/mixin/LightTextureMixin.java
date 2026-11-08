package dev.vantage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.visual.WorldVisuals;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright: the lightmap is computed as if night vision were active at full strength. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	private static boolean vantage$fullbright() {
		if (!VantageClient.isReady()) {
			return false;
		}
		WorldVisuals.Fullbright module = VantageClient.get().modules().get(WorldVisuals.Fullbright.class);
		return module != null && module.isEnabled();
	}

	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z", ordinal = 0))
	private boolean vantage$nightVision(boolean original) {
		return original || vantage$fullbright();
	}

	/** Skips the vanilla lookup entirely: without a real night vision effect it would dereference null. */
	@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
	private float vantage$nightVisionScale(LivingEntity entity, float partialTick, Operation<Float> original) {
		return vantage$fullbright() ? 1.0f : original.call(entity, partialTick);
	}
}
