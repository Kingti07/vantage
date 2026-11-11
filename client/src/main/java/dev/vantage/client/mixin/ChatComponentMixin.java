package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.utility.UtilityModules;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Chat Tweaks: timestamps, stacked repeated messages and a configurable history length. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	protected abstract void refreshTrimmedMessages();

	private static UtilityModules.ChatTweaks vantage$tweaks() {
		return VantageClient.isReady() ? VantageClient.get().modules().get(UtilityModules.ChatTweaks.class) : null;
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component vantage$decorate(Component message) {
		UtilityModules.ChatTweaks tweaks = vantage$tweaks();
		if (tweaks == null) {
			return message;
		}
		boolean[] repeated = new boolean[1];
		Component decorated = tweaks.decorate(message, repeated);
		if (repeated[0] && !allMessages.isEmpty()) {
			// Replace the previous copy with the stacked one that is about to be added.
			allMessages.removeFirst();
			refreshTrimmedMessages();
		}
		return decorated;
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int vantage$history(int original) {
		UtilityModules.ChatTweaks tweaks = vantage$tweaks();
		return tweaks == null ? original : tweaks.historySize();
	}

	@Inject(method = "clearMessages", at = @At("HEAD"))
	private void vantage$reset(boolean clearHistory, CallbackInfo ci) {
		UtilityModules.ChatTweaks tweaks = vantage$tweaks();
		if (tweaks != null) {
			tweaks.reset();
		}
	}
}
