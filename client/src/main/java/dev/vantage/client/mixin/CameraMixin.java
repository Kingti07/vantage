package dev.vantage.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.camera.FreelookModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Renders the camera from the freelook angles while freelook is active. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	private static FreelookModule vantage$freelook(Entity entity) {
		if (!VantageClient.isReady() || entity != Minecraft.getInstance().player) {
			return null;
		}
		FreelookModule module = VantageClient.get().modules().get(FreelookModule.class);
		return module != null && module.isActive() ? module : null;
	}

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float vantage$yaw(Entity entity, float partialTick, Operation<Float> original) {
		FreelookModule freelook = vantage$freelook(entity);
		return freelook != null ? freelook.yaw() : original.call(entity, partialTick);
	}

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float vantage$pitch(Entity entity, float partialTick, Operation<Float> original) {
		FreelookModule freelook = vantage$freelook(entity);
		return freelook != null ? freelook.pitch() : original.call(entity, partialTick);
	}
}
