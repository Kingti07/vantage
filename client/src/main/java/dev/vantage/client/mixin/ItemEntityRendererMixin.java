package dev.vantage.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.visual.WorldVisuals;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Item Physics: dropped items lie flat (no bobbing or spinning); blocks rest on the ground. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	private static boolean vantage$physics() {
		if (!VantageClient.isReady()) {
			return false;
		}
		WorldVisuals.ItemPhysics module = VantageClient.get().modules().get(WorldVisuals.ItemPhysics.class);
		return module != null && module.isEnabled();
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void vantage$noBob(PoseStack stack, float x, float y, float z, Operation<Void> original, @Local AABB box) {
		if (!vantage$physics()) {
			original.call(stack, x, y, z);
			return;
		}
		boolean flat = box.getZsize() <= 0.0625 + 1.0e-4;
		original.call(stack, x, flat ? 0.03f : (float) -box.minY, z);
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void vantage$layFlat(PoseStack stack, Quaternionfc rotation, Operation<Void> original, @Local(argsOnly = true) ItemEntityRenderState state,
		@Local AABB box) {
		if (!vantage$physics()) {
			original.call(stack, rotation);
			return;
		}
		// Stable per-item yaw so items do not all face the same way.
		stack.mulPose(Axis.YP.rotation(state.bobOffset * 2.0f));
		if (box.getZsize() <= 0.0625 + 1.0e-4) {
			stack.mulPose(Axis.XP.rotationDegrees(90));
		}
	}
}
