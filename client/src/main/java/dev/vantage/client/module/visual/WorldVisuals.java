package dev.vantage.client.module.visual;

import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import net.minecraft.resources.Identifier;

/**
 * Purely visual, client-side world tweaks. Each is applied by a mixin that checks the module first; none of
 * them changes what the server sees.
 */
public final class WorldVisuals {
	private WorldVisuals() {
	}

	/** Maximum brightness everywhere (like permanent night vision, without the effect). */
	public static final class Fullbright extends Module {
		public Fullbright() {
			super("fullbright", "Fullbright", "See clearly in the dark.", Category.VISUAL, false);
			toggleKeybind();
		}
	}

	public static final class LowFire extends Module {
		public final NumberSetting height = add(new NumberSetting("height", "Lower by", "How far the fire overlay is moved down.", 0.3, 0, 0.5, 0.05));

		public LowFire() {
			super("lowfire", "Low Fire", "Lowers the burning overlay so it covers less of the screen.", Category.VISUAL, true);
		}
	}

	public static final class ClearWater extends Module {
		public final BooleanSetting removeOverlay = add(new BooleanSetting("overlay", "Remove overlay", "Hide the underwater texture overlay.", true));
		public final NumberSetting visibility = add(new NumberSetting("visibility", "Visibility", "Fog distance underwater in blocks.", 96, 16, 256, 8));

		public ClearWater() {
			super("clearwater", "Clear Water", "See much further underwater.", Category.VISUAL, false);
		}
	}

	public static final class NoHurtCam extends Module {
		public final NumberSetting strength = add(new NumberSetting("strength", "Shake strength", "0% removes the camera shake completely.", 0, 0, 1, 0.05));

		public NoHurtCam() {
			super("nohurtcam", "No Hurt Cam", "Removes or reduces the camera shake when you take damage.", Category.VISUAL, true);
		}
	}

	public static final class CustomSky extends Module {
		public final ColorSetting skyColor = add(new ColorSetting("sky", "Sky colour", "Colour of the sky.", 0xFF6E8FFF));
		public final BooleanSetting fog = add(new BooleanSetting("fogEnabled", "Recolour fog", "Also change the horizon fog colour.", true));
		public final ColorSetting fogColor = add(new ColorSetting("fog", "Fog colour", "Colour of the fog.", 0xFFB5C6FF));
		public final BooleanSetting clouds = add(new BooleanSetting("cloudsEnabled", "Recolour clouds", "Also change the cloud colour.", false));
		public final ColorSetting cloudColor = add(new ColorSetting("clouds", "Cloud colour", "Colour of the clouds.", 0xFFFFFFFF));

		public CustomSky() {
			super("customsky", "Custom Sky", "Recolour the sky, fog and clouds.", Category.VISUAL, false);
			fogColor.visibleWhen(fog::enabled);
			cloudColor.visibleWhen(clouds::enabled);
		}
	}

	public static final class CustomWeather extends Module {
		public enum Weather {CLEAR, RAIN, THUNDER}

		public final EnumSetting<Weather> weather = add(new EnumSetting<>("weather", "Weather", "Weather shown on your screen.", Weather.CLEAR));

		public CustomWeather() {
			super("customweather", "Custom Weather", "Show the weather you prefer, client-side only.", Category.VISUAL, false);
		}

		public float rain() {
			return weather.get() == Weather.CLEAR ? 0f : 1f;
		}

		public float thunder() {
			return weather.get() == Weather.THUNDER ? 1f : 0f;
		}
	}

	public static final class CustomTime extends Module {
		public final NumberSetting time = add(new NumberSetting("time", "Time of day", "0 = sunrise, 6000 = noon, 18000 = midnight.", 6000, 0, 23999, 100));
		public final BooleanSetting realTime = add(new BooleanSetting("realTime", "Follow real time", "Match the sky to your local clock.", false));

		public CustomTime() {
			super("customtime", "Custom Time", "Fix the time of day on your screen, client-side only.", Category.VISUAL, false);
			time.visibleWhen(() -> !realTime.enabled());
		}

		public long dayTime(long serverDayTime) {
			long day = Math.floorDiv(serverDayTime, 24000L) * 24000L;
			if (realTime.enabled()) {
				java.time.LocalTime now = java.time.LocalTime.now();
				double hours = now.getHour() + now.getMinute() / 60.0;
				// Minecraft time 0 is 06:00.
				return day + (long) (((hours - 6 + 24) % 24) * 1000);
			}
			return day + time.intValue();
		}
	}

	/** Dropped items lie flat on the ground instead of spinning and bobbing. */
	public static final class ItemPhysics extends Module {
		public ItemPhysics() {
			super("itemphysics", "Item Physics", "Dropped items lie flat on the ground.", Category.VISUAL, false);
		}
	}

	/** Blends each frame with the previous ones using a post-processing shader. */
	public static final class MotionBlur extends Module {
		public final NumberSetting strength = add(new NumberSetting("strength", "Strength", "How long previous frames linger.", 5, 1, 9, 1));

		public MotionBlur() {
			super("motionblur", "Motion Blur", "Smooth motion blur on camera movement.", Category.VISUAL, false);
		}

		public Identifier effect() {
			return Identifier.fromNamespaceAndPath("vantage", "motion_blur_" + strength.intValue());
		}
	}
}
