package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.camera.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While freelook is active, mouse movement turns the camera instead of the player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void vantage$freelook(double dx, double dy, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player || !VantageClient.isReady()) {
			return;
		}
		FreelookModule freelook = VantageClient.get().modules().get(FreelookModule.class);
		if (freelook != null && freelook.isActive()) {
			freelook.turn(dx, dy);
			ci.cancel();
		}
	}
}
