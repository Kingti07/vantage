package dev.vantage.common;

/**
 * Static identity of the project, shared by the launcher and the in-game client.
 */
public final class VantageInfo {
	private static final java.util.Properties BUILD_INFO = loadBuildInfo();

	public static final String NAME = "Vantage";
	/** Version of this build, taken from {@code project_version} in gradle.properties. */
	public static final String VERSION = BUILD_INFO.getProperty("version", "0.0.0-dev");
	/** Minecraft version the bundled in-game client mod is built for. */
	public static final String CLIENT_MINECRAFT_VERSION = BUILD_INFO.getProperty("minecraft_version", "1.21.11");
	/** Loader used by every Vantage instance. */
	public static final String LOADER = "fabric";

	/**
	 * Modrinth asks API consumers to send a User-Agent that uniquely identifies the application.
	 * See https://docs.modrinth.com/api/#user-agents
	 */
	public static final String USER_AGENT = "VantageClient/" + VERSION + " (Minecraft PvP client; Java " + Runtime.version().feature() + ")";

	/** System property the launcher passes to the game so the in-game client can find shared launcher data. */
	public static final String PROP_LAUNCHER_DIR = "vantage.launcherDir";
	/** System property carrying the Azure application (client) id used for Microsoft sign-in. */
	public static final String PROP_MSA_CLIENT_ID = "vantage.msaClientId";
	/** GitHub repository whose releases deliver launcher (and bundled client) updates. */
	public static final String DEFAULT_UPDATE_REPOSITORY = "Kingti07/vantage";
	/**
	 * Application (client) id of the "Vantage Launcher" Azure app registration (personal Microsoft accounts,
	 * public client flows enabled). Public-client ids are not secrets. Microsoft must approve the id for the
	 * Minecraft Services API before sign-in succeeds. Overridable in the launcher settings or with
	 * {@code -Dvantage.msaClientId}.
	 */
	public static final String DEFAULT_MSA_CLIENT_ID = "bf947917-4c6a-4e2f-80ac-22d5bad7ed48";

	private VantageInfo() {
	}

	private static java.util.Properties loadBuildInfo() {
		java.util.Properties properties = new java.util.Properties();
		try (java.io.InputStream in = VantageInfo.class.getResourceAsStream("build-info.properties")) {
			if (in != null) {
				properties.load(in);
			}
		} catch (java.io.IOException ignored) {
			// fall back to the defaults below
		}
		return properties;
	}
}
