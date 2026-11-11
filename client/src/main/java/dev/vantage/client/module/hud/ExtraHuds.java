package dev.vantage.client.module.hud;

import dev.vantage.client.VantageClient;
import dev.vantage.client.hud.HudAnchor;
import dev.vantage.client.hud.HudModule;
import dev.vantage.client.hud.TextHudModule;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Session, server, item counters and warnings. Each nested class is an independent HUD element. */
public final class ExtraHuds {
	private ExtraHuds() {
	}

	/** Time since joining the current world or server. */
	public static final class SessionTime extends TextHudModule {
		public SessionTime() {
			super("session", "Session Time", "How long you have been in the current world or on the current server.", false,
				HudAnchor.TOP_LEFT, 4, 116);
		}

		@Override
		protected String label() {
			return "Session";
		}

		@Override
		protected String value(boolean preview) {
			long seconds = preview ? 3725 : VantageClient.get().combat().sessionMillis() / 1000;
			long hours = seconds / 3600;
			return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
				: String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
		}
	}

	/** Address of the server you are on. */
	public static final class Server extends TextHudModule {
		public Server() {
			super("server", "Server Address", "The address of the server you are playing on (Singleplayer in your own worlds).", false,
				HudAnchor.TOP_LEFT, 4, 132);
		}

		@Override
		protected String label() {
			return "Server";
		}

		@Override
		protected String value(boolean preview) {
			if (MC.hasSingleplayerServer()) {
				return "Singleplayer";
			}
			ServerData server = MC.getCurrentServer();
			return server != null ? server.ip : preview ? "play.example.net" : "-";
		}
	}

	/** Deaths this session. */
	public static final class Deaths extends TextHudModule {
		public Deaths() {
			super("deaths", "Death Counter", "How often you died since joining the world or server.", false, HudAnchor.MIDDLE_LEFT, 4, 90);
		}

		@Override
		protected String label() {
			return "Deaths";
		}

		@Override
		protected String value(boolean preview) {
			return Integer.toString(VantageClient.get().combat().deaths());
		}
	}

	/** Totems of Undying in your inventory. */
	public static final class Totems extends TextHudModule {
		public final BooleanSetting hideWhenNone = add(new BooleanSetting("hideWhenNone", "Hide when you have none",
			"Only show the counter while you carry at least one totem.", true));

		public Totems() {
			super("totems", "Totem Counter", "Totems of Undying in your inventory, including the off hand.", false, HudAnchor.MIDDLE_RIGHT, -4, 48);
		}

		@Override
		public boolean hasContent() {
			return !hideWhenNone.enabled() || count(stack -> stack.is(Items.TOTEM_OF_UNDYING)) > 0;
		}

		@Override
		protected String label() {
			return "Totems";
		}

		@Override
		protected String value(boolean preview) {
			int count = count(stack -> stack.is(Items.TOTEM_OF_UNDYING));
			return Integer.toString(preview && count == 0 ? 3 : count);
		}
	}

	/** Arrows in your inventory. */
	public static final class Arrows extends TextHudModule {
		public final BooleanSetting onlyWithBow = add(new BooleanSetting("onlyWithBow", "Only with bow or crossbow",
			"Only show the counter while you hold a bow or crossbow.", true));

		public Arrows() {
			super("arrows", "Arrow Counter", "Arrows (normal, spectral and tipped) in your inventory.", false, HudAnchor.MIDDLE_RIGHT, -4, 64);
		}

		@Override
		public boolean hasContent() {
			if (!onlyWithBow.enabled() || MC.player == null) {
				return true;
			}
			ItemStack main = MC.player.getMainHandItem();
			ItemStack off = MC.player.getOffhandItem();
			return main.is(Items.BOW) || main.is(Items.CROSSBOW) || off.is(Items.BOW) || off.is(Items.CROSSBOW);
		}

		@Override
		protected String label() {
			return "Arrows";
		}

		@Override
		protected String value(boolean preview) {
			int count = count(stack -> stack.is(ItemTags.ARROWS));
			return Integer.toString(preview && count == 0 ? 64 : count);
		}
	}

