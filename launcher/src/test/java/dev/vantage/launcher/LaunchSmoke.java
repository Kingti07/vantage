package dev.vantage.launcher;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.game.GameLauncher;
import dev.vantage.launcher.game.LaunchService;
import dev.vantage.launcher.settings.Instance;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Developer integration check for the full launch pipeline (not part of the product). Installs Minecraft,
 * Fabric, the Mojang Java runtime, the bundled Vantage client and Fabric API into a throwaway data directory,
 * then starts the game with the in-game smoke test enabled, which exits by itself.
 * <p>
 * The account is a local placeholder with an invalid token: enough for the game to start and run
 * singleplayer, but it cannot join servers. Real players always sign in with Microsoft.
 * <p>
 * Usage: {@code ./gradlew :launcher:launchSmoke -PsmokeDir=<empty folder>}
 */
public final class LaunchSmoke {
	private LaunchSmoke() {
	}

	public static void main(String[] args) throws Exception {
		Path dir = Path.of(args[0]).toAbsolutePath();
		LauncherPaths paths = new LauncherPaths(dir);
		paths.createDirectories();
		LauncherContext context = new LauncherContext(paths);
		Instance instance = context.settings.selectedInstance();
		instance.extraJvmArgs = "-Dvantage.smokeTest=true -Dvantage.mixinAudit=true";
		instance.memoryMb = 3072;
		// Controlled runs: only the mods named on the command line, never the automatic performance mods.
		instance.performanceMods = false;
		context.settings.updateInstance(instance);
		// A brand-new game directory opens Minecraft's accessibility onboarding first; skip it for the unattended run.
		Path gameDir = instance.resolveGameDirectory(paths.instances());
		Files.createDirectories(gameDir);
		Path options = gameDir.resolve("options.txt");
		if (!Files.exists(options)) {
			Files.writeString(options, "onboardAccessibility:false\n", StandardCharsets.UTF_8);
		}
		if (context.accounts.accounts().isEmpty()) {
			context.accounts.put(new MinecraftAccount(UUID.randomUUID().toString().replace("-", ""), "VantageSmoke", null,
				"invalid-test-token", Long.MAX_VALUE, "0", null));
		}

		// Optional second argument: comma-separated Modrinth slugs to install first (compatibility checks, e.g. "sodium,lithium").
		if (args.length > 1 && !args[1].isBlank()) {
			ModManager mods = new ModManager(gameDir.resolve("mods"), context.modrinth, instance.minecraftVersion, VantageInfo.LOADER);
			for (String slug : args[1].split(",")) {
				InstallPlan plan = mods.plan(slug.trim(), null);
				if (plan.canInstall()) {
					mods.execute(plan, ProgressListener.NONE);
					System.out.println("[smoke] installed " + slug.trim());
				} else {
					System.out.println("[smoke] cannot install " + slug.trim() + ": " + plan.problems());
				}
			}
		}

		ProgressListener progress = new ProgressListener() {
			private String last = "";
			private int lastPercent = -1;

			@Override
			public synchronized void status(String message) {
				if (!message.equals(last)) {
					System.out.println("[status] " + message);
					last = message;
				}
			}

			@Override
			public synchronized void progress(double fraction) {
				int percent = (int) Math.round(fraction * 100);
				if (percent >= 0 && percent / 10 != lastPercent / 10) {
					System.out.println(String.format(Locale.ROOT, "[progress] %d%%", percent));
					lastPercent = percent;
				}
			}
		};
		Path log = dir.resolve("game-output.log");
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8), true)) {
			long start = System.currentTimeMillis();
			LaunchService.Session session = context.launchService.launch(instance, context.settings.settings(), context.accounts,
				GameLauncher.QuickPlay.NONE, progress, line -> {
					writer.println(line);
					if (line.contains("Vantage") || line.contains("ERROR") || line.contains("Exception")) {
						System.out.println("[game] " + line);
					}
				});
			System.out.println("[launcher] prepared and started in " + (System.currentTimeMillis() - start) / 1000 + " s");
			int exit = session.process().waitFor();
			System.out.println("[launcher] game exited with code " + exit);
			Thread.sleep(500);
			System.exit(exit);
		}
	}
}
