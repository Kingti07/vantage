package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.pvp.HitboxesModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes module: skips the vanilla F3+B boxes while Vantage draws its own coloured ones. */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
	@Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
	private void vantage$replaceHitboxes(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum,
			float partialTick, CallbackInfo ci) {
		if (!VantageClient.isReady()) {
			return;
		}
		HitboxesModule hitboxes = VantageClient.get().modules().get(HitboxesModule.class);
		if (hitboxes != null && hitboxes.replacesVanilla()) {
			ci.cancel();
		}
	}
}
