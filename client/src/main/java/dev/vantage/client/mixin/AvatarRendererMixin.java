package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.cosmetics.PlayerCosmeticsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cosmetics: adds the chosen cape and shoulder parrots to your own player's render state. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void vantage$cosmetics(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (!VantageClient.isReady() || avatar != Minecraft.getInstance().player) {
			return;
		}
		PlayerCosmeticsModule cosmetics = VantageClient.get().modules().get(PlayerCosmeticsModule.class);
		if (cosmetics != null) {
			cosmetics.apply(state);
		}
	}
}
