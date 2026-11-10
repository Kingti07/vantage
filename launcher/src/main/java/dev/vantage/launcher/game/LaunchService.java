package dev.vantage.launcher.game;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.AccountStore;
import dev.vantage.common.auth.AuthException;
import dev.vantage.common.auth.MicrosoftAuth;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.common.modrinth.ModrinthApi;
import dev.vantage.common.mods.InstallPlan;
import dev.vantage.common.mods.InstalledMod;
import dev.vantage.common.mods.ModManager;
import dev.vantage.common.mods.ModProblem;
import dev.vantage.common.util.HashUtil;
import dev.vantage.common.util.ProgressListener;
import dev.vantage.launcher.LauncherPaths;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.settings.LauncherSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * End-to-end "Play" pipeline: refresh the account, install/verify game files, prepare the mods folder (bundled
 * Vantage client + Fabric API, queued mod changes), then start the game and stream its output.
 */
public final class LaunchService {
	private static final Logger LOGGER = LoggerFactory.getLogger(LaunchService.class);
	public static final String BUNDLED_CLIENT_RESOURCE = "/bundled/vantage-client.jar";
	public static final String CLIENT_JAR_NAME = "vantage-client.jar";
	/** Left by the in-game account switcher (client AccountsScreen.RELAUNCH_FILE) to ask for a restart with another account. */
	public static final String RELAUNCH_REQUEST_FILE = "relaunch-request.json";

	/** A running game; {@code offline} means the session could not be refreshed and online play will not work. */
	public record Session(Process process, Instance instance, Path gameDir, boolean offline) {
	}

	private final LauncherPaths paths;
	private final GameMeta meta;
	private final GameInstaller installer;
	private final ModrinthApi modrinth;

	public LaunchService(LauncherPaths paths, GameMeta meta, GameInstaller installer, ModrinthApi modrinth) {
		this.paths = paths;
		this.meta = meta;
		this.installer = installer;
		this.modrinth = modrinth;
	}

	/**
	 * Runs on a background thread. Output lines of the game are delivered to {@code output}; the returned
	 * session's process can be awaited for the exit code.
	 */
	public Session launch(Instance instance, LauncherSettings settings, AccountStore accounts, GameLauncher.QuickPlay quickPlay,
		ProgressListener progress, Consumer<String> output) throws IOException, AuthException {
		MinecraftAccount account = accounts.selected().orElseThrow(() -> new AuthException("Add a Microsoft account before playing."));
		String clientId = settings.effectiveClientId();
		boolean offline = false;
		if (account.needsRefresh()) {
			if (clientId.isBlank()) {
				throw new AuthException("Your session expired and no Azure client id is configured. Set it in Settings → Microsoft sign-in.");
			}
			progress.status("Refreshing Microsoft session");
			try {
				account = new MicrosoftAuth(clientId).refresh(account);
				accounts.update(account);
			} catch (AuthException e) {
				if (!dev.vantage.common.util.Http.isNetworkUnavailable(e) || account.minecraftAccessToken() == null) {
					throw e;
				}
				// Offline: singleplayer works with the old session; online servers will reject it until we are back online.
				LOGGER.warn("Could not refresh the session (offline); starting with the saved one");
				offline = true;
				output.accept("[Vantage] You are offline. Starting with your saved session: singleplayer works, "
					+ "online servers will not accept it until you are back online.");
			}
		}

		String loader = instance.loaderVersion == null || instance.loaderVersion.isBlank()
			? meta.latestLoader(instance.minecraftVersion) : instance.loaderVersion;
		Path customJava = instance.javaPath == null || instance.javaPath.isBlank() ? null : Path.of(instance.javaPath);
		progress.checkCancelled();
		GameInstaller.PreparedGame game = installer.install(instance.minecraftVersion, loader, customJava, false,
			settings.downloadThreads, progress);

		Path gameDir = instance.resolveGameDirectory(paths.instances());
		progress.checkCancelled();
		for (String note : prepareMods(instance, gameDir, progress)) {
			output.accept("[Vantage] " + note);
		}

		// Last point at which cancelling is possible; after this the game process exists.
		progress.checkCancelled();
		progress.status("Starting Minecraft " + instance.minecraftVersion);
		List<String> command = GameLauncher.buildCommand(game, instance, gameDir, account, clientId, paths.root(), quickPlay, settings.gcMode);
		WindowsTuning.preferHighPerformanceGpu(game.javaExecutable(), settings.preferHighPerformanceGpu);
		LOGGER.info("Launching: {}", GameLauncher.describe(command, account));
		Process process = GameLauncher.start(command, gameDir);
		if (settings.highPriority) {
			WindowsTuning.raisePriority(process);
		}
		Thread pump = new Thread(() -> pump(process.getInputStream(), output), "vantage-game-output");
		pump.setDaemon(true);
		pump.start();
		return new Session(process, instance, gameDir, offline);
	}

