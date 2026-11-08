package dev.vantage.common.auth;

/**
 * A signed-in Microsoft account that owns Minecraft: Java Edition.
 *
 * @param uuid                  Minecraft profile id without dashes
 * @param username              Minecraft player name
 * @param microsoftRefreshToken long-lived token used to sign in again without user interaction
 * @param minecraftAccessToken  short-lived (≈24h) token passed to the game
 * @param minecraftTokenExpiry  epoch millis when {@code minecraftAccessToken} expires
 * @param xuid                  Xbox user id (passed to the game for telemetry), may be empty
 * @param skinUrl               current skin texture URL, may be null
 */
public record MinecraftAccount(
	String uuid,
	String username,
	String microsoftRefreshToken,
	String minecraftAccessToken,
	long minecraftTokenExpiry,
	String xuid,
	String skinUrl
) {
	/** Token is considered stale 5 minutes before it actually expires. */
	public boolean needsRefresh() {
		return minecraftAccessToken == null || System.currentTimeMillis() > minecraftTokenExpiry - 5 * 60_000L;
	}

	/** UUID in the dashed 8-4-4-4-12 form. */
	public String dashedUuid() {
		if (uuid == null || uuid.length() != 32) {
			return uuid;
		}
		return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-"
			+ uuid.substring(16, 20) + "-" + uuid.substring(20);
	}

	@Override
	public String toString() {
		// Never print tokens.
		return "MinecraftAccount[" + username + ", " + uuid + "]";
	}
}
