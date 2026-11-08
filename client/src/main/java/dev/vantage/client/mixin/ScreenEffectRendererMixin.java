package dev.vantage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.visual.WorldVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire (fire overlay lowered) and Clear Water (no underwater overlay). */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@ModifyArg(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1)
	private static float vantage$lowFire(float y) {
		if (!VantageClient.isReady()) {
			return y;
		}
		WorldVisuals.LowFire module = VantageClient.get().modules().get(WorldVisuals.LowFire.class);
		return module != null && module.isEnabled() ? y - module.height.floatValue() : y;
	}

	@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
	private static void vantage$clearWater(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
		if (!VantageClient.isReady()) {
			return;
		}
		WorldVisuals.ClearWater module = VantageClient.get().modules().get(WorldVisuals.ClearWater.class);
		if (module != null && module.isEnabled() && module.removeOverlay.enabled()) {
			ci.cancel();
		}
	}
}
