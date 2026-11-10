package dev.vantage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.camera.FovModule;
import dev.vantage.client.module.camera.ZoomModule;
import dev.vantage.client.module.visual.WorldVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FOV (custom FOV + zoom), hurt camera shake and the motion blur post effect. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void vantage$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (!VantageClient.isReady()) {
			return;
		}
		float fov = cir.getReturnValueF();
		if (useFovSetting) {
			FovModule custom = VantageClient.get().modules().get(FovModule.class);
			float base = custom != null ? custom.baseFov() : -1;
			if (base > 0) {
				fov = fov * base / minecraft.options.fov().get().floatValue();
			}
		}
		ZoomModule zoom = VantageClient.get().modules().get(ZoomModule.class);
		if (zoom != null) {
			fov *= zoom.fovMultiplier();
		}
		cir.setReturnValue(fov);
	}

	/** Scales the vanilla damage tilt by the No Hurt Cam strength (0 = no shake). */
	@ModifyExpressionValue(method = "bobHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object vantage$hurtCam(Object tiltStrength) {
		if (!VantageClient.isReady() || !(tiltStrength instanceof Double value)) {
			return tiltStrength;
		}
		WorldVisuals.NoHurtCam module = VantageClient.get().modules().get(WorldVisuals.NoHurtCam.class);
		return module != null && module.isEnabled() ? value * module.strength.get() : tiltStrength;
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void vantage$motionBlur(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		if (!VantageClient.isReady()) {
			return;
		}
		WorldVisuals.MotionBlur blur = VantageClient.get().modules().get(WorldVisuals.MotionBlur.class);
		if (blur == null || !blur.isEnabled() || WorldVisuals.MotionBlur.UNSUPPORTED_RENDERER) {
			return;
		}
		PostChain chain = minecraft.getShaderManager().getPostChain(blur.effect(), LevelTargetBundle.MAIN_TARGETS);
		if (chain != null) {
			chain.process(minecraft.getMainRenderTarget(), resourcePool);
		}
	}
}
