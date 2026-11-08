package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.camera.FovModule;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic FOV control (sprint/speed/bow FOV changes). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void vantage$dynamicFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		if (!VantageClient.isReady()) {
			return;
		}
		FovModule module = VantageClient.get().modules().get(FovModule.class);
		if (module != null) {
			cir.setReturnValue(module.adjustModifier(cir.getReturnValueF()));
		}
	}
}
