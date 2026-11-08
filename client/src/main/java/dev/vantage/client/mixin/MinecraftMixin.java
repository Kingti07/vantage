package dev.vantage.client.mixin;

import dev.vantage.client.ui.screen.MainMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replaces the vanilla title screen with the Vantage main menu (if enabled in Interface settings). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen vantage$replaceTitleScreen(Screen screen) {
		if (screen instanceof TitleScreen && MainMenuScreen.enabled()) {
			return new MainMenuScreen();
		}
		return screen;
	}
}
