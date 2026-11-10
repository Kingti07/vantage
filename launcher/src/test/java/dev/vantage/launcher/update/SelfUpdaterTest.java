package dev.vantage.launcher.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfUpdaterTest {
	private static JsonArray releases(String json) {
		return JsonParser.parseString(json).getAsJsonArray();
	}

	private static String release(String tag, boolean draft, boolean prerelease, String... assets) {
		StringBuilder list = new StringBuilder();
		for (String asset : assets) {
			if (!list.isEmpty()) {
				list.append(',');
			}
			list.append("{\"name\":\"").append(asset).append("\",\"browser_download_url\":\"https://example.com/").append(asset)
				.append("\",\"size\":1}");
		}
		return "{\"tag_name\":\"" + tag + "\",\"name\":\"" + tag + "\",\"body\":\"\",\"draft\":" + draft + ",\"prerelease\":" + prerelease
			+ ",\"assets\":[" + list + "]}";
	}

	@Test
	void picksNewestIncludingPreReleases() {
		JsonArray list = releases("[" + release("v0.3.0", true, false, "vantage-launcher-0.3.0-all-win.jar") + ","
			+ release("v0.2.1", false, true, "vantage-launcher-0.2.1-all-win.jar") + ","
			+ release("v0.2.0", false, false, "vantage-launcher-0.2.0-all-win.jar") + ","
			+ release("v0.1.0", false, true, "vantage-launcher-0.1.0-all-win.jar") + "]");
		Optional<SelfUpdater.Release> update = SelfUpdater.newest(list, "0.1.0", "win");
		assertTrue(update.isPresent());
		assertEquals("0.2.1", update.get().version());
	}

	@Test
	void ignoresOlderSameAndReleasesWithoutPlatformAsset() {
		JsonArray list = releases("[" + release("v0.2.0", false, false, "vantage-launcher-0.2.0-all-linux.jar") + ","
			+ release("v0.1.0", false, true, "vantage-launcher-0.1.0-all-win.jar") + "]");
		assertTrue(SelfUpdater.newest(list, "0.1.0", "win").isEmpty());
	}
}
