package dev.vantage.client.dev;

import dev.vantage.client.VantageClient;
import dev.vantage.client.hud.HudModule;
import dev.vantage.client.module.Module;
import dev.vantage.client.module.hud.InfoHuds;
import dev.vantage.client.module.pvp.BlockOverlayModule;
import dev.vantage.client.module.pvp.CrosshairModule;
import dev.vantage.client.module.visual.WorldVisuals;
import dev.vantage.client.ui.screen.AccountsScreen;
import dev.vantage.client.ui.screen.CosmeticsScreen;
import dev.vantage.client.ui.screen.HudEditorScreen;
import dev.vantage.client.ui.screen.InstalledModsScreen;
import dev.vantage.client.ui.screen.KeybindsScreen;
import dev.vantage.client.ui.screen.MainMenuScreen;
import dev.vantage.client.ui.screen.ModBrowserScreen;
import dev.vantage.client.ui.screen.ModulesScreen;
import dev.vantage.client.ui.screen.PerformancePackScreen;
import dev.vantage.client.ui.screen.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Development-only automated UI walk-through, enabled with {@code -Dvantage.smokeTest=true}. Opens every Vantage
 * screen, creates a test world with several modules enabled, saves screenshots to the run directory and exits.
 * Never active in normal use.
 */
public final class DevSmokeTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Vantage/SmokeTest");

	private record Step(String name, Runnable action, BooleanSupplier ready, int settleTicks) {
	}

	private final List<Step> steps = new ArrayList<>();
	private int index = -1;
	private int waited;
	private boolean started;
	private int failures;

	public static boolean enabled() {
		return Boolean.getBoolean("vantage.smokeTest");
	}

	public DevSmokeTest() {
		Minecraft mc = Minecraft.getInstance();
		screen("main-menu", MainMenuScreen::new, 60);
		screen("modules", () -> new ModulesScreen(null), 30);
		screen("settings", () -> new SettingsScreen(null), 30);
		screen("cosmetics", () -> new CosmeticsScreen(null), 30);
		screen("keybinds", () -> new KeybindsScreen(null), 30);
		screen("hud-editor-menu", () -> new HudEditorScreen(null), 30);
		screen("modrinth", () -> new ModBrowserScreen(null), 160);
		screen("installed-mods", () -> new InstalledModsScreen(null), 80);
		screen("performance-pack", () -> new PerformancePackScreen(null), 120);
		screen("accounts", () -> new AccountsScreen(null), 30);
		steps.add(new Step("create-world", this::createWorld, () -> mc.player != null && mc.level != null && mc.screen == null, 200));
		steps.add(new Step("in-world-hud", () -> {
		}, () -> true, 20));
		steps.add(new Step("block-overlay", () -> {
			if (mc.player != null) {
				mc.player.setXRot(70f);
			}
		}, () -> true, 20));
		steps.add(new Step("motion-blur", () -> {
			enable(VantageClient.get().modules().get(WorldVisuals.MotionBlur.class));
			if (mc.player != null) {
				mc.player.setYRot(mc.player.getYRot() + 45f);
			}
		}, () -> true, 10));
		screen("hud-editor-world", () -> new HudEditorScreen(null), 30);
		screen("modules-world", () -> new ModulesScreen(null), 30);
		steps.add(new Step("quit", () -> {
			LOGGER.info("[SmokeTest] finished with {} failure(s)", failures);
			mc.stop();
		}, () -> true, 0));
	}

	private void screen(String name, Supplier<Screen> factory, int settle) {
		steps.add(new Step(name, () -> Minecraft.getInstance().setScreen(factory.get()), () -> true, settle));
	}

	private void createWorld() {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(null);
		var modules = VantageClient.get().modules();
		enable(modules.get(CrosshairModule.class));
		enable(modules.get(BlockOverlayModule.class));
		enable(modules.get(InfoHuds.Performance.class));
		enable(modules.get(InfoHuds.Direction.class));
		enable(modules.get(WorldVisuals.Fullbright.class));
		enable(modules.get(WorldVisuals.CustomTime.class));
		enable(modules.get(WorldVisuals.ItemPhysics.class));
		for (HudModule hud : modules.hudModules()) {
			LOGGER.info("[SmokeTest] HUD {} enabled={}", hud.id(), hud.isEnabled());
		}
		LevelSettings settings = new LevelSettings("Vantage Smoke Test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
			new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel("vantage-smoke-" + System.currentTimeMillis(), settings,
			new WorldOptions(12345L, false, false), WorldPresets::createNormalWorldDimensions, new MainMenuScreen());
	}

	private static void enable(Module module) {
		if (module != null) {
			module.setEnabled(true);
		}
	}

	/** Called every client tick. */
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (!started) {
			if (mc.getOverlay() != null) {
				return;
			}
			// Normally the main menu is up; first launches may show another vanilla screen (e.g. onboarding) first.
			if (mc.screen instanceof MainMenuScreen || ++waited > 200) {
				started = true;
				waited = 0;
				LOGGER.info("[SmokeTest] starting (screen: {})", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
			}
			return;
		}
		if (index >= steps.size()) {
			return;
		}
		if (index >= 0) {
			Step step = steps.get(index);
			if (!step.ready().getAsBoolean()) {
				if (++waited > 20 * 120) {
					LOGGER.error("[SmokeTest] step {} timed out", step.name());
					failures++;
					next();
				}
				return;
			}
			if (waited++ < step.settleTicks()) {
				return;
			}
			capture(step.name());
		}
		next();
	}

	private void next() {
		index++;
		waited = 0;
		if (index < steps.size()) {
			Step step = steps.get(index);
			LOGGER.info("[SmokeTest] step {}: {}", index, step.name());
			try {
				step.action().run();
			} catch (Throwable t) {
				failures++;
				LOGGER.error("[SmokeTest] step {} failed", step.name(), t);
			}
		}
	}

	private void capture(String name) {
		Minecraft mc = Minecraft.getInstance();
		try {
			String file = String.format("smoke-%02d-%s.png", index, name);
			Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1, message -> LOGGER.info("[SmokeTest] {}", message.getString()));
		} catch (Throwable t) {
			LOGGER.warn("[SmokeTest] screenshot {} failed: {}", name, t.toString());
		}
	}
}
