package dev.vantage.launcher;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.AccountStore;
import dev.vantage.common.modrinth.ModrinthApi;
import dev.vantage.common.mods.ModManager;
import dev.vantage.launcher.game.GameInstaller;
import dev.vantage.launcher.game.GameMeta;
import dev.vantage.launcher.game.JavaRuntimeManager;
import dev.vantage.launcher.game.LaunchService;
import dev.vantage.launcher.settings.Instance;
import dev.vantage.launcher.settings.SettingsStore;
import dev.vantage.launcher.update.SelfUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires together all launcher services. One instance lives for the lifetime of the application.
 */
public final class LauncherContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(LauncherContext.class);

	/** Notes about user data that had to be repaired while starting; shown once the window is visible. */
	public final List<String> startupWarnings;
	public final LauncherPaths paths;
	public final SettingsStore settings;
	public final AccountStore accounts;
	public final GameMeta meta;
	public final JavaRuntimeManager runtimes;
	public final GameInstaller installer;
	public final ModrinthApi modrinth;
	public final LaunchService launchService;
	public final SelfUpdater updater;

	public LauncherContext(LauncherPaths paths) {
		this.paths = paths;
		this.settings = new SettingsStore(paths);
		this.settings.load();
		List<String> warnings = new ArrayList<>(settings.warnings());
		this.accounts = new AccountStore(paths.root());
		try {
			this.accounts.load().ifPresent(backup -> warnings.add("Your saved accounts could not be read because "
				+ "accounts.json was damaged. The old file was kept as " + backup.getFileName() + ". Please sign in again."));
		} catch (IOException e) {
			LOGGER.error("Could not read accounts.json; starting without accounts", e);
			warnings.add("Your saved accounts could not be read (" + e.getMessage() + "). Check that the Vantage data "
				+ "folder is accessible, then restart the launcher.");
		}
		this.startupWarnings = List.copyOf(warnings);
		this.meta = new GameMeta(paths);
		this.runtimes = new JavaRuntimeManager(paths);
		this.installer = new GameInstaller(paths, meta, runtimes);
		this.modrinth = new ModrinthApi();
		this.launchService = new LaunchService(paths, meta, installer, modrinth);
		this.updater = new SelfUpdater(paths);
	}

	public ModManager modManager(Instance instance) {
		return new ModManager(instance.resolveGameDirectory(paths.instances()).resolve("mods"), modrinth,
			instance.minecraftVersion, VantageInfo.LOADER);
	}
}
