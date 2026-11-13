package dev.vantage.launcher;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.game.GameLauncher;
import dev.vantage.launcher.game.LaunchService;
import dev.vantage.launcher.game.ModpackInstaller;
import dev.vantage.launcher.game.ModrinthAppImporter;
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
 * Usage: {@code ./gradlew :launcher:launchSmoke -PsmokeDir=<empty folder>}; {@code -PsmokeModpack=<slug>} first installs
 * that Modrinth modpack (newest Fabric version for the client's Minecraft version) into a new installation;
 * {@code -PsmokeImport=<folder>} copies that Modrinth App profile of this computer into a new installation.
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
		if (args.length > 2 && !args[2].isBlank()) {
			instance = installModpack(context, args[2].trim());
		}
		if (args.length > 3 && !args[3].isBlank()) {
			instance = importModrinthProfile(context, args[3].trim());
		}
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

		ProgressListener progress = printer();
		Path log = dir.resolve("game-output.log");
		Instance launched = instance;
		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8), true)) {
			long start = System.currentTimeMillis();
			LaunchService.Session session = context.launchService.launch(launched, context.settings.settings(), context.accounts,
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

	/** Copies a Modrinth App profile (read-only) into a new installation the way the Modpacks page does. */
	private static Instance importModrinthProfile(LauncherContext context, String folder) throws Exception {
		Path data = ModrinthAppImporter.findDataDir().orElseThrow(() -> new IllegalStateException("No Modrinth App on this computer"));
		ModrinthAppImporter.Profile profile = ModrinthAppImporter.profiles(data).stream()
			.filter(p -> p.folder().getFileName().toString().equals(folder)).findFirst()
			.orElseThrow(() -> new IllegalStateException("No Modrinth App profile " + folder));
		Instance instance = context.settings.createInstance(profile.name() + " (imported)", profile.gameVersion());
		instance.loaderVersion = ModpackInstaller.loaderVersion(profile.loaderVersion(), instance.vantageClient);
		context.settings.updateInstance(instance);
		context.settings.select(instance);
		Path gameDir = instance.resolveGameDirectory(context.paths.instances());
		long start = System.currentTimeMillis();
		int files = ModrinthAppImporter.copy(profile, gameDir, false, printer());
		long mods;
		try (var list = Files.list(gameDir.resolve("mods"))) {
			mods = list.filter(p -> p.toString().endsWith(".jar")).count();
		}
		System.out.println("[smoke] imported Modrinth App profile " + profile.name() + ": " + files + " files, " + mods + " enabled mods, Minecraft "
			+ profile.gameVersion() + ", loader " + instance.loaderVersion + ", " + (System.currentTimeMillis() - start) / 1000 + " s");
		skipOnboarding(gameDir);
		return instance;
	}

	/** Installs the newest Fabric version of a Modrinth modpack the way the Modpacks page does. */
	private static Instance installModpack(LauncherContext context, String slug) throws Exception {
		var versions = context.modrinth.projectVersions(slug, "fabric", VantageInfo.CLIENT_MINECRAFT_VERSION);
		if (versions.isEmpty()) {
			throw new IllegalStateException("No Fabric version of " + slug + " for " + VantageInfo.CLIENT_MINECRAFT_VERSION);
		}
		var version = versions.getFirst();
		ModpackInstaller installer = new ModpackInstaller(context.paths);
		dev.vantage.common.modpack.MrPack pack = dev.vantage.common.modpack.MrPack.open(installer.download(version, printer()));
		Instance instance = context.settings.createInstance(pack.name(), pack.minecraftVersion());
		instance.loaderVersion = ModpackInstaller.loaderVersion(pack, instance.vantageClient);
		instance.modpackProjectId = version.projectId();
		instance.modpackVersionId = version.id();
		instance.modpackName = pack.name();
		instance.modpackVersion = version.versionNumber();
		context.settings.updateInstance(instance);
		context.settings.select(instance);
		Path gameDir = instance.resolveGameDirectory(context.paths.instances());
		long start = System.currentTimeMillis();
		int overrides = installer.installFiles(pack, gameDir, true, 8, printer());
		long mods;
		try (var files = Files.list(gameDir.resolve("mods"))) {
			mods = files.filter(p -> p.toString().endsWith(".jar")).count();
		}
		System.out.println("[smoke] modpack " + pack.name() + " " + version.versionNumber() + ": " + mods + " mods, " + overrides
			+ " override files, loader " + instance.loaderVersion + ", " + (System.currentTimeMillis() - start) / 1000 + " s");
		skipOnboarding(gameDir);
		return instance;
	}

	/** A copied options.txt would show Minecraft's accessibility onboarding or start in fullscreen; the unattended run avoids both. */
	private static void skipOnboarding(Path gameDir) throws java.io.IOException {
		Path options = gameDir.resolve("options.txt");
		String text = Files.exists(options) ? Files.readString(options, StandardCharsets.UTF_8) : "";
		String cleaned = text.replace("onboardAccessibility:true", "").replace("fullscreen:true", "fullscreen:false").stripTrailing();
		if (!cleaned.contains("onboardAccessibility:false")) {
			cleaned = (cleaned.isEmpty() ? "" : cleaned + System.lineSeparator()) + "onboardAccessibility:false";
		}
		Files.writeString(options, cleaned + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static ProgressListener printer() {
		return new ProgressListener() {
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
	}
}
