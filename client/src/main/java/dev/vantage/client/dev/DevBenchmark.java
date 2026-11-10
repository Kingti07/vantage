package dev.vantage.client.dev;

import com.google.gson.GsonBuilder;
import dev.vantage.client.VantageClient;
import dev.vantage.client.module.Module;
import dev.vantage.client.module.performance.PerformanceModules;
import dev.vantage.client.ui.screen.MainMenuScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Development-only FPS benchmark, enabled with {@code -Dvantage.benchmark=<seconds>}. Creates the same test world
 * every time (fixed seed, spawn point), lets chunks load during a warm-up, then turns the camera at a constant speed
 * while recording every frame time. Writes average FPS, 1% / 0.1% lows and the installed performance mods to
 * {@code -Dvantage.benchmark.out} (default {@code benchmark.json} in the game directory) and quits. Never active in
 * normal use.
 */
public final class DevBenchmark {
	private static final Logger LOGGER = LoggerFactory.getLogger("Vantage/Benchmark");
	private static final List<String> RELEVANT_MODS = List.of("sodium", "lithium", "immediatelyfast", "ferritecore", "entityculling",
		"moreculling", "iris", "vulkanmod", "c2me");
	/** Degrees per client tick: one full turn every 20 seconds, the same in every run. */
	private static final float TURN_PER_TICK = 0.9f;

	private enum Phase {WAIT_MENU, LOADING_WORLD, WARMUP, MEASURE, DONE}

	private static DevBenchmark active;

	private final int measureTicks = Integer.getInteger("vantage.benchmark", 30) * 20;
	private final int warmupTicks = Integer.getInteger("vantage.benchmark.warmup", 20) * 20;
	private Phase phase = Phase.WAIT_MENU;
	private int ticks;
	private long[] frames = new long[4096];
	private int frameCount;
	private long lastFrame;
	private long measureStart;

	public static boolean enabled() {
		return System.getProperty("vantage.benchmark") != null;
	}

	public DevBenchmark() {
		active = this;
	}

	/** Called for every rendered frame (from the HUD layer); records frame times while measuring. */
	public static void onFrame() {
		DevBenchmark benchmark = active;
		if (benchmark == null || benchmark.phase != Phase.MEASURE) {
			return;
		}
		long now = System.nanoTime();
		if (benchmark.lastFrame != 0) {
			if (benchmark.frameCount == benchmark.frames.length) {
				benchmark.frames = Arrays.copyOf(benchmark.frames, benchmark.frames.length * 2);
			}
			benchmark.frames[benchmark.frameCount++] = now - benchmark.lastFrame;
		}
		benchmark.lastFrame = now;
	}

	/** Called every client tick. */
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		switch (phase) {
			case WAIT_MENU -> {
				if (mc.getOverlay() == null && (mc.screen instanceof MainMenuScreen || ++ticks > 200)) {
					// A frame cap from Dynamic FPS (e.g. when the window lost focus) would falsify the result.
					Module dynamicFps = VantageClient.get().modules().get(PerformanceModules.DynamicFps.class);
					if (dynamicFps != null) {
						dynamicFps.setEnabled(false);
					}
					if (Boolean.getBoolean("vantage.benchmark.preset")) {
						PerformanceModules.FpsBoost.applyPreset();
						LOGGER.info("[Benchmark] FPS Boost preset applied");
					}
					createWorld(mc);
					phase = Phase.LOADING_WORLD;
					ticks = 0;
				}
			}
			case LOADING_WORLD -> {
				if (mc.player != null && mc.level != null && mc.screen == null) {
					LOGGER.info("[Benchmark] world loaded, warming up for {} s", warmupTicks / 20);
					mc.player.setXRot(8f);
					phase = Phase.WARMUP;
					ticks = 0;
				} else if (++ticks > 20 * 180) {
					fail(mc, "the test world did not load within 3 minutes");
				}
			}
			case WARMUP -> {
				turn(mc);
				if (++ticks >= warmupTicks) {
					LOGGER.info("[Benchmark] measuring for {} s", measureTicks / 20);
					phase = Phase.MEASURE;
					ticks = 0;
					lastFrame = 0;
					measureStart = System.nanoTime();
				}
			}
			case MEASURE -> {
				turn(mc);
				if (++ticks >= measureTicks) {
					phase = Phase.DONE;
					finish(mc, System.nanoTime() - measureStart);
				}
			}
			case DONE -> {
			}
		}
	}

	private static void turn(Minecraft mc) {
		if (mc.player != null) {
			mc.player.setYRot(mc.player.getYRot() + TURN_PER_TICK);
		}
	}

	private static void createWorld(Minecraft mc) {
		mc.setScreen(null);
		LevelSettings settings = new LevelSettings("Vantage Benchmark", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
			new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel("vantage-benchmark-" + System.currentTimeMillis(), settings,
			new WorldOptions(12345L, false, false), WorldPresets::createNormalWorldDimensions, new MainMenuScreen());
	}

	private void finish(Minecraft mc, long elapsedNanos) {
		long[] times = Arrays.copyOf(frames, frameCount);
		Arrays.sort(times);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("frames", frameCount);
		result.put("seconds", elapsedNanos / 1e9);
		result.put("averageFps", round(frameCount / (elapsedNanos / 1e9)));
		result.put("medianFps", round(fps(percentile(times, 0.50))));
		result.put("onePercentLowFps", round(fps(percentile(times, 0.99))));
		result.put("pointOnePercentLowFps", round(fps(percentile(times, 0.999))));
		result.put("renderDistance", mc.options.renderDistance().get());
		result.put("fpsBoostPreset", Boolean.getBoolean("vantage.benchmark.preset"));
		result.put("window", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
		Map<String, String> mods = new LinkedHashMap<>();
		for (String id : RELEVANT_MODS) {
			FabricLoader.getInstance().getModContainer(id)
				.ifPresent(mod -> mods.put(id, mod.getMetadata().getVersion().getFriendlyString()));
		}
		result.put("performanceMods", mods);
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(result);
		LOGGER.info("[Benchmark] result {}", json.replaceAll("\\s+", " "));
		Path out = Path.of(System.getProperty("vantage.benchmark.out", mc.gameDirectory.toPath().resolve("benchmark.json").toString()));
		try {
			Files.writeString(out, json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("[Benchmark] could not write {}", out, e);
		}
		mc.stop();
	}

	private void fail(Minecraft mc, String reason) {
		LOGGER.error("[Benchmark] failed: {}", reason);
		phase = Phase.DONE;
		mc.stop();
	}

	private static long percentile(long[] sorted, double fraction) {
		if (sorted.length == 0) {
			return 0;
		}
		return sorted[Math.min(sorted.length - 1, (int) Math.floor(fraction * sorted.length))];
	}

	private static double fps(long frameNanos) {
		return frameNanos <= 0 ? 0 : 1e9 / frameNanos;
	}

	private static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}
}
