package dev.vantage.client.modrinth;

import dev.vantage.common.VantageInfo;
import dev.vantage.common.modrinth.ModrinthApi;
import dev.vantage.common.mods.ModManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Mod management for the running game: the shared {@link ModManager} pointed at this instance's mods folder,
 * a worker pool for network/disk work and a "restart required" flag after changes.
 */
public final class ModService {
	private static final Logger LOGGER = LoggerFactory.getLogger("Vantage/Mods");
	private final ModrinthApi api = new ModrinthApi();
	private final ModManager manager;
	private final String gameVersion;
	private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
		Thread thread = new Thread(r, "vantage-mods");
		thread.setDaemon(true);
		return thread;
	});
	private volatile boolean restartRequired;

	public ModService() {
		gameVersion = FabricLoader.getInstance().getModContainer("minecraft")
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(VantageInfo.CLIENT_MINECRAFT_VERSION);
		manager = new ModManager(FabricLoader.getInstance().getGameDir().resolve("mods"), api, gameVersion, VantageInfo.LOADER);
	}

	public ModrinthApi api() {
		return api;
	}

	public ModManager manager() {
		return manager;
	}

	public String gameVersion() {
		return gameVersion;
	}

	public boolean restartRequired() {
		return restartRequired;
	}

	public void markRestartRequired() {
		restartRequired = true;
	}

	/** Runs work off the render thread and delivers the outcome back on it. */
	public <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
		pool.submit(() -> {
			try {
				T result = work.call();
				Minecraft.getInstance().execute(() -> onSuccess.accept(result));
			} catch (Throwable t) {
				LOGGER.warn("Mod operation failed", t);
				Minecraft.getInstance().execute(() -> onError.accept(t));
			}
		});
	}

	public static String message(Throwable t) {
		Throwable root = t;
		while (root.getCause() != null && root.getMessage() == null) {
			root = root.getCause();
		}
		return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
	}
}
