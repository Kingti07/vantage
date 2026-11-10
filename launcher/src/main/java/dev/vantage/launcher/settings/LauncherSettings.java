package dev.vantage.launcher.settings;

import dev.vantage.common.VantageInfo;

import java.util.regex.Pattern;

/**
 * Global launcher preferences, stored in {@code launcher.json}. Every field has a safe default so that a
 * missing or partial file always yields a working configuration, and {@link #validate()} repairs values that are
 * out of range after loading. Mutated only on the JavaFX thread and saved immediately by {@link SettingsStore#save()}.
 */
public final class LauncherSettings {
	public enum AfterLaunch {KEEP_OPEN, MINIMIZE, CLOSE}

	public static final String DEFAULT_ACCENT = "#7C5CFF";
	public static final int DEFAULT_DOWNLOAD_THREADS = 8;
	public static final int MIN_DOWNLOAD_THREADS = 2;
	public static final int MAX_DOWNLOAD_THREADS = 16;
	private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final Pattern REPOSITORY = Pattern.compile("[\\w.-]+/[\\w.-]+");

	/**
	 * Schema version of this file. 0 means "written before the launcher versioned its files"; see
	 * {@link SettingsStore#migrate}.
	 */
	public int configVersion = 0;
	/**
	 * Azure AD application (client) id used for Microsoft sign-in. Must be approved for the Minecraft API.
	 * Can also be supplied at build/run time with {@code -Dvantage.msaClientId=...}.
	 */
	public String msaClientId = "";
	public String accentColor = DEFAULT_ACCENT;
	public boolean animations = true;
	public AfterLaunch afterLaunch = AfterLaunch.MINIMIZE;
	public boolean checkUpdatesOnStart = true;
	/** GitHub "owner/repo" whose releases provide launcher updates. Empty uses the official Vantage repository. */
	public String updateRepository = "";
	public String selectedInstance = "";
	public boolean showSnapshots = false;
	public int downloadThreads = DEFAULT_DOWNLOAD_THREADS;
	public boolean consoleOnLaunch = false;

	/** The configured update repository, or the official one when the field is empty. */
	public String effectiveUpdateRepository() {
		return updateRepository == null || updateRepository.isBlank()
			? VantageInfo.DEFAULT_UPDATE_REPOSITORY : updateRepository.trim();
	}

	/** Settings field first, then {@code -Dvantage.msaClientId}, then the id built into this distribution. */
	public String effectiveClientId() {
		if (msaClientId != null && !msaClientId.isBlank()) {
			return msaClientId.trim();
		}
		String fromProperty = System.getProperty(VantageInfo.PROP_MSA_CLIENT_ID);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		return VantageInfo.DEFAULT_MSA_CLIENT_ID;
	}

	/**
	 * Replaces missing or invalid values with their defaults, e.g. after a manual edit or a file written by a
	 * different launcher version. Returns true if anything had to be changed.
	 */
	public boolean validate() {
		boolean changed = false;
		if (msaClientId == null) {
			msaClientId = "";
			changed = true;
		}
		if (accentColor == null || !HEX_COLOR.matcher(accentColor).matches()) {
			accentColor = DEFAULT_ACCENT;
			changed = true;
		}
		if (afterLaunch == null) {
			afterLaunch = AfterLaunch.MINIMIZE;
			changed = true;
		}
		if (updateRepository == null || !updateRepository.isBlank() && !REPOSITORY.matcher(updateRepository.trim()).matches()) {
			updateRepository = "";
			changed = true;
		}
		if (selectedInstance == null) {
			selectedInstance = "";
			changed = true;
		}
		if (downloadThreads < MIN_DOWNLOAD_THREADS || downloadThreads > MAX_DOWNLOAD_THREADS) {
			downloadThreads = DEFAULT_DOWNLOAD_THREADS;
			changed = true;
		}
		return changed;
	}
}
