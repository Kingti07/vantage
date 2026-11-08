package dev.vantage.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.performance.PerformanceModules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic FPS: lower frame cap while unfocused, minimised or in menus. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void vantage$dynamicFps(CallbackInfoReturnable<Integer> cir) {
		if (!VantageClient.isReady()) {
			return;
		}
		PerformanceModules.DynamicFps module = VantageClient.get().modules().get(PerformanceModules.DynamicFps.class);
		if (module != null) {
			cir.setReturnValue(module.adjust(cir.getReturnValueI()));
		}
	}
}
