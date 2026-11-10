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

/**
 * End-to-end "Play" pipeline: refresh the account, install/verify game files, prepare the mods folder (bundled
 * Vantage client + Fabric API, queued mod changes), then start the game and stream its output.
 */
public final class LaunchService {
	private static final Logger LOGGER = LoggerFactory.getLogger(LaunchService.class);
	public static final String BUNDLED_CLIENT_RESOURCE = "/bundled/vantage-client.jar";
	public static final String CLIENT_JAR_NAME = "vantage-client.jar";

	/** A running game. */
	public record Session(Process process, Instance instance, Path gameDir) {
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
		if (account.needsRefresh()) {
			if (clientId.isBlank()) {
				throw new AuthException("Your session expired and no Azure client id is configured. Set it in Settings → Microsoft sign-in.");
			}
			progress.status("Refreshing Microsoft session");
			account = new MicrosoftAuth(clientId).refresh(account);
			accounts.update(account);
		}

		String loader = instance.loaderVersion == null || instance.loaderVersion.isBlank()
			? meta.latestLoader(instance.minecraftVersion) : instance.loaderVersion;
		Path customJava = instance.javaPath == null || instance.javaPath.isBlank() ? null : Path.of(instance.javaPath);
		progress.checkCancelled();
		GameInstaller.PreparedGame game = installer.install(instance.minecraftVersion, loader, customJava, false,
			settings.downloadThreads, progress);

		Path gameDir = instance.resolveGameDirectory(paths.instances());
		progress.checkCancelled();
		prepareMods(instance, gameDir, progress);

		// Last point at which cancelling is possible; after this the game process exists.
		progress.checkCancelled();
		progress.status("Starting Minecraft " + instance.minecraftVersion);
		List<String> command = GameLauncher.buildCommand(game, instance, gameDir, account, clientId, paths.root(), quickPlay);
		LOGGER.info("Launching: {}", GameLauncher.describe(command, account));
		Process process = GameLauncher.start(command, gameDir);
		Thread pump = new Thread(() -> pump(process.getInputStream(), output), "vantage-game-output");
		pump.setDaemon(true);
		pump.start();
		return new Session(process, instance, gameDir);
	}

	/** Applies queued mod changes and keeps the bundled client mod and Fabric API in place. */
	public void prepareMods(Instance instance, Path gameDir, ProgressListener progress) throws IOException {
		Path modsDir = gameDir.resolve("mods");
		Files.createDirectories(modsDir);
		ModManager mods = new ModManager(modsDir, modrinth, instance.minecraftVersion, VantageInfo.LOADER);
		int failed = mods.applyPendingOperations();
		if (failed > 0) {
			LOGGER.warn("{} queued mod change(s) could not be applied", failed);
		}

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
