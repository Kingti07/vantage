package dev.vantage.client.module.hud;

import dev.vantage.client.hud.HudAnchor;
import dev.vantage.client.hud.HudModule;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import dev.vantage.client.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Multi-line and graphical information elements. */
public final class InfoHuds {
	private InfoHuds() {
	}

	static String cardinal(float yaw) {
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		int index = Math.floorMod(Math.round(Mth.wrapDegrees(yaw) / 45f), 8);
		return names[index];
	}

	public static final class Coordinates extends HudModule {
		public enum Layout {VERTICAL, HORIZONTAL}

		private final EnumSetting<Layout> layout = add(new EnumSetting<>("layout", "Layout", "One line or one line per axis.", Layout.VERTICAL));
		private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals", "Digits after the decimal point.", 1, 0, 3, 1));
		private final BooleanSetting facing = add(new BooleanSetting("facing", "Show facing", "Append the direction you are looking.", true));
		private final BooleanSetting nether = add(new BooleanSetting("nether", "Nether coordinates", "Show the matching Overworld/Nether position.", false));

		public Coordinates() {
			super("coordinates", "Coordinates", "Your X, Y and Z position.", true, HudAnchor.TOP_LEFT, 4, 52);
		}

		private List<String[]> lines(boolean preview) {
			double x = 120.5;
			double y = 64;
			double z = -48.3;
			float yaw = 0;
			boolean inNether = false;
			if (MC.player != null) {
				x = MC.player.getX();
				y = MC.player.getY();
				z = MC.player.getZ();
				yaw = MC.player.getYRot();
				inNether = MC.level != null && MC.level.dimension() == net.minecraft.world.level.Level.NETHER;
			}
			String format = "%." + decimals.intValue() + "f";
			List<String[]> lines = new ArrayList<>();
			String fx = String.format(Locale.ROOT, format, x);
			String fy = String.format(Locale.ROOT, format, y);
			String fz = String.format(Locale.ROOT, format, z);
			if (layout.get() == Layout.HORIZONTAL) {
				lines.add(new String[]{"XYZ: ", fx + " " + fy + " " + fz + (facing.enabled() ? " " + cardinal(yaw) : "")});
			} else {
				lines.add(new String[]{"X: ", fx});
				lines.add(new String[]{"Y: ", fy});
				lines.add(new String[]{"Z: ", fz});
				if (facing.enabled()) {
					lines.add(new String[]{"F: ", cardinal(yaw)});
				}
			}
			if (nether.enabled()) {
				double factor = inNether ? 8 : 1 / 8.0;
				lines.add(new String[]{inNether ? "OW: " : "Nether: ",
					String.format(Locale.ROOT, "%.0f %.0f", x * factor, z * factor)});
			}
			return lines;
		}

		@Override
		public int contentWidth(boolean preview) {
			int width = 0;
			for (String[] line : lines(preview)) {
				width = Math.max(width, textWidth(line[0]) + textWidth(line[1]));
			}
			return width;
		}

		@Override
		public int contentHeight(boolean preview) {
			return lines(preview).size() * 10 - 2;
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			int row = 0;
			for (String[] line : lines(preview)) {
				drawText(g, line[0], x, y + row * 10, accent());
				drawText(g, line[1], x + textWidth(line[0]), y + row * 10, text());
				row++;
			}
		}
	}

	/** Compass strip that scrolls with your view direction. */
	public static final class Direction extends HudModule {
		private final NumberSetting width = add(new NumberSetting("width", "Width", "Width of the compass strip.", 120, 60, 240, 10, "px"));
		private final BooleanSetting degrees = add(new BooleanSetting("degrees", "Show degrees", "Show the exact yaw below the strip.", true));

		public Direction() {
			super("direction", "Direction", "A compass showing where you are facing.", false, HudAnchor.TOP_CENTER, 0, 4);
		}

		@Override
		public int contentWidth(boolean preview) {
			return width.intValue();
		}

		@Override
		public int contentHeight(boolean preview) {
			return degrees.enabled() ? 22 : 12;
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			float yaw = MC.player != null ? Mth.wrapDegrees(MC.player.getYRot()) : 0;
			int w = width.intValue();
			float pixelsPerDegree = w / 180f;
			g.enableScissor(x, y, x + w, y + 12);
			String[] labels = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
			for (int step = -8; step <= 16; step++) {
				float angle = step * 45f;
				float delta = Mth.wrapDegrees(angle - yaw);
				int px = Math.round(x + w / 2f + delta * pixelsPerDegree);
				String label = labels[Math.floorMod(step, 8)];
				int color = label.length() == 1 ? text() : fade(Colors.withAlpha(text(), 160), 1);
				drawText(g, label, px - textWidth(label) / 2, y + 2, color);
			}
			for (int tick = -180; tick < 360; tick += 15) {
				float delta = Mth.wrapDegrees(tick - yaw);
				int px = Math.round(x + w / 2f + delta * pixelsPerDegree);
				if (tick % 45 != 0) {
					g.fill(px, y + 4, px + 1, y + 8, Colors.withAlpha(text(), 90));
				}
			}
			g.disableScissor();
			g.fill(x + w / 2, y + 10, x + w / 2 + 1, y + 12, accent());
			if (degrees.enabled()) {
				String value = String.format(Locale.ROOT, "%.1f°", (yaw + 360) % 360);
				drawText(g, value, x + w / 2 - textWidth(value) / 2, y + 14, accent());
			}
		}
	}

	/** Durability of the held item(s). */
	public static final class Durability extends HudModule {
		public enum Display {NUMBER, PERCENT, BOTH}