	/**
	 * Applies queued mod changes, keeps the bundled client mod and Fabric API in place and brings the performance
	 * mods and the renderer in line with the installation's settings. Returns notes for the console.
	 */
	public List<String> prepareMods(Instance instance, Path gameDir, ProgressListener progress) throws IOException {
		Path modsDir = gameDir.resolve("mods");
		Files.createDirectories(modsDir);
		ModManager mods = new ModManager(modsDir, modrinth, instance.minecraftVersion, VantageInfo.LOADER);
		int failed = mods.applyPendingOperations();
		if (failed > 0) {
			LOGGER.warn("{} queued mod change(s) could not be applied", failed);
		}
		installClient(instance, mods, progress);
		progress.checkCancelled();
		return PerformanceMods.apply(instance, mods, progress);
	}

	private void installClient(Instance instance, ModManager mods, ProgressListener progress) throws IOException {
		Path modsDir = mods.modsDir();
		Path clientJar = modsDir.resolve(CLIENT_JAR_NAME);
		boolean wantClient = instance.vantageClient && instance.supportsVantageClient();
		if (!wantClient) {
			Files.deleteIfExists(clientJar);
			return;
		}
		try (InputStream bundled = LaunchService.class.getResourceAsStream(BUNDLED_CLIENT_RESOURCE)) {
			if (bundled == null) {
				LOGGER.warn("This launcher build does not contain the Vantage client mod; launching without it");
				return;
			}
			Path temp = Files.createTempFile(modsDir, "vantage-client", ".tmp");
			try {
				Files.copy(bundled, temp, StandardCopyOption.REPLACE_EXISTING);
				if (!Files.exists(clientJar) || !HashUtil.sha1(temp).equals(HashUtil.sha1(clientJar))) {
					progress.status("Installing Vantage client " + VantageInfo.VERSION);
					Files.move(temp, clientJar, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(temp);
			}
		}

		// The client mod requires Fabric API; install it from Modrinth if it is missing.
		List<InstalledMod> installed = mods.installed();
		boolean hasFabricApi = installed.stream().anyMatch(m -> m.effectiveEnabled() && m.metadata() != null
			&& ("fabric-api".equals(m.metadata().id()) || m.metadata().provides().contains("fabric")));
		if (!hasFabricApi) {
			progress.status("Installing Fabric API");
			InstallPlan plan = mods.plan("fabric-api", null);
			if (!plan.canInstall()) {
				throw new IOException("Fabric API could not be installed: " + plan.problems().stream()
					.filter(ModProblem::isError).map(ModProblem::message).findFirst().orElse("unknown error"));
			}
			mods.execute(plan, progress);
		}
	}

	/** Calls {@code onExit} with the exit code, on a background thread, once the game process has ended. */
	public static void whenExited(Session session, IntConsumer onExit) {
		Thread waiter = new Thread(() -> {
			try {
				onExit.accept(session.process().waitFor());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, "vantage-game-waiter");
		waiter.setDaemon(true);
		waiter.start();
	}

	/** True (once) if the game asked to be restarted with another account; the request is consumed. */
	public boolean takeRelaunchRequest() {
		try {
			return Files.deleteIfExists(paths.root().resolve(RELAUNCH_REQUEST_FILE));
		} catch (IOException e) {
			LOGGER.warn("Could not process the relaunch request", e);
			return false;
		}
	}

	private static void pump(InputStream stream, Consumer<String> output) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.accept(line);
			}
		} catch (IOException e) {
			output.accept("[Vantage] Lost game output: " + e.getMessage());
		}
	}
}
