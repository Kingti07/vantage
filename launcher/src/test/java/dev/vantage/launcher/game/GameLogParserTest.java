package dev.vantage.launcher.game;

import dev.vantage.launcher.game.GameLogParser.Level;
import dev.vantage.launcher.game.GameLogParser.LogLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLogParserTest {
	private static List<LogLine> feedAll(String output) {
		GameLogParser parser = new GameLogParser();
		List<LogLine> lines = new ArrayList<>();
		for (String raw : output.split("\n")) {
			lines.addAll(parser.feed(raw));
		}
		return lines;
	}

	@Test
	void parsesXmlEventsIntoReadableLines() {
		List<LogLine> lines = feedAll("""
			<log4j:Event logger="net.minecraft.client.Minecraft" timestamp="1762780000000" level="INFO" thread="Render thread">
			  <log4j:Message><![CDATA[Setting user: Alice]]></log4j:Message>
			</log4j:Event>
			""");
		assertEquals(1, lines.size());
		LogLine line = lines.getFirst();
		assertEquals(Level.INFO, line.level());
		assertEquals("Render thread", line.thread());
		assertEquals("Setting user: Alice", line.text());
		assertTrue(line.format().endsWith("[Render thread/INFO]: Setting user: Alice"), line.format());
	}

	@Test
	void keepsStackTracesAndMultiLineMessages() {
		List<LogLine> lines = feedAll("""
			<log4j:Event logger="x" timestamp="1" level="ERROR" thread="main">
			  <log4j:Message><![CDATA[First line
			second line]]></log4j:Message>
			  <log4j:Throwable><![CDATA[java.lang.IllegalStateException: boom
				at a.b.C.d(C.java:1)
			]]></log4j:Throwable>
			</log4j:Event>
			""");
		assertEquals(List.of("First line", "second line", "java.lang.IllegalStateException: boom", "\tat a.b.C.d(C.java:1)"),
			lines.stream().map(LogLine::text).toList());
		assertTrue(lines.stream().allMatch(l -> l.level() == Level.ERROR));
	}

	@Test
	void joinsSplitCdataSections() {
		List<LogLine> lines = feedAll("""
			<log4j:Event logger="x" timestamp="1" level="WARN" thread="main">
			  <log4j:Message><![CDATA[a]]]]><![CDATA[>b]]></log4j:Message>
			</log4j:Event>
			""");
		assertEquals("a]]>b", lines.getFirst().text());
		assertEquals(Level.WARN, lines.getFirst().level());
	}

	@Test
	void plainOutputPassesThrough() {
		List<LogLine> lines = feedAll("Picked up JAVA_TOOL_OPTIONS: -Dx\n[Vantage] Preparing\nException in thread \"main\" java.lang.Error");
		assertEquals(Level.PLAIN, lines.get(0).level());
		assertEquals(Level.LAUNCHER, lines.get(1).level());
		assertEquals(Level.ERROR, lines.get(2).level());
		assertEquals("[Vantage] Preparing", lines.get(1).format());
	}
}
