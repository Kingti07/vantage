package dev.vantage.client;

import dev.vantage.client.combat.CombatTracker;
import dev.vantage.client.config.ConfigManager;
import dev.vantage.client.cosmetics.CosmeticsLayer;
import dev.vantage.client.cosmetics.PlayerCosmeticsModule;
import dev.vantage.client.hud.HudRenderer;
import dev.vantage.client.keybind.KeybindManager;
import dev.vantage.client.modrinth.ModService;
import dev.vantage.client.module.InterfaceModule;
import dev.vantage.client.module.ModuleManager;
import dev.vantage.client.module.camera.FovModule;
import dev.vantage.client.module.camera.FreelookModule;
import dev.vantage.client.module.camera.PerspectiveModule;
import dev.vantage.client.module.camera.ToggleSprintModule;
import dev.vantage.client.module.camera.ZoomModule;
import dev.vantage.client.module.hud.CombatHuds;
import dev.vantage.client.module.hud.ExtraHuds;
import dev.vantage.client.module.hud.InfoHuds;
import dev.vantage.client.module.hud.StatHuds;
import dev.vantage.client.module.performance.PerformanceModules;
import dev.vantage.client.module.pvp.BlockOverlayModule;
import dev.vantage.client.module.pvp.CrosshairModule;
import dev.vantage.client.module.pvp.DamageIndicatorsModule;
import dev.vantage.client.module.pvp.HitColorModule;
import dev.vantage.client.module.pvp.HitboxesModule;
import dev.vantage.client.module.pvp.PvpModules;
import dev.vantage.client.module.utility.UtilityModules;
import dev.vantage.client.module.visual.WorldVisuals;
import dev.vantage.client.render.Text;
import dev.vantage.client.ui.screen.HudEditorScreen;
import dev.vantage.client.ui.screen.ModulesScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Entry point of the in-game client. Owns the module, keybind, config, combat and mod services and wires them
 * to Fabric events. Everything else reaches them through {@link #get()}.
 */
public final class VantageClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Vantage");
	private static VantageClient instance;

	private KeybindManager keybinds;
	private ModuleManager modules;
	private ConfigManager config;
	private CombatTracker combat;
	private ModService mods;
	private InterfaceModule interfaceModule;

	public static VantageClient get() {
		return instance;
	}

	/** False during early start-up, before modules exist (mixins must check this). */
	public static boolean isReady() {
		return instance != null && instance.config != null;
	}

	@Override
	public void onInitializeClient() {
		long start = System.nanoTime();
		keybinds = new KeybindManager();
		modules = new ModuleManager(keybinds);
		combat = new CombatTracker();
		mods = new ModService();
		registerModules();
		instance = this;
		config = new ConfigManager(modules, keybinds);
		config.load();
		Text.setUiFace(interfaceModule.uiFont.get());

		interfaceModule.hudEditorKey.onPress(() -> Minecraft.getInstance().setScreen(new HudEditorScreen(null)));
		interfaceModule.menuKey.onPress(() -> Minecraft.getInstance().setScreen(new ModulesScreen(null)));

		registerRendering();
		registerEvents();
		LOGGER.info("Vantage client ready: {} modules, {} keybinds ({} ms)", modules.all().size(), keybinds.all().size(),
			(System.nanoTime() - start) / 1_000_000);
	}

	private void registerModules() {
		interfaceModule = modules.register(new InterfaceModule());
		// HUD
		modules.register(new StatHuds.Fps());
		modules.register(new StatHuds.Cps());
		modules.register(new StatHuds.Ping());
		modules.register(new InfoHuds.Coordinates());
		modules.register(new CombatHuds.Keystrokes());
		modules.register(new CombatHuds.ArmorStatus());
		modules.register(new CombatHuds.PotionEffects());
		modules.register(new InfoHuds.Durability());
		modules.register(new InfoHuds.Direction());
		modules.register(new StatHuds.Biome());
		modules.register(new StatHuds.Speed());
		modules.register(new StatHuds.Reach());
		modules.register(new StatHuds.Combo());
		modules.register(new StatHuds.Hits());
		modules.register(new StatHuds.Kills());
		modules.register(new StatHuds.WTaps());
		modules.register(new StatHuds.SprintStatus());
		modules.register(new CombatHuds.Target());
		modules.register(new InfoHuds.Performance());
		modules.register(new StatHuds.Clock());
		modules.register(new ExtraHuds.SessionTime());
		modules.register(new ExtraHuds.Server());
		modules.register(new ExtraHuds.Deaths());
		modules.register(new ExtraHuds.Totems());
		modules.register(new ExtraHuds.Arrows());
		modules.register(new ExtraHuds.Warnings());
		// PvP
		modules.register(new CrosshairModule());
		modules.register(new HitColorModule());
		modules.register(new DamageIndicatorsModule());
		modules.register(new PvpModules.HitParticles());
		modules.register(new BlockOverlayModule());
		modules.register(new HitboxesModule());
		modules.register(new PvpModules.ItemAnimations());
		// Camera & movement
		modules.register(new ZoomModule());
		modules.register(new FreelookModule());
		modules.register(new PerspectiveModule());
		modules.register(new ToggleSprintModule());
		modules.register(new FovModule());
		// Visual
		modules.register(new WorldVisuals.Fullbright());
		modules.register(new WorldVisuals.MotionBlur());
		modules.register(new WorldVisuals.LowFire());
		modules.register(new WorldVisuals.NoHurtCam());
		modules.register(new WorldVisuals.ClearWater());
		modules.register(new WorldVisuals.ItemPhysics());
		modules.register(new WorldVisuals.CustomSky());
		modules.register(new WorldVisuals.CustomWeather());
		modules.register(new WorldVisuals.CustomTime());
		modules.register(new PlayerCosmeticsModule());
		// Performance
		modules.register(new PerformanceModules.DynamicFps());
		modules.register(new PerformanceModules.EntityCulling());
		modules.register(new PerformanceModules.Particles());
		// Utility
		modules.register(new UtilityModules.DeathCoordinates());
		modules.register(new UtilityModules.ChatTweaks());
	}

	private void registerRendering() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("vantage", "hud"), HudRenderer::render);
		CrosshairModule crosshair = modules.get(CrosshairModule.class);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (HudElement) (graphics, tracker) -> {
			if (crosshair.isEnabled()) {
				crosshair.render(graphics);
			} else {
				vanilla.render(graphics, tracker);
			}
		});
		BlockOverlayModule blockOverlay = modules.get(BlockOverlayModule.class);
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(blockOverlay::render);
		DamageIndicatorsModule indicators = modules.get(DamageIndicatorsModule.class);
		WorldRenderEvents.AFTER_ENTITIES.register(indicators::render);
		HitboxesModule hitboxes = modules.get(HitboxesModule.class);
		WorldRenderEvents.AFTER_ENTITIES.register(hitboxes::render);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticsLayer(avatar, context.getModelSet()));
			}
		});
	}

	private void registerEvents() {
		dev.vantage.client.dev.DevSmokeTest smokeTest = dev.vantage.client.dev.DevSmokeTest.enabled() ? new dev.vantage.client.dev.DevSmokeTest() : null;
		dev.vantage.client.dev.DevBenchmark benchmark = dev.vantage.client.dev.DevBenchmark.enabled() ? new dev.vantage.client.dev.DevBenchmark() : null;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			modules.tick();
			combat.tick();
			config.tick();
			if (smokeTest != null) {
				smokeTest.tick();
			}
			if (benchmark != null) {
				benchmark.tick();
			}
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			modules.get(HitColorModule.class).apply();
			if (Boolean.getBoolean("vantage.mixinAudit")) {
				LOGGER.info("Running mixin audit (dev)");
				MixinEnvironment.getCurrentEnvironment().audit();
				LOGGER.info("Mixin audit finished");
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			combat.resetSession();
			// Server profiles: switch to the profile linked to this server, if there is one.
			if (client.getCurrentServer() != null) {
				String profile = config.enterServer(client.getCurrentServer().ip);
				if (profile != null) {
					client.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("Vantage: using profile \"" + profile
						+ "\" on this server.").withStyle(net.minecraft.ChatFormatting.GRAY));
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			combat.resetSession();
			config.leaveServer();
		});
	}

	public KeybindManager keybinds() {
		return keybinds;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	public CombatTracker combat() {
		return combat;
	}

	public ModService mods() {
		return mods;
	}

	public InterfaceModule interfaceModule() {
		return interfaceModule;
	}
}
