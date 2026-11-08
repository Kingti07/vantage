package dev.vantage.common.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vantage.common.util.Http;
import dev.vantage.common.util.HttpException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Official Microsoft → Xbox Live → XSTS → Minecraft sign-in chain, using the OAuth 2.0 device code flow so no
 * password ever passes through this application.
 * <p>
 * Requires an Azure AD application ("client id") with "Allow public client flows" enabled that has been approved
 * for the Minecraft Services API (https://aka.ms/mce-reviewappid). The id is configured in the launcher settings.
 */
public final class MicrosoftAuth {
	private static final String TENANT = "consumers";
	private static final String SCOPE = "XboxLive.signin offline_access";
	private static final URI DEVICE_CODE = URI.create("https://login.microsoftonline.com/" + TENANT + "/oauth2/v2.0/devicecode");
	private static final URI TOKEN = URI.create("https://login.microsoftonline.com/" + TENANT + "/oauth2/v2.0/token");
	private static final URI XBL = URI.create("https://user.auth.xboxlive.com/user/authenticate");
	private static final URI XSTS = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
	private static final URI MC_LOGIN = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
	private static final URI MC_ENTITLEMENTS = URI.create("https://api.minecraftservices.com/entitlements/mcstore");
	private static final URI MC_PROFILE = URI.create("https://api.minecraftservices.com/minecraft/profile");

	/** What the user must do to finish signing in. */
	public record DeviceCode(String userCode, String verificationUri, String message, Instant expiresAt, int intervalSeconds, String deviceCode) {
	}

	private final String clientId;

	public MicrosoftAuth(String clientId) {
		if (clientId == null || clientId.isBlank()) {
			throw new IllegalArgumentException("A Microsoft (Azure) application client id is required");
		}
		this.clientId = clientId.trim();
	}

	/** Step 1: ask Microsoft for a code the user enters at microsoft.com/link. */
	public DeviceCode requestDeviceCode() throws AuthException {
		JsonObject json = postForm(DEVICE_CODE, Map.of("client_id", clientId, "scope", SCOPE), "start Microsoft sign-in");
		return new DeviceCode(
			json.get("user_code").getAsString(),
			json.get("verification_uri").getAsString(),
			json.has("message") ? json.get("message").getAsString() : null,
			Instant.now().plusSeconds(json.get("expires_in").getAsLong()),
			json.has("interval") ? json.get("interval").getAsInt() : 5,
			json.get("device_code").getAsString());
	}

	/**
	 * Step 2: poll until the user approved the sign-in, then complete the full chain.
	 *
	 * @param cancelled polled between attempts; return true to abort
	 */
	public MinecraftAccount awaitDeviceLogin(DeviceCode code, BooleanSupplier cancelled) throws AuthException {
		int interval = Math.max(1, code.intervalSeconds());
		while (Instant.now().isBefore(code.expiresAt())) {
			if (cancelled.getAsBoolean()) {
				throw new AuthException("Sign-in cancelled.");
			}
			sleep(Duration.ofSeconds(interval));
			Map<String, String> form = new LinkedHashMap<>();
			form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
			form.put("client_id", clientId);
			form.put("device_code", code.deviceCode());
			try {
				String body = Http.postForm(TOKEN, form);
				JsonObject token = JsonParser.parseString(body).getAsJsonObject();
				return completeLogin(token.get("access_token").getAsString(), token.get("refresh_token").getAsString());
			} catch (HttpException e) {
				String error = oauthError(e);
				switch (error) {
					case "authorization_pending" -> {
					}
					case "slow_down" -> interval += 5;
					case "authorization_declined" -> throw new AuthException("Sign-in was declined in the browser.");
					case "expired_token", "bad_verification_code" -> throw new AuthException("The sign-in code expired. Please try again.");
					default -> throw new AuthException("Microsoft sign-in failed: " + (error.isEmpty() ? e.getMessage() : error), e);
				}
			} catch (IOException e) {
				throw new AuthException("Could not reach Microsoft: " + e.getMessage(), e);
			}
		}
		throw new AuthException("The sign-in code expired. Please try again.");
	}

	/** Signs in again using the stored refresh token. No user interaction required. */
	public MinecraftAccount refresh(MinecraftAccount account) throws AuthException {
		if (account.microsoftRefreshToken() == null) {
			throw new AuthException("This account must be signed in again.");
		}
		Map<String, String> form = new LinkedHashMap<>();
		form.put("grant_type", "refresh_token");
		form.put("client_id", clientId);
		form.put("refresh_token", account.microsoftRefreshToken());
		form.put("scope", SCOPE);
		JsonObject token = postForm(TOKEN, form, "refresh the Microsoft session");
		String refresh = token.has("refresh_token") ? token.get("refresh_token").getAsString() : account.microsoftRefreshToken();
		return completeLogin(token.get("access_token").getAsString(), refresh);
	}

	/** Returns a usable account, refreshing tokens only when they are about to expire. */
	public MinecraftAccount ensureValid(MinecraftAccount account) throws AuthException {
		return account.needsRefresh() ? refresh(account) : account;
	}

	private MinecraftAccount completeLogin(String msAccessToken, String msRefreshToken) throws AuthException {
		// Xbox Live user token
		JsonObject xblBody = new JsonObject();
		JsonObject xblProps = new JsonObject();
		xblProps.addProperty("AuthMethod", "RPS");
		xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
		xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
		xblBody.add("Properties", xblProps);
		xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
		xblBody.addProperty("TokenType", "JWT");
		JsonObject xbl = postJson(XBL, xblBody, null, "sign in to Xbox Live");
		String xblToken = xbl.get("Token").getAsString();
		String userHash = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

		// XSTS token for Minecraft services
		JsonObject xstsBody = new JsonObject();
		JsonObject xstsProps = new JsonObject();
		xstsProps.addProperty("SandboxId", "RETAIL");
		JsonArray userTokens = new JsonArray();
		userTokens.add(xblToken);
		xstsProps.add("UserTokens", userTokens);
		xstsBody.add("Properties", xstsProps);
		xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		xstsBody.addProperty("TokenType", "JWT");
		JsonObject xsts;
		try {
			xsts = postJsonRaw(XSTS, xstsBody, null);
		} catch (HttpException e) {
			throw new AuthException(describeXstsError(e), e);
		} catch (IOException e) {
			throw new AuthException("Could not reach Xbox Live: " + e.getMessage(), e);
		}
		String xstsToken = xsts.get("Token").getAsString();
		String xuid = "";
		JsonElement claims = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0);
		if (claims.getAsJsonObject().has("xid")) {
			xuid = claims.getAsJsonObject().get("xid").getAsString();
		}

		// Minecraft access token
		JsonObject mcBody = new JsonObject();
		mcBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
		JsonObject mc;
		try {
			mc = postJsonRaw(MC_LOGIN, mcBody, null);
		} catch (HttpException e) {
			if (e.status() == 403) {
				throw new AuthException("This Azure application is not approved for Minecraft sign-in yet. "
					+ "Request access at https://aka.ms/mce-reviewappid or use an approved client id.", e);
			}
			throw new AuthException("Minecraft sign-in failed (HTTP " + e.status() + ").", e);
		} catch (IOException e) {
			throw new AuthException("Could not reach Minecraft services: " + e.getMessage(), e);
		}
		String mcToken = mc.get("access_token").getAsString();
		long expiry = System.currentTimeMillis() + mc.get("expires_in").getAsLong() * 1000L;

		// Ownership + profile. Game Pass accounts may have no store entitlement but still have a profile.
		JsonObject profile;
		try {
			profile = getJson(MC_PROFILE, mcToken);
		} catch (HttpException e) {
			if (e.status() == 404) {
				boolean owns = ownsGame(mcToken);
				throw new AuthException(owns
					? "This account owns Minecraft but has no profile yet. Launch the official launcher once to choose a name."
					: "This Microsoft account does not own Minecraft: Java Edition.", e);
			}
			throw new AuthException("Could not load the Minecraft profile (HTTP " + e.status() + ").", e);
		} catch (IOException e) {
			throw new AuthException("Could not load the Minecraft profile: " + e.getMessage(), e);
		}
		String skin = null;
		if (profile.get("skins") instanceof JsonArray skins) {
			for (JsonElement s : skins) {
				JsonObject skinObject = s.getAsJsonObject();
				if (skinObject.has("state") && "ACTIVE".equals(skinObject.get("state").getAsString())) {
					skin = skinObject.get("url").getAsString();
				}
			}
		}
		return new MinecraftAccount(profile.get("id").getAsString(), profile.get("name").getAsString(),
			msRefreshToken, mcToken, expiry, xuid, skin);
	}

	private boolean ownsGame(String mcToken) {
		try {
			JsonObject entitlements = getJson(MC_ENTITLEMENTS, mcToken);
			return entitlements.has("items") && !entitlements.getAsJsonArray("items").isEmpty();
		} catch (IOException e) {
			return false;
		}
	}

	private static String describeXstsError(HttpException e) {
		try {
			JsonObject json = JsonParser.parseString(e.body()).getAsJsonObject();
			long code = json.has("XErr") ? json.get("XErr").getAsLong() : 0;
			if (code == 2148916233L) {
				return "This Microsoft account has no Xbox profile. Sign in at minecraft.net once to create one.";
			}
			if (code == 2148916235L) {
				return "Xbox Live is not available in your country.";
			}
			if (code == 2148916236L || code == 2148916237L) {
				return "This account needs adult verification on the Xbox website.";
			}
			if (code == 2148916238L) {
				return "This is a child account. An adult must add it to a Microsoft family first.";
			}
		} catch (RuntimeException ignored) {
			// fall through
		}
		return "Xbox Live sign-in failed (HTTP " + e.status() + ").";
	}

	private static String oauthError(HttpException e) {
		try {
			JsonObject json = JsonParser.parseString(e.body()).getAsJsonObject();
			return json.has("error") ? json.get("error").getAsString() : "";
		} catch (RuntimeException ignored) {
			return "";
		}
	}

	private static JsonObject postForm(URI uri, Map<String, String> form, String action) throws AuthException {
		try {
			return JsonParser.parseString(Http.postForm(uri, form)).getAsJsonObject();
		} catch (HttpException e) {
			String error = oauthError(e);
			if ("invalid_grant".equals(error)) {
				throw new AuthException("Your Microsoft session expired. Please sign in again.", e);
			}
			if ("unauthorized_client".equals(error) || "invalid_client".equals(error)) {
				throw new AuthException("The configured Azure client id is invalid or does not allow public client flows.", e);
			}
			throw new AuthException("Could not " + action + " (" + (error.isEmpty() ? "HTTP " + e.status() : error) + ").", e);
		} catch (IOException e) {
			throw new AuthException("Could not " + action + ": " + e.getMessage(), e);
		}
	}

	private static JsonObject postJson(URI uri, JsonObject body, String bearer, String action) throws AuthException {
		try {
			return postJsonRaw(uri, body, bearer);
		} catch (HttpException e) {
			throw new AuthException("Could not " + action + " (HTTP " + e.status() + ").", e);
		} catch (IOException e) {
			throw new AuthException("Could not " + action + ": " + e.getMessage(), e);
		}
	}

	private static JsonObject postJsonRaw(URI uri, JsonObject body, String bearer) throws IOException {
		HttpRequest.Builder builder = Http.request(uri)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		if (bearer != null) {
			builder.header("Authorization", "Bearer " + bearer);
		}
		return JsonParser.parseString(Http.send(builder.build())).getAsJsonObject();
	}

	private static JsonObject getJson(URI uri, String bearer) throws IOException {
		HttpRequest request = Http.request(uri).header("Authorization", "Bearer " + bearer).GET().build();
		return JsonParser.parseString(Http.send(request)).getAsJsonObject();
	}

	private static void sleep(Duration duration) throws AuthException {
		try {
			Thread.sleep(duration.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("Sign-in cancelled.");
		}
	}
}
