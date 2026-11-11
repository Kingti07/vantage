package dev.vantage.client.module.utility;

import dev.vantage.client.VantageClient;
import dev.vantage.client.combat.CombatTracker;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Quality-of-life modules that are neither HUD nor PvP. */
public final class UtilityModules {
	private UtilityModules() {
	}

	/** Writes your death position into the chat (only you see it). */
	public static final class DeathCoordinates extends Module {
		public final BooleanSetting copy = add(new BooleanSetting("copy", "Copy to clipboard", "Also copy the coordinates, ready to paste.", false));
		private int seenDeaths;

		public DeathCoordinates() {
			super("deathcoords", "Death Coordinates", "Shows where you died in the chat. Only you see the message.", Category.UTILITY, true);
		}

		@Override
		public void onTick() {
			CombatTracker combat = VantageClient.get().combat();
			int deaths = combat.deaths();
			if (deaths < seenDeaths) {
				seenDeaths = deaths;
			}
			if (deaths > seenDeaths && combat.lastDeath() != null && MC.player != null) {
				seenDeaths = deaths;
				CombatTracker.DeathPoint point = combat.lastDeath();
				String coords = String.format(Locale.ROOT, "%d %d %d", (int) Math.floor(point.position().x), (int) Math.floor(point.position().y),
					(int) Math.floor(point.position().z));
				MutableComponent message = Component.literal("☠ ").withStyle(ChatFormatting.RED)
					.append(Component.literal("You died at ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(coords).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (" + point.dimension().replace('_', ' ') + ")").withStyle(ChatFormatting.GRAY));
				MC.gui.getChat().addMessage(message);
				if (copy.enabled()) {
					MC.keyboardHandler.setClipboard(coords);
				}
			}
		}
	}

	/** Timestamps, stacked duplicate messages and a longer chat history. */
	public static final class ChatTweaks extends Module {
		/** Clock format of the timestamps. */
		public enum TimeFormat {
			H24("24 hours", "HH:mm"),
			H24_SECONDS("24 hours with seconds", "HH:mm:ss"),
			H12("12 hours", "h:mm a");

			private final String label;
			private final DateTimeFormatter formatter;

			TimeFormat(String label, String pattern) {
				this.label = label;
				this.formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
			}

			@Override
			public String toString() {
				return label;
			}
		}

		public final BooleanSetting timestamps = add(new BooleanSetting("timestamps", "Timestamps", "Show the time in front of every message.", true));
		public final EnumSetting<TimeFormat> format = add(new EnumSetting<>("format", "Time format", "How the time is written.", TimeFormat.H24));
		public final BooleanSetting stack = add(new BooleanSetting("stack", "Stack repeated messages",
			"Show a repeated message once with a counter, like \"(x3)\", instead of filling the chat.", true));
		public final NumberSetting history = add(new NumberSetting("history", "Chat history", "How many messages the chat keeps (Minecraft: 100).",
			500, 100, 2000, 50, ""));

		private String lastRaw;
		private int repeats;

		public ChatTweaks() {
			super("chattweaks", "Chat Tweaks", "Timestamps, stacked repeated messages and a longer chat history.", Category.UTILITY, true);
			format.visibleWhen(timestamps::enabled);
		}

		/** Size of the chat history the chat component keeps. */
		public int historySize() {
			return isEnabled() ? history.intValue() : 100;
		}

		/**
		 * Rewrites an incoming message. Returns true in {@code repeated[0]} when it repeats the newest message, which the
		 * caller then removes before the stacked copy is added.
		 */
		public Component decorate(Component message, boolean[] repeated) {
			repeated[0] = false;
			if (!isEnabled()) {
				return message;
			}
			MutableComponent result = message.copy();
			if (stack.enabled()) {
				String raw = message.getString();
				if (raw.equals(lastRaw)) {
					repeats++;
					repeated[0] = true;
					result.append(Component.literal(" (x" + repeats + ")").withStyle(ChatFormatting.GRAY));
				} else {
					lastRaw = raw;
					repeats = 1;
				}
			}
			if (timestamps.enabled()) {
				return Component.literal("[" + LocalTime.now().format(format.get().formatter) + "] ").withStyle(ChatFormatting.DARK_GRAY).append(result);
			}
			return result;
		}

		/** How often the newest message has been repeated so far. */
		public int repeats() {
			return repeats;
		}

		/** Forgets the last message, e.g. when the chat is cleared. */
		public void reset() {
			lastRaw = null;
			repeats = 0;
		}
	}
}
