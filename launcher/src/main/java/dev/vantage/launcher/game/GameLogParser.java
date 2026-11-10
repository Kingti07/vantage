package dev.vantage.launcher.game;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Minecraft's console output into readable lines. When started by a launcher, Minecraft logs through
 * log4j's XML layout ({@code <log4j:Event ...><log4j:Message><![CDATA[...]]></log4j:Message></log4j:Event>}) so
 * that launchers can parse it; other output (JVM messages, crashes before logging starts) is plain text.
 * Not thread-safe; callers serialise access.
 */
public final class GameLogParser {
	public enum Level {ERROR, WARN, INFO, DEBUG, LAUNCHER, PLAIN}

	/** One display line. {@code time} is epoch millis, or 0 when unknown. */
	public record LogLine(long time, Level level, String thread, String text) {
		private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

		/** "[12:34:56] [Render thread/INFO]: message" for events, the plain text otherwise. */
		public String format() {
			if (time <= 0 || thread == null) {
				return text;
			}
			return "[" + CLOCK.format(Instant.ofEpochMilli(time)) + "] [" + thread + "/" + level + "]: " + text;
		}
	}

	private static final int MAX_EVENT_LINES = 5_000;
	private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)=\"([^\"]*)\"");
	private static final Pattern MESSAGE = Pattern.compile("<log4j:Message>(.*?)</log4j:Message>", Pattern.DOTALL);
	private static final Pattern THROWABLE = Pattern.compile("<log4j:Throwable>(.*?)</log4j:Throwable>", Pattern.DOTALL);

	private final StringBuilder event = new StringBuilder();
	private final List<String> eventRaw = new ArrayList<>();
	private boolean inEvent;

	/** Feeds one raw output line; returns the display lines it completes (often none while an event is open). */
	public List<LogLine> feed(String raw) {
		if (!inEvent) {
			if (!raw.stripLeading().startsWith("<log4j:Event")) {
				return List.of(plain(raw));
			}
			inEvent = true;
			event.setLength(0);
			eventRaw.clear();
		}
		event.append(raw).append('\n');
		eventRaw.add(raw);
		if (raw.contains("</log4j:Event>")) {
			inEvent = false;
			return parseEvent(event.toString());
		}
		if (eventRaw.size() > MAX_EVENT_LINES) {
			// Not a well-formed event after all; show what we have.
			inEvent = false;
			return eventRaw.stream().map(GameLogParser::plain).toList();
		}
		return List.of();
	}

	static List<LogLine> parseEvent(String xml) {
		int headerEnd = xml.indexOf('>');
		String header = headerEnd > 0 ? xml.substring(0, headerEnd) : xml;
		String levelName = "INFO";
		String thread = "main";
		long time = 0;
		Matcher attributes = ATTRIBUTE.matcher(header);
		while (attributes.find()) {
			switch (attributes.group(1)) {
				case "level" -> levelName = attributes.group(2);
				case "thread" -> thread = unescape(attributes.group(2));
				case "timestamp" -> time = parseLong(attributes.group(2));
				default -> {
				}
			}
		}
		Level level = level(levelName);
		List<LogLine> out = new ArrayList<>();
		Matcher message = MESSAGE.matcher(xml);
		String text = message.find() ? content(message.group(1)) : "";
		String[] lines = text.split("\\R", -1);
		out.add(new LogLine(time, level, thread, lines[0]));
		for (int i = 1; i < lines.length; i++) {
			if (!lines[i].isEmpty() || i < lines.length - 1) {
				out.add(new LogLine(0, level, null, lines[i]));
			}
		}
		Matcher throwable = THROWABLE.matcher(xml);
		if (throwable.find()) {
			for (String line : content(throwable.group(1)).split("\\R")) {
				if (!line.isBlank()) {
					out.add(new LogLine(0, level, null, line));
				}
			}
		}
		return out;
	}

	/** Undoes CDATA wrapping (log4j splits CDATA sections that contain "]]>") or XML escaping. */
	private static String content(String inner) {
		String trimmed = inner.strip();
		if (trimmed.startsWith("<![CDATA[")) {
			StringBuilder text = new StringBuilder();
			Matcher cdata = Pattern.compile("<!\\[CDATA\\[(.*?)]]>", Pattern.DOTALL).matcher(trimmed);
			while (cdata.find()) {
				text.append(cdata.group(1));
			}
			return text.toString();
		}
		return unescape(trimmed);
	}

	private static String unescape(String text) {
		return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
	}

	/** A line that is not part of a log event (launcher messages, JVM output). */
	public static LogLine plain(String raw) {
		if (raw.startsWith("[Vantage]")) {
			return new LogLine(0, Level.LAUNCHER, null, raw);
		}
		String upper = raw.toUpperCase(Locale.ROOT);
		Level level = upper.contains("/ERROR]") || upper.contains("[ERROR]") || upper.startsWith("EXCEPTION IN THREAD")
			? Level.ERROR : upper.contains("/WARN]") || upper.contains("[WARN]") ? Level.WARN : Level.PLAIN;
		return new LogLine(0, level, null, raw);
	}

	private static Level level(String name) {
		return switch (name.toUpperCase(Locale.ROOT)) {
			case "FATAL", "ERROR" -> Level.ERROR;
			case "WARN" -> Level.WARN;
			case "DEBUG", "TRACE" -> Level.DEBUG;
			default -> Level.INFO;
		};
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
