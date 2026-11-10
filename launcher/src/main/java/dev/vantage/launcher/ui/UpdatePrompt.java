package dev.vantage.launcher.ui;

import dev.vantage.common.VantageInfo;
import dev.vantage.launcher.settings.LauncherSettings;
import dev.vantage.launcher.update.SelfUpdater;

/**
 * The user-facing side of self-updates: asks {@link SelfUpdater} for a newer verified release, offers it and
 * downloads it with progress. All update logic lives in {@link SelfUpdater}.
 */
public final class UpdatePrompt {
	private UpdatePrompt() {
	}

	/**
	 * Checks the release channel and offers to download a newer launcher (which also contains the newer client).
	 *
	 * @param userInitiated report "up to date" and errors, which an automatic check on start keeps quiet
	 */
	public static void check(MainWindow window, boolean userInitiated) {
		LauncherSettings settings = window.context().settings.settings();
		String repository = settings.effectiveUpdateRepository();
		String platform = SelfUpdater.currentPlatform();
		Ui.async(() -> window.context().updater.check(repository, platform), release -> {
			if (release.isEmpty()) {
				if (userInitiated) {
					Toasts.success("You are on the latest version (" + VantageInfo.VERSION + ").");
				}
				return;
			}
			SelfUpdater.Release update = release.get();
			String notes = update.notes().isBlank() ? update.name() : update.notes();
			if (!SelfUpdater.isOfficialRepository(repository)) {
				notes = "Warning: this update comes from \"" + repository + "\", not from the official Vantage repository. "
					+ "Only install it if you trust its owner.\n\n" + notes;
			}
			Overlay.confirm("Vantage " + update.version() + " is available", notes, "Download update", false,
				() -> download(window, update, platform));
		}, err -> {
			if (userInitiated) {
				Toasts.error("Update check failed: " + Ui.message(err));
			}
		});
	}

	private static void download(MainWindow window, SelfUpdater.Release update, String platform) {
		var progress = window.taskBar().begin("Downloading update", true);
		Ui.async(() -> window.context().updater.download(update, platform, progress), path -> {
			progress.finish();
			Overlay.message("Update ready", "Vantage " + update.version()
				+ " has been downloaded and verified. It starts automatically the next time you open the launcher.");
		}, err -> {
			progress.finish();
			if (err instanceof dev.vantage.common.util.CancelledException) {
				Toasts.info("Update download cancelled.");
			} else {
				Overlay.error("Update download failed", err);
			}
		});
	}
}