		private final EnumSetting<Display> display = add(new EnumSetting<>("display", "Display", "How durability is shown.", Display.NUMBER));
		private final BooleanSetting offhand = add(new BooleanSetting("offhand", "Include offhand", "Also show the offhand item.", true));

		public Durability() {
			super("durability", "Durability", "Durability of the items in your hands.", false, HudAnchor.BOTTOM_RIGHT, -4, -40);
		}

		private List<ItemStack> items(boolean preview) {
			List<ItemStack> items = new ArrayList<>();
			if (MC.player != null) {
				if (MC.player.getMainHandItem().isDamageableItem()) {
					items.add(MC.player.getMainHandItem());
				}
				if (offhand.enabled() && MC.player.getOffhandItem().isDamageableItem()) {
					items.add(MC.player.getOffhandItem());
				}
			}
			if (items.isEmpty() && preview) {
				items.add(new ItemStack(Items.DIAMOND_SWORD));
			}
			return items;
		}

		@Override
		public boolean hasContent() {
			return !items(false).isEmpty();
		}

		private String label(ItemStack stack) {
			int max = stack.getMaxDamage();
			int left = max - stack.getDamageValue();
			int percent = max == 0 ? 100 : Math.round(left * 100f / max);
			return switch (display.get()) {
				case NUMBER -> Integer.toString(left);
				case PERCENT -> percent + "%";
				case BOTH -> left + " (" + percent + "%)";
			};
		}

		@Override
		public int contentWidth(boolean preview) {
			int width = 0;
			for (ItemStack stack : items(preview)) {
				width = Math.max(width, 20 + textWidth(label(stack)));
			}
			return width;
		}

		@Override
		public int contentHeight(boolean preview) {
			return Math.max(16, items(preview).size() * 18 - 2);
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			int row = 0;
			for (ItemStack stack : items(preview)) {
				g.renderItem(stack, x, y + row * 18);
				float fraction = stack.getMaxDamage() == 0 ? 1 : 1 - stack.getDamageValue() / (float) stack.getMaxDamage();
				int color = Colors.lerp(0xFFFF5C7A, 0xFF3DDC97, fraction);
				drawText(g, label(stack), x + 20, y + row * 18 + 4, fade(color, opacity.floatValue()));
				row++;
			}
		}
	}

	/** FPS graph, frame time, memory and world statistics. */
	public static final class Performance extends HudModule {
		private static final int SAMPLES = 90;
		private final long[] frameTimes = new long[SAMPLES];
		private int index;
		private long lastFrame = System.nanoTime();
		private final BooleanSetting graph = add(new BooleanSetting("graph", "Frame time graph", "Show a graph of recent frame times.", true));
		private final BooleanSetting memory = add(new BooleanSetting("memory", "Memory", "Show Java heap usage.", true));
		private final BooleanSetting world = add(new BooleanSetting("world", "World stats", "Show entity and particle counts.", true));

		public Performance() {
			super("performance", "Performance Monitor", "Frame times, memory usage and world statistics.", false, HudAnchor.TOP_RIGHT, -4, 20);
		}

		private List<String[]> lines() {
			List<String[]> lines = new ArrayList<>();
			long sum = 0;
			long worst = 0;
			for (long t : frameTimes) {
				sum += t;
				worst = Math.max(worst, t);
			}
			double avgMs = sum / (double) SAMPLES / 1_000_000.0;
			lines.add(new String[]{"FPS: ", MC.getFps() + String.format(Locale.ROOT, "  (%.1f ms, max %.1f)", avgMs, worst / 1_000_000.0)});
			if (memory.enabled()) {
				Runtime runtime = Runtime.getRuntime();
				long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
				long max = runtime.maxMemory() / (1024 * 1024);
				lines.add(new String[]{"Memory: ", used + " / " + max + " MB (" + (used * 100 / Math.max(1, max)) + "%)"});
			}
			if (world.enabled() && MC.level != null) {
				lines.add(new String[]{"Entities: ", Integer.toString(MC.level.getEntityCount())});
				lines.add(new String[]{"Particles: ", MC.particleEngine.countParticles()});
			}
			return lines;
		}

		/** Called once per rendered frame by the HUD renderer. */
		public void sampleFrame() {
			long now = System.nanoTime();
			frameTimes[index] = now - lastFrame;
			index = (index + 1) % SAMPLES;
			lastFrame = now;
		}

		@Override
		public int contentWidth(boolean preview) {
			int width = graph.enabled() ? SAMPLES : 0;
			for (String[] line : lines()) {
				width = Math.max(width, textWidth(line[0]) + textWidth(line[1]));
			}
			return width;
		}

		@Override
		public int contentHeight(boolean preview) {
			return lines().size() * 10 - 2 + (graph.enabled() ? 26 : 0);
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			sampleFrame();
			int row = 0;
			for (String[] line : lines()) {
				drawText(g, line[0], x, y + row * 10, accent());
				drawText(g, line[1], x + textWidth(line[0]), y + row * 10, text());
				row++;
			}
			if (graph.enabled()) {
				int top = y + row * 10 + 2;
				int height = 22;
				Render2D.rect(g, x, top, SAMPLES, height, 0x40000000);
				for (int i = 0; i < SAMPLES; i++) {
					long t = frameTimes[(index + i) % SAMPLES];
					double ms = t / 1_000_000.0;
					int bar = (int) Math.min(height, Math.round(ms / 50.0 * height));
					int color = ms < 17 ? 0xC03DDC97 : ms < 34 ? 0xC0FFB547 : 0xC0FF5C7A;
					g.fill(x + i, top + height - bar, x + i + 1, top + height, fade(color, opacity.floatValue()));
				}
			}
		}
	}
}
