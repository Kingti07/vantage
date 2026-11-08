package dev.vantage.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.camera.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse input: CPS counting, keybinds on mouse buttons, zoom scrolling and zoom sensitivity. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void vantage$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (window != minecraft.getWindow().handle() || !VantageClient.isReady()) {
			return;
		}
		if (action == 1 && minecraft.screen == null && minecraft.getOverlay() == null) {
			VantageClient.get().combat().onClick(info.button());
		}
		if (VantageClient.get().keybinds().onMouse(info.button(), action)) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void vantage$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (window != minecraft.getWindow().handle() || minecraft.screen != null || !VantageClient.isReady()) {
			return;
		}
		ZoomModule zoom = VantageClient.get().modules().get(ZoomModule.class);
		if (zoom != null && zoom.onScroll(vertical)) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void vantage$zoomSensitivity(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		double factor = 1;
		if (VantageClient.isReady()) {
			ZoomModule zoom = VantageClient.get().modules().get(ZoomModule.class);
			factor = zoom != null ? zoom.sensitivityMultiplier() : 1;
		}
		original.call(player, dx * factor, dy * factor);
	}
}
