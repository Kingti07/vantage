package dev.vantage.client.module.hud;

import dev.vantage.client.VantageClient;
import dev.vantage.client.hud.HudAnchor;
import dev.vantage.client.hud.TextHudModule;
import dev.vantage.client.module.camera.ToggleSprintModule;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Single-value text HUD elements. Each nested class is an independent, separately configurable module. */
public final class StatHuds {
	private StatHuds() {
	}

	public static final class Fps extends TextHudModule {
		public Fps() {
			super("fps", "FPS", "Frames rendered per second.", true, HudAnchor.TOP_LEFT, 4, 4);
		}

		@Override
		protected String label() {
			return "FPS";
		}

		@Override
		protected String value(boolean preview) {
			return Integer.toString(MC.getFps());
		}
	}

	public static final class Ping extends TextHudModule {
		public Ping() {
			super("ping", "Ping", "Your latency to the server as shown in the player list.", true, HudAnchor.TOP_LEFT, 4, 20);
		}

		@Override
		protected String label() {
			return "Ping";
		}

		@Override
		protected String value(boolean preview) {
			if (MC.player == null || MC.getConnection() == null) {
				return preview ? "32 ms" : "-";
			}
			if (MC.isLocalServer()) {
				return "0 ms";
			}
			PlayerInfo info = MC.getConnection().getPlayerInfo(MC.player.getUUID());
			return info == null ? "-" : info.getLatency() + " ms";
		}
	}

	public static final class Cps extends TextHudModule {
		public enum Mode {
			LEFT("Left only"), RIGHT("Right only"), BOTH("Left | Right");

			private final String label;

			Mode(String label) {
				this.label = label;
			}

			@Override
			public String toString() {
				return label;
			}
		}

		private final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Buttons", "Which mouse buttons to count.", Mode.BOTH));

		public Cps() {
			super("cps", "CPS", "Clicks per second for the left and right mouse buttons.", true, HudAnchor.TOP_LEFT, 4, 36);
		}

		@Override
		protected String label() {
			return "CPS";
		}

		@Override
		protected String value(boolean preview) {
			var tracker = VantageClient.get().combat();
			return switch (mode.get()) {
				case LEFT -> Integer.toString(tracker.leftCps());
				case RIGHT -> Integer.toString(tracker.rightCps());
				case BOTH -> tracker.leftCps() + " | " + tracker.rightCps();
			};
		}
	}

	public static final class Biome extends TextHudModule {
		public Biome() {
			super("biome", "Biome", "The biome you are standing in.", false, HudAnchor.TOP_LEFT, 4, 84);
		}

		@Override
		protected String label() {
			return "Biome";
		}

