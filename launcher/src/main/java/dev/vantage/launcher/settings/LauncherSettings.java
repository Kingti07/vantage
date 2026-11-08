package dev.vantage.launcher.settings;

/**
 * Global launcher preferences, stored in {@code launcher.json}. Every field has a safe default so that a
 * missing or partial file always yields a working configuration. Mutated only on the JavaFX thread and saved
 * immediately by {@link SettingsStore#save()}.
 */
public final class LauncherSettings {
	public enum AfterLaunch {KEEP_OPEN, MINIMIZE, CLOSE}

	/**
	 * Azure AD application (client) id used for Microsoft sign-in. Must be approved for the Minecraft API.
	 * Can also be supplied at build/run time with {@code -Dvantage.msaClientId=...}.
	 */
	public String msaClientId = "";
	public String accentColor = "#7C5CFF";
	public boolean animations = true;
	public AfterLaunch afterLaunch = AfterLaunch.MINIMIZE;
	public boolean checkUpdatesOnStart = true;
	/** GitHub "owner/repo" whose releases provide launcher updates. Empty disables update checks. */
	public String updateRepository = "";
	public String selectedInstance = "";
	public boolean showSnapshots = false;
	public int downloadThreads = 8;
	public boolean consoleOnLaunch = false;

	/** Settings field first, then {@code -Dvantage.msaClientId}, then the id built into this distribution. */
	public String effectiveClientId() {
		if (msaClientId != null && !msaClientId.isBlank()) {
			return msaClientId.trim();
		}
		String fromProperty = System.getProperty(dev.vantage.common.VantageInfo.PROP_MSA_CLIENT_ID);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		return dev.vantage.common.VantageInfo.DEFAULT_MSA_CLIENT_ID;
	}
}
