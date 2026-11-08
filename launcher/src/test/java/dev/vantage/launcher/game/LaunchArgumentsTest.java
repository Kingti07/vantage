package dev.vantage.launcher.game;

import com.google.gson.JsonParser;
import dev.vantage.common.auth.MinecraftAccount;
import dev.vantage.common.util.Json;
import dev.vantage.launcher.settings.Instance;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchArgumentsTest {
	@Test
	void mavenPaths() {
		assertEquals("net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar", GameInstaller.mavenPath("net.fabricmc:fabric-loader:0.20.1"));
		assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar", GameInstaller.mavenPath("org.lwjgl:lwjgl:3.3.3:natives-windows"));
		assertEquals("a/b/c/1.0/c-1.0.zip", GameInstaller.mavenPath("a.b:c:1.0@zip"));
	}

	@Test
	void placeholdersAndJvmArgs() {
		assertEquals("--gameDir /x", GameLauncher.substitute("--gameDir ${game_directory}", Map.of("game_directory", "/x")));
		assertEquals("${unknown}", GameLauncher.substitute("${unknown}", Map.of()));
		assertEquals(List.of("-Xss2M", "-Dname=a b", "-XX:+UseZGC"), GameLauncher.splitArgs(" -Xss2M  \"-Dname=a b\" -XX:+UseZGC "));
	}

	@Test
	void buildsCommandFromVersionJsonAndMasksToken() {
		String json = """
			{
			  "id": "fabric-loader-0.20.1-1.21.11",
			  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
			  "arguments": {
			    "game": ["--username", "${auth_player_name}", "--accessToken", "${auth_access_token}",
			      {"rules": [{"action": "allow", "features": {"is_demo_user": true}}], "value": "--demo"},
			      {"rules": [{"action": "allow", "features": {"is_quick_play_multiplayer": true}}], "value": ["--quickPlayMultiplayer", "${quickPlayMultiplayer}"]}],
			    "jvm": ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"]
			  }
			}""";
		MojangModels.VersionJson version = Json.GSON.fromJson(JsonParser.parseString(json), MojangModels.VersionJson.class);
		GameInstaller.PreparedGame game = new GameInstaller.PreparedGame(version, "1.21.11", List.of(Path.of("a.jar"), Path.of("b.jar")),
			Path.of("natives"), Path.of("assets"), "29", null, null, Path.of("java"));
		Instance instance = new Instance();
		instance.id = "test";
		instance.memoryMb = 2048;
		MinecraftAccount account = new MinecraftAccount("0123456789abcdef0123456789abcdef", "Steve", "r", "SECRET", Long.MAX_VALUE, "1", null);
		List<String> command = GameLauncher.buildCommand(game, instance, Path.of("game"), account, "client", Path.of("root"),
			new GameLauncher.QuickPlay("play.example.net", null));
		assertTrue(command.contains("-Xmx2048M"));
		assertTrue(command.contains("Steve"));
		assertTrue(command.contains("--quickPlayMultiplayer"));
		assertTrue(command.contains("play.example.net"));
		assertFalse(command.contains("--demo"));
		assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", command.get(command.indexOf("-cp") + 2));
		String described = GameLauncher.describe(command, account);
		assertFalse(described.contains("SECRET"));
	}

	@Test
	void mergeKeepsChildLibrariesFirstAndDeduplicates() {
		String parent = """
			{"id":"1.21.11","mainClass":"net.minecraft.client.main.Main","libraries":[{"name":"org.ow2.asm:asm:9.0"},{"name":"com.mojang:x:1"}],
			 "arguments":{"game":["--a"],"jvm":["-Dp"]}}""";
		String child = """
			{"id":"fabric","inheritsFrom":"1.21.11","mainClass":"Knot","libraries":[{"name":"org.ow2.asm:asm:9.10.1","url":"https://maven.fabricmc.net/"}],
			 "arguments":{"game":[],"jvm":["-Dc"]}}""";
		var merged = GameInstaller.merge(Json.GSON.fromJson(parent, MojangModels.VersionJson.class), Json.GSON.fromJson(child, MojangModels.VersionJson.class));
		assertEquals("Knot", merged.mainClass());
		assertEquals(2, merged.libraries().size());
		assertEquals("org.ow2.asm:asm:9.10.1", merged.libraries().getFirst().name());
		assertEquals(2, merged.arguments().jvm().size());
	}
}
