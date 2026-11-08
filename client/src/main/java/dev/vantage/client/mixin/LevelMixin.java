package dev.vantage.client.mixin;

import dev.vantage.client.VantageClient;
import dev.vantage.client.module.visual.WorldVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Time and Custom Weather. Only the client's own copy of the world ({@link ClientLevel}) is affected,
 * so the server (including the integrated singleplayer server) keeps its real time and weather.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
	private void vantage$time(CallbackInfoReturnable<Long> cir) {
		if (!((Object) this instanceof ClientLevel) || !VantageClient.isReady()) {
			return;
		}
		WorldVisuals.CustomTime module = VantageClient.get().modules().get(WorldVisuals.CustomTime.class);
		if (module != null && module.isEnabled()) {
			cir.setReturnValue(module.dayTime(cir.getReturnValueJ()));
		}
	}

	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void vantage$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
		WorldVisuals.CustomWeather module = vantage$weather();
		if (module != null) {
			cir.setReturnValue(module.rain());
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void vantage$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		WorldVisuals.CustomWeather module = vantage$weather();
		if (module != null) {
			cir.setReturnValue(module.thunder() * module.rain());
		}
	}

	private WorldVisuals.CustomWeather vantage$weather() {
		if (!((Object) this instanceof ClientLevel) || !VantageClient.isReady()) {
			return null;
		}
		WorldVisuals.CustomWeather module = VantageClient.get().modules().get(WorldVisuals.CustomWeather.class);
		return module != null && module.isEnabled() ? module : null;
	}
}