		@Override
		protected String value(boolean preview) {
			if (MC.player == null || MC.level == null) {
				return "Plains";
			}
			String id = MC.level.getBiome(MC.player.blockPosition()).getRegisteredName();
			String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
			StringBuilder pretty = new StringBuilder();
			for (String word : path.split("_")) {
				if (!word.isEmpty()) {
					pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT)).append(' ');
				}
			}
			return pretty.toString().trim();
		}
	}

	public static final class Speed extends TextHudModule {
		public Speed() {
			super("speed", "Speed", "Horizontal movement speed in blocks per second.", false, HudAnchor.TOP_LEFT, 4, 100);
		}

		@Override
		protected String label() {
			return "Speed";
		}

		@Override
		protected String value(boolean preview) {
			return String.format(Locale.ROOT, "%.2f m/s", VantageClient.get().combat().speed());
		}
	}

	public static final class Reach extends TextHudModule {
		private final NumberSetting timeout = add(new NumberSetting("timeout", "Show for", "How long the last reach stays visible.", 3, 1, 10, 1, "s"));

		public Reach() {
			super("reach", "Reach", "Distance of your last landed hit, measured from your eyes to the target hitbox.", false, HudAnchor.MIDDLE_LEFT, 4, 10);
		}

		@Override
		protected String label() {
			return "Reach";
		}

		@Override
		protected String value(boolean preview) {
			double reach = VantageClient.get().combat().lastReach((long) (timeout.get() * 1000));
			if (reach < 0) {
				return preview ? "2.94 blocks" : "-";
			}
			return String.format(Locale.ROOT, "%.2f blocks", reach);
		}
	}

	public static final class Combo extends TextHudModule {
		public Combo() {
			super("combo", "Combo", "Hits landed in a row without taking damage.", false, HudAnchor.MIDDLE_LEFT, 4, 26);
		}

		@Override
		protected String label() {
			return "Combo";
		}

		@Override
		protected String value(boolean preview) {
			int combo = VantageClient.get().combat().combo();
			return combo == 0 ? "No combo" : combo + (combo == 1 ? " hit" : " hits");
		}
	}

	public static final class Hits extends TextHudModule {
		public Hits() {
			super("hits", "Hit Counter", "Landed hits since joining the world or server.", false, HudAnchor.MIDDLE_LEFT, 4, 42);
		}

		@Override
		protected String label() {
			return "Hits";
		}

		@Override
		protected String value(boolean preview) {
			return Integer.toString(VantageClient.get().combat().hits());
		}
	}

	public static final class Kills extends TextHudModule {
		public Kills() {
			super("kills", "Kill Counter", "Entities that died shortly after you hit them, this session.", false, HudAnchor.MIDDLE_LEFT, 4, 58);
		}

		@Override
		protected String label() {
			return "Kills";
		}

		@Override
		protected String value(boolean preview) {
			return Integer.toString(VantageClient.get().combat().kills());
		}
	}

	public static final class WTaps extends TextHudModule {
		public WTaps() {
			super("wtaps", "W-Tap Counter", "Landed hits where you reset your sprint since the previous hit.", false, HudAnchor.MIDDLE_LEFT, 4, 74);
		}

		@Override
		protected String label() {
			return "W-Taps";
		}

		@Override
		protected String value(boolean preview) {
			var combat = VantageClient.get().combat();
			int hits = combat.hits();
			int taps = combat.wtaps();
			return taps + (hits > 0 ? " (" + Math.round(taps * 100f / hits) + "%)" : "");
		}
	}

	public static final class Clock extends TextHudModule {
		private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
		private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
		private final BooleanSetting twelveHour = add(new BooleanSetting("twelveHour", "12-hour clock", "Use AM/PM format.", false));

		public Clock() {
			super("clock", "Clock", "Your local time.", false, HudAnchor.TOP_RIGHT, -4, 4);
		}

		@Override
		protected String label() {
			return "Time";
		}

		@Override
		protected String value(boolean preview) {
			return LocalTime.now().format(twelveHour.enabled() ? H12 : H24);
		}
	}

	/** Shows whether sprint/sneak are toggled by the Toggle Sprint module or held normally. */
	public static final class SprintStatus extends TextHudModule {
		public SprintStatus() {
			super("sprintstatus", "Sprint Status", "Shows sprint and sneak state, including toggled states.", true, HudAnchor.BOTTOM_LEFT, 4, -4);
			showLabel.overrideDefault(false);
		}

		@Override
		protected String label() {
			return "Status";
		}

		@Override
		public boolean hasContent() {
			return !value(false).isEmpty();
		}

		@Override
		protected String value(boolean preview) {
			ToggleSprintModule toggle = VantageClient.get().modules().get(ToggleSprintModule.class);
			String status = toggle != null ? toggle.status() : "";
			if (status.isEmpty() && MC.player != null) {
				if (MC.player.isShiftKeyDown()) {
					status = "Sneaking (Vanilla)";
				} else if (MC.player.isSprinting()) {
					status = "Sprinting (Vanilla)";
				}
			}
			return status.isEmpty() && preview ? "Sprinting (Toggled)" : status;
		}
	}
}
