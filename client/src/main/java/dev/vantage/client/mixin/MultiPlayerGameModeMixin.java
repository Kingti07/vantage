package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.pvp.PvpModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes your own attacks for the combat statistics and hit particles. Never changes the attack. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void vantage$onAttack(Player player, Entity target, CallbackInfo ci) {
		if (!VantageClient.isReady() || player != Minecraft.getInstance().player) {
			return;
		}
		VantageClient.get().combat().onAttack(Minecraft.getInstance().player, target);
		PvpModules.HitParticles particles = VantageClient.get().modules().get(PvpModules.HitParticles.class);
		if (particles != null) {
			particles.onAttack(target);
		}
	}
}