	/**
	 * Warnings in the middle of the screen: low health, armour or the held item about to break, and helpful
	 * effects running out.
	 */
	public static final class Warnings extends HudModule {
		public final NumberSetting health = add(new NumberSetting("health", "Low health below", "Warn at or below this many hearts (0 = off).",
			3, 0, 10, 0.5, " ♥"));
		public final NumberSetting durability = add(new NumberSetting("durability", "Breaking below", "Warn when armour or the held item has "
			+ "less durability left than this (0 = off).", 10, 0, 50, 1, "%"));
		public final NumberSetting effects = add(new NumberSetting("effects", "Effects ending within", "Warn when a helpful effect ends within "
			+ "this many seconds (0 = off).", 5, 0, 30, 1, " s"));
		public final BooleanSetting blink = add(new BooleanSetting("blink", "Blink", "Let warnings blink.", true));

		private List<String> lines = List.of();
		private long computedAt;

		public Warnings() {
			super("warnings", "Warnings", "Low health, breaking armour or tools and effects that are about to end.", false,
				HudAnchor.CENTER, 0, -40);
		}

		private List<String> lines(boolean preview) {
			if (preview) {
				return List.of("LOW HEALTH", "Helmet breaking: 6%", "Speed ends in 4s");
			}
			long now = System.currentTimeMillis();
			if (now - computedAt < 100) {
				return lines;
			}
			computedAt = now;
			List<String> result = new ArrayList<>();
			if (MC.player != null) {
				double hearts = MC.player.getHealth() / 2.0;
				if (health.get() > 0 && hearts <= health.get() && MC.player.isAlive()) {
					result.add("LOW HEALTH");
				}
				if (durability.get() > 0) {
					for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
						EquipmentSlot.MAINHAND}) {
						ItemStack stack = MC.player.getItemBySlot(slot);
						if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
							int percent = Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
							if (percent < durability.get()) {
								result.add(slotName(slot) + " breaking: " + percent + "%");
							}
						}
					}
				}
				if (effects.get() > 0) {
					for (MobEffectInstance effect : MC.player.getActiveEffects()) {
						int seconds = effect.getDuration() / 20;
						if (effect.getEffect().value().isBeneficial() && !effect.isInfiniteDuration() && seconds <= effects.get()) {
							result.add(effect.getEffect().value().getDisplayName().getString() + " ends in " + seconds + "s");
						}
					}
				}
			}
			lines = result;
			return result;
		}

		private static String slotName(EquipmentSlot slot) {
			return switch (slot) {
				case HEAD -> "Helmet";
				case CHEST -> "Chestplate";
				case LEGS -> "Leggings";
				case FEET -> "Boots";
				default -> "Held item";
			};
		}

		@Override
		public boolean hasContent() {
			return !lines(false).isEmpty();
		}

		@Override
		public int contentWidth(boolean preview) {
			int width = 0;
			for (String line : lines(preview)) {
				width = Math.max(width, textWidth(line));
			}
			return Math.max(width, 20);
		}

		@Override
		public int contentHeight(boolean preview) {
			return Math.max(1, lines(preview).size()) * 10 - 2;
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			boolean visible = preview || !blink.enabled() || (System.currentTimeMillis() / 400) % 2 == 0;
			int width = contentWidth(preview);
			List<String> current = lines(preview);
			for (int i = 0; i < current.size(); i++) {
				String line = current.get(i);
				int color = i == 0 && line.equals("LOW HEALTH") ? 0xFFFF5C7A : accent();
				if (!visible && line.equals("LOW HEALTH")) {
					color = fade(color, 0.35f);
				}
				drawText(g, line, x + (width - textWidth(line)) / 2, y + i * 10, color);
			}
		}
	}

	private interface StackTest {
		boolean test(ItemStack stack);
	}

	/** Counts matching items in the whole inventory, including armour and the off hand. */
	static int count(StackTest test) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && test.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
