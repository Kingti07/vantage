package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.pvp.PvpModules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Animations: swing duration of your own arm (visual only). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
	private void vantage$swingSpeed(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this != Minecraft.getInstance().player || !VantageClient.isReady()) {
			return;
		}
		PvpModules.ItemAnimations module = VantageClient.get().modules().get(PvpModules.ItemAnimations.class);
		if (module != null && module.isEnabled()) {
			cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValueI() * module.swingSpeed.floatValue())));
		}
	}
}
