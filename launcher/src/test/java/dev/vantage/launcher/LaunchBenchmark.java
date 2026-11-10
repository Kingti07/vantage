package dev.vantage.launcher;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.mods.ModProblem;
import dev.vantage.common.mods.RecommendedMods;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.game.GameLauncher;
import dev.vantage.launcher.game.LaunchService;
import dev.vantage.launcher.settings.Instance;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Developer FPS benchmark: installs Minecraft + Fabric + Vantage (and, depending on the profile, performance mods)
 * into a throwaway folder and runs the in-game benchmark ({@code DevBenchmark}) in a fixed test world.
 * <p>
 * Usage: {@code ./gradlew :launcher:launchBenchmark -PbenchDir=<folder> -PbenchProfile=vanilla|pack|vulkan
 * -PbenchSeconds=30 [-PbenchRd=12] [-PbenchMods=slug1,slug2]}.
 * With benchMods the profile is only the label of the run. Every profile gets its own game folder in the same data folder, so game files are shared.
 * The result is written to {@code <folder>/benchmark-<profile>.json}.
 */
public final class LaunchBenchmark {
	private LaunchBenchmark() {
	}

	public static void main(String[] args) throws Exception {
		Path dir = Path.of(args[0]).toAbsolutePath();
		String profile = args.length > 1 ? args[1] : "vanilla";
		int seconds = args.length > 2 ? Integer.parseInt(args[2]) : 30;
		int renderDistance = args.length > 3 && !args[3].isBlank() ? Integer.parseInt(args[3]) : 12;
		String modList = args.length > 4 ? args[4] : "";
		LauncherPaths paths = new LauncherPaths(dir);
		paths.createDirectories();
		LauncherContext context = new LauncherContext(paths);

		Instance instance = context.settings.selectedInstance();
		Path gameDir = dir.resolve("bench-" + profile);
		Path result = dir.resolve("benchmark-" + profile + ".json");
		Files.deleteIfExists(result);
		instance.gameDirectory = gameDir.toString();
		instance.memoryMb = 4096;
		// Controlled runs: only the mods named on the command line, never the automatic performance mods.
		instance.performanceMods = false;
		instance.customResolution = true;
		instance.width = 1280;
		instance.height = 720;
		// Far render distances need longer to generate and build the world before measuring makes sense.
		int warmup = renderDistance >= 20 ? 90 : 20;
		instance.extraJvmArgs = "-Dvantage.benchmark=" + seconds + " -Dvantage.benchmark.warmup=" + warmup
			+ " \"-Dvantage.benchmark.out=" + result + "\"";
		context.settings.updateInstance(instance);

		Files.createDirectories(gameDir.resolve("mods"));
		// Same video settings every run: no frame cap, no V-Sync, no AFK limiter, render distance 12.
		Files.writeString(gameDir.resolve("options.txt"), String.join("\n", "onboardAccessibility:false", "maxFps:260",
			"enableVsync:false", "inactivityFpsLimit:minimized", "renderDistance:" + renderDistance, "simulationDistance:8") + "\n", StandardCharsets.UTF_8);
		if (context.accounts.accounts().isEmpty()) {
			context.accounts.put(new MinecraftAccount(UUID.randomUUID().toString().replace("-", ""), "VantageBench", null,
				"invalid-test-token", Long.MAX_VALUE, "0", null));
		}

		ModManager mods = new ModManager(gameDir.resolve("mods"), context.modrinth, instance.minecraftVersion, VantageInfo.LOADER);
		List<String> slugs = !modList.isBlank() ? List.of(modList.split(",")) : switch (profile) {
			case "pack" -> RecommendedMods.PERFORMANCE.stream().filter(RecommendedMods.Recommendation::defaultSelected)
				.map(RecommendedMods.Recommendation::slug).toList();
			case "vulkan" -> List.of("vulkanmod");
			default -> List.of();
		};
		for (String slug : slugs) {
			InstallPlan plan = mods.plan(slug, null);
			if (plan.canInstall()) {
				mods.execute(plan, ProgressListener.NONE);
				System.out.println("[bench] installed " + slug);
			} else {
				System.out.println("[bench] cannot install " + slug + ": " + plan.problems().stream().filter(ModProblem::isError)
					.map(ModProblem::message).toList());
			}
		}

		Path log = dir.resolve("game-output-" + profile + ".log");
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8), true)) {
			LaunchService.Session session = context.launchService.launch(instance, context.settings.settings(), context.accounts,
				GameLauncher.QuickPlay.NONE, ProgressListener.NONE, line -> {
					writer.println(line);
					if (line.contains("[Benchmark]") || line.contains("Exception in thread")) {
						System.out.println("[game] " + line.trim());
					}
				});
			int exit = session.process().waitFor();
			System.out.println("[bench] game exited with code " + exit);
		}
		if (Files.exists(result)) {
			System.out.println("[bench] result " + profile + ": " + Files.readString(result).replaceAll("\\s+", " "));
		} else {
			System.out.println("[bench] no result written; see " + log);
			System.exit(1);
		}
		System.exit(0);
	}
}
