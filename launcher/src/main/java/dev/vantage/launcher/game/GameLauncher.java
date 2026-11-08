package dev.vantage.launcher.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.vantage.common.VantageInfo;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.common.util.Json;
import dev.vantage.launcher.game.GameInstaller.PreparedGame;
import dev.vantage.launcher.game.MojangModels.Rule;
import dev.vantage.launcher.settings.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the JVM/game command line from a merged version JSON and starts the game process.
 */
public final class GameLauncher {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z_]+)}");

	/** Optional "Quick Play" target: join a server or open a world directly after start-up. */
	public record QuickPlay(String multiplayerAddress, String singleplayerWorld) {
		public static final QuickPlay NONE = new QuickPlay(null, null);
	}

	private GameLauncher() {
	}

	public static List<String> buildCommand(PreparedGame game, Instance instance, Path gameDir, MinecraftAccount account,
		String clientId, Path launcherDir, QuickPlay quickPlay) {
		Map<String, Boolean> features = new HashMap<>();
		features.put("has_custom_resolution", instance.customResolution && !instance.fullscreen);
		features.put("is_demo_user", false);
		boolean quickMultiplayer = quickPlay.multiplayerAddress() != null && !quickPlay.multiplayerAddress().isBlank();
		boolean quickSingleplayer = quickPlay.singleplayerWorld() != null && !quickPlay.singleplayerWorld().isBlank();
		features.put("has_quick_plays_support", quickMultiplayer || quickSingleplayer);
		features.put("is_quick_play_multiplayer", quickMultiplayer);
		features.put("is_quick_play_singleplayer", quickSingleplayer);

		Map<String, String> values = new HashMap<>();
		values.put("auth_player_name", account.username());
		values.put("version_name", game.version().id());
		values.put("game_directory", gameDir.toString());
		values.put("assets_root", game.assetsDir().toString());
		values.put("game_assets", game.assetsDir().toString());
		values.put("assets_index_name", game.assetIndexId());
		values.put("auth_uuid", account.uuid());
		values.put("auth_access_token", account.minecraftAccessToken());
		values.put("auth_session", account.minecraftAccessToken());
		values.put("clientid", clientId == null ? "" : clientId);
		values.put("auth_xuid", account.xuid() == null || account.xuid().isEmpty() ? "0" : account.xuid());
		values.put("user_type", "msa");
		values.put("user_properties", "{}");
		values.put("version_type", VantageInfo.NAME);
		values.put("resolution_width", Integer.toString(instance.width));
		values.put("resolution_height", Integer.toString(instance.height));
		values.put("natives_directory", game.nativesDir().toString());
		values.put("launcher_name", VantageInfo.NAME);
		values.put("launcher_version", VantageInfo.VERSION);
		values.put("library_directory", launcherDir.resolve("libraries").toString());
		values.put("classpath_separator", File.pathSeparator);
		values.put("classpath", String.join(File.pathSeparator, game.classpath().stream().map(Path::toString).distinct().toList()));
		values.put("quickPlayPath", gameDir.resolve("quickPlay").resolve("log.json").toString());
		values.put("quickPlayMultiplayer", quickMultiplayer ? quickPlay.multiplayerAddress().trim() : "");
		values.put("quickPlaySingleplayer", quickSingleplayer ? quickPlay.singleplayerWorld() : "");
		values.put("quickPlayRealms", "");

		List<String> command = new ArrayList<>();
		command.add(game.javaExecutable().toString());
		command.add("-Xmx" + instance.memoryMb + "M");
		command.add("-Xms" + Math.min(instance.memoryMb, 1024) + "M");
		// Sensible G1 defaults for a game client: short pauses matter more than throughput.
		command.add("-XX:+UseG1GC");
		command.add("-XX:MaxGCPauseMillis=50");
		command.add("-XX:+ParallelRefProcEnabled");
		command.add("-D" + VantageInfo.PROP_LAUNCHER_DIR + "=" + launcherDir);
		if (clientId != null && !clientId.isBlank()) {
			command.add("-D" + VantageInfo.PROP_MSA_CLIENT_ID + "=" + clientId);
		}
		if (game.loggingConfig() != null && game.loggingArgument() != null) {
			command.add(game.loggingArgument().replace("${path}", game.loggingConfig().toString()));
		}
		for (String arg : splitArgs(instance.extraJvmArgs)) {
			command.add(arg);
		}
		var arguments = game.version().arguments();
		if (arguments != null && arguments.jvm() != null && !arguments.jvm().isEmpty()) {
			addArguments(command, arguments.jvm(), features, values);
		} else {
			// Pre-1.13 versions have no JVM argument list.
			command.add("-Djava.library.path=" + game.nativesDir());
			command.add("-cp");
			command.add(values.get("classpath"));
		}
		command.add(game.version().mainClass());
		if (arguments != null && arguments.game() != null && !arguments.game().isEmpty()) {
			addArguments(command, arguments.game(), features, values);
		} else if (game.version().minecraftArguments() != null) {
			for (String arg : game.version().minecraftArguments().split(" ")) {
				command.add(substitute(arg, values));
			}
		}
		if (instance.fullscreen) {
			command.add("--fullscreen");
		}
		return command;
	}

	public static Process start(List<String> command, Path gameDir) throws IOException {
		Files.createDirectories(gameDir);
		ProcessBuilder builder = new ProcessBuilder(command).directory(gameDir.toFile()).redirectErrorStream(true);
		return builder.start();
	}

	/** Command line safe for logs: the access token is masked. */
	public static String describe(List<String> command, MinecraftAccount account) {
		String token = account.minecraftAccessToken();
		List<String> masked = new ArrayList<>();
		for (String arg : command) {
			masked.add(token != null && !token.isEmpty() ? arg.replace(token, "<access token>") : arg);
		}
		return String.join(" ", masked);
	}

	private static void addArguments(List<String> out, List<JsonElement> args, Map<String, Boolean> features, Map<String, String> values) {
		for (JsonElement element : args) {
			if (element.isJsonPrimitive()) {
				out.add(substitute(element.getAsString(), values));
				continue;
			}
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			List<Rule> rules = object.has("rules") ? Json.GSON.fromJson(object.get("rules"), new TypeToken<List<Rule>>() {
			}.getType()) : List.of();
			if (!Rules.allows(rules, features)) {
				continue;
			}
			JsonElement value = object.get("value");
			if (value == null) {
				continue;
			}
			if (value.isJsonArray()) {
				for (JsonElement v : (JsonArray) value) {
					out.add(substitute(v.getAsString(), values));
				}
			} else {
				out.add(substitute(value.getAsString(), values));
			}
		}
	}

	static String substitute(String arg, Map<String, String> values) {
		Matcher matcher = PLACEHOLDER.matcher(arg);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String replacement = values.getOrDefault(matcher.group(1), matcher.group(0));
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/** Splits user JVM arguments on whitespace, honouring double quotes. */
	static List<String> splitArgs(String args) {
		List<String> result = new ArrayList<>();
		if (args == null || args.isBlank()) {
			return result;
		}
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (char c : args.toCharArray()) {
			if (c == '"') {
				quoted = !quoted;
			} else if (Character.isWhitespace(c) && !quoted) {
				if (!current.isEmpty()) {
					result.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (!current.isEmpty()) {
			result.add(current.toString());
		}
		return result;
	}
}
