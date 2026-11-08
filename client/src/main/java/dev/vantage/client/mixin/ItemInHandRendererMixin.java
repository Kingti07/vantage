package dev.vantage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.pvp.PvpModules;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Animations: held item offset/scale and disabling the re-equip dip. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	private static PvpModules.ItemAnimations vantage$animations() {
		if (!VantageClient.isReady()) {
			return null;
		}
		PvpModules.ItemAnimations module = VantageClient.get().modules().get(PvpModules.ItemAnimations.class);
		return module != null && module.isEnabled() ? module : null;
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void vantage$push(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
		float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		PvpModules.ItemAnimations animations = vantage$animations();
		poseStack.pushPose();
		if (animations != null && !stack.isEmpty()) {
			float side = hand == InteractionHand.MAIN_HAND ? 1 : -1;
			poseStack.translate(animations.offsetX.floatValue() * side * 0.5f, animations.offsetY.floatValue() * 0.5f, animations.offsetZ.floatValue() * 0.5f);
			float scale = animations.scale.floatValue();
			poseStack.scale(scale, scale, scale);
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void vantage$pop(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
		float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.popPose();
	}

	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float vantage$noReequip(float equipProgress) {
		PvpModules.ItemAnimations animations = vantage$animations();
		return animations != null && animations.noReequip.enabled() ? 0f : equipProgress;
	}
}
