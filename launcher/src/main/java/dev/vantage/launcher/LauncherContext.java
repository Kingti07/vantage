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

/**
 * Wires together all launcher services. One instance lives for the lifetime of the application.
 */
public final class LauncherContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(LauncherContext.class);

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
		this.accounts = new AccountStore(paths.root());
		try {
			this.accounts.load();
		} catch (IOException e) {
			LOGGER.error("Could not read accounts.json; starting without accounts", e);
		}
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
