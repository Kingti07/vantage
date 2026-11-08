package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keyboard input for Vantage keybinds (and capturing keys in the keybind editor). */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void vantage$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (window != minecraft.getWindow().handle() || !VantageClient.isReady()) {
			return;
		}
		if (VantageClient.get().keybinds().onKey(event.key(), action)) {
			ci.cancel();
		}
	}
}
