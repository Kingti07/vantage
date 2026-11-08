package dev.vantage.client.module.hud;

import dev.vantage.client.VantageClient;
import dev.vantage.client.hud.HudAnchor;
import dev.vantage.client.hud.HudModule;
import dev.vantage.client.render.Render2D;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.EnumSetting;
import dev.vantage.client.setting.NumberSetting;
import dev.vantage.client.util.Anim;
import dev.vantage.client.util.Colors;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Combat-oriented graphical elements: keystrokes, armour, potion effects and the target panel. */
public final class CombatHuds {
	private CombatHuds() {
	}

	public static final class Keystrokes extends HudModule {
		private static final int KEY = 22;
		private static final int GAP = 2;
		private final BooleanSetting mouse = add(new BooleanSetting("mouse", "Mouse buttons", "Show LMB/RMB with CPS.", true));
		private final BooleanSetting space = add(new BooleanSetting("space", "Space bar", "Show the jump key.", true));
		private final ColorSetting pressedColor = add(new ColorSetting("pressedColor", "Pressed colour", "Key background while pressed.", 0xD9FFFFFF));
		private final ColorSetting keyColor = add(new ColorSetting("keyColor", "Key colour", "Key background while released.", 0x8C0B0D12, false));
		private final ColorSetting pressedText = add(new ColorSetting("pressedText", "Pressed text", "Text colour while pressed.", 0xFF0B0D12));
		private final Anim[] anims = new Anim[7];

		public Keystrokes() {
			super("keystrokes", "Keystrokes", "Shows movement keys, mouse buttons and jump as you press them.", true, HudAnchor.BOTTOM_RIGHT, -4, -4);
			background.overrideDefault(false);
			padding.overrideDefault(0d);
			for (int i = 0; i < anims.length; i++) {
				anims[i] = new Anim(0, 18);
			}
		}

		@Override
		public int contentWidth(boolean preview) {
			return KEY * 3 + GAP * 2;
		}

		@Override
		public int contentHeight(boolean preview) {
			int h = KEY * 2 + GAP;
			if (mouse.enabled()) {
				h += GAP + KEY;
			}
			if (space.enabled()) {
				h += GAP + 10;
			}
			return h;
		}

		private void key(GuiGraphics g, int index, int x, int y, int w, int h, String label, boolean down) {
			float t = anims[index].target(down ? 1 : 0).get();
			int bg = Colors.lerp(keyColor.color(), pressedColor.color(chromaOffset()), t);
			Render2D.rounded(g, x, y, w, h, cornerRadius.intValue(), fade(bg, opacity.floatValue()));
			int fg = Colors.lerp(text(), pressedText.color(), t);
			if (label.contains("\n")) {
				String[] parts = label.split("\n");
				drawText(g, parts[0], x + (w - textWidth(parts[0])) / 2, y + h / 2 - 8, fg);
				drawSmall(g, parts[1], x + w / 2f, y + h / 2f + 2, fg);
			} else {
				drawText(g, label, x + (w - textWidth(label)) / 2, y + (h - 8) / 2, fg);
			}
		}

		private void drawSmall(GuiGraphics g, String text, float cx, float y, int color) {
			g.pose().pushMatrix();
			g.pose().translate(cx, y);
			g.pose().scale(0.6f, 0.6f);
			drawText(g, text, -textWidth(text) / 2, 0, color);
			g.pose().popMatrix();
		}

		private static String name(KeyMapping mapping) {
			String name = mapping.getTranslatedKeyMessage().getString();
			return name.length() > 3 ? name.substring(0, 3) : name;
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			var options = MC.options;
			key(g, 0, x + KEY + GAP, y, KEY, KEY, name(options.keyUp), options.keyUp.isDown());
			key(g, 1, x, y + KEY + GAP, KEY, KEY, name(options.keyLeft), options.keyLeft.isDown());
			key(g, 2, x + KEY + GAP, y + KEY + GAP, KEY, KEY, name(options.keyDown), options.keyDown.isDown());
			key(g, 3, x + (KEY + GAP) * 2, y + KEY + GAP, KEY, KEY, name(options.keyRight), options.keyRight.isDown());
			int row = y + (KEY + GAP) * 2;
			if (mouse.enabled()) {
				int half = (KEY * 3 + GAP * 2 - GAP) / 2;
				var combat = VantageClient.get().combat();
				key(g, 4, x, row, half, KEY, "LMB\n" + combat.leftCps() + " CPS", options.keyAttack.isDown());
				key(g, 5, x + half + GAP, row, half, KEY, "RMB\n" + combat.rightCps() + " CPS", options.keyUse.isDown());
				row += KEY + GAP;
			}
			if (space.enabled()) {
				float t = anims[6].target(options.keyJump.isDown() ? 1 : 0).get();
				int bg = Colors.lerp(keyColor.color(), pressedColor.color(chromaOffset()), t);
				int w = KEY * 3 + GAP * 2;
				Render2D.rounded(g, x, row, w, 10, cornerRadius.intValue(), fade(bg, opacity.floatValue()));
				int bar = Colors.lerp(text(), pressedText.color(), t);
				g.fill(x + w / 2 - 12, row + 4, x + w / 2 + 12, row + 6, bar);
			}
		}
	}

	public static final class ArmorStatus extends HudModule {
		public enum Layout {VERTICAL, HORIZONTAL}

		public enum Display {NUMBER, PERCENT, NONE}

		private final EnumSetting<Layout> layout = add(new EnumSetting<>("layout", "Layout", "Stack items vertically or horizontally.", Layout.VERTICAL));
		private final EnumSetting<Display> display = add(new EnumSetting<>("display", "Durability", "How durability is shown.", Display.NUMBER));
		private final BooleanSetting mainHand = add(new BooleanSetting("mainHand", "Held item", "Include the item in your main hand.", true));

		public ArmorStatus() {
			super("armor", "Armor Status", "Your armour pieces and their durability.", true, HudAnchor.MIDDLE_RIGHT, -4, 0);
			background.overrideDefault(false);
		}

		private List<ItemStack> items(boolean preview) {
			List<ItemStack> items = new ArrayList<>();
			if (MC.player != null) {
				for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					ItemStack stack = MC.player.getItemBySlot(slot);
					if (!stack.isEmpty()) {
						items.add(stack);
					}
				}
				if (mainHand.enabled() && !MC.player.getMainHandItem().isEmpty()) {
					items.add(MC.player.getMainHandItem());
				}
			}
			if (items.isEmpty() && preview) {
				items.add(new ItemStack(Items.DIAMOND_HELMET));
				items.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
				items.add(new ItemStack(Items.DIAMOND_LEGGINGS));
				items.add(new ItemStack(Items.DIAMOND_BOOTS));
			}
			return items;
		}

		@Override
		public boolean hasContent() {
			return !items(false).isEmpty();
		}

		private String label(ItemStack stack) {
			if (display.get() == Display.NONE) {
				return "";
			}
			if (!stack.isDamageableItem()) {
				return stack.getCount() > 1 ? Integer.toString(stack.getCount()) : "";
			}
			int max = stack.getMaxDamage();
			int left = max - stack.getDamageValue();
			return display.get() == Display.PERCENT ? Math.round(left * 100f / max) + "%" : Integer.toString(left);
		}

		private int labelWidth(boolean preview) {
			int width = 0;
			for (ItemStack stack : items(preview)) {
				width = Math.max(width, textWidth(label(stack)));
			}
			return width;
		}

		@Override
		public int contentWidth(boolean preview) {
			int count = items(preview).size();
			int lw = labelWidth(preview);
			return layout.get() == Layout.VERTICAL ? 16 + (lw > 0 ? 3 + lw : 0) : Math.max(1, count) * (16 + (lw > 0 ? 3 + lw : 0) + 4) - 4;
		}

		@Override
		public int contentHeight(boolean preview) {
			int count = Math.max(1, items(preview).size());
			return layout.get() == Layout.VERTICAL ? count * 17 - 1 : 16;
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			int lw = labelWidth(preview);
			int step = layout.get() == Layout.VERTICAL ? 17 : 16 + (lw > 0 ? 3 + lw : 0) + 4;
			int i = 0;
			for (ItemStack stack : items(preview)) {
				int ix = layout.get() == Layout.VERTICAL ? x : x + i * step;
				int iy = layout.get() == Layout.VERTICAL ? y + i * step : y;
				g.renderItem(stack, ix, iy);
				String label = label(stack);
				if (!label.isEmpty()) {
					int color = text();
					if (stack.isDamageableItem()) {
						float fraction = 1 - stack.getDamageValue() / (float) stack.getMaxDamage();
						color = fade(Colors.lerp(0xFFFF5C7A, textColor.color(), Math.min(1, fraction * 1.6f)), opacity.floatValue());
					}
					drawText(g, label, ix + 19, iy + 4, color);
				}
				i++;
			}
		}
	}

	public static final class PotionEffects extends HudModule {
		private final BooleanSetting icons = add(new BooleanSetting("icons", "Icons", "Show effect icons.", true));
		private final BooleanSetting showLevel = add(new BooleanSetting("level", "Show level", "Append the effect level (II, III, …).", true));
		private final NumberSetting blinkSeconds = add(new NumberSetting("blink", "Blink when ending", "Blink during the last seconds of an effect.", 5, 0, 20, 1, "s"));

		public PotionEffects() {
			super("potions", "Potion Effects", "Active potion effects and their remaining time.", true, HudAnchor.MIDDLE_LEFT, 4, 34);
			background.overrideDefault(false);
		}

		private List<MobEffectInstance> effects(boolean preview) {
			List<MobEffectInstance> list = new ArrayList<>();
			if (MC.player != null) {
				list.addAll(MC.player.getActiveEffects());
			}
			if (list.isEmpty() && preview) {
				list.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 20 * 95, 1));
				list.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 160, 0));
			}
			list.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
			return list;
		}

		@Override
		public boolean hasContent() {
			return !effects(false).isEmpty();
		}

		private String name(MobEffectInstance effect) {
			String name = effect.getEffect().value().getDisplayName().getString();
			if (showLevel.enabled() && effect.getAmplifier() > 0) {
				name += " " + roman(effect.getAmplifier() + 1);
			}
			return name;
		}

		private static String duration(MobEffectInstance effect) {
			if (effect.isInfiniteDuration()) {
				return "∞";
			}
			int seconds = effect.getDuration() / 20;
			return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
		}

		private static String roman(int value) {
			String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
			return value < numerals.length ? numerals[value] : Integer.toString(value);
		}

		@Override
		public int contentWidth(boolean preview) {
			int width = 0;
			for (MobEffectInstance effect : effects(preview)) {
				width = Math.max(width, Math.max(textWidth(name(effect)), textWidth(duration(effect))));
			}
			return width + (icons.enabled() ? 22 : 0);
		}

		@Override
		public int contentHeight(boolean preview) {
			return Math.max(1, effects(preview).size()) * 22 - 2;
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			int row = 0;
			for (MobEffectInstance effect : effects(preview)) {
				int top = y + row * 22;
				boolean blink = !effect.isInfiniteDuration() && effect.getDuration() < blinkSeconds.get() * 20
					&& (System.currentTimeMillis() / 300) % 2 == 0;
				float alpha = blink ? 0.35f : 1f;
				int textX = x;
				if (icons.enabled()) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Gui.getMobEffectSprite(effect.getEffect()), x, top, 18, 18,
						Colors.withAlpha(0xFFFFFF, Math.round(255 * alpha * opacity.floatValue())));
					textX += 22;
				}
				drawText(g, name(effect), textX, top, Colors.fade(text(), alpha));
				drawText(g, duration(effect), textX, top + 10, Colors.fade(accent(), alpha));
				row++;
			}
		}
	}

	/** Information about the entity you last hit: name, health bar, distance and armour. */
	public static final class Target extends HudModule {
		private final BooleanSetting head = add(new BooleanSetting("head", "Player head", "Show the player's face.", true));
		private final ColorSetting healthColor = add(new ColorSetting("healthColor", "Health bar", "Colour of the health bar.", 0xFF3DDC97));
		private final Anim healthAnim = new Anim(1, 10);

		public Target() {
			super("target", "Target HUD", "Health and distance of the entity you are fighting.", true, HudAnchor.CENTER, 20, 20);
		}

		@Override
		public boolean hasContent() {
			return VantageClient.get().combat().target() != null;
		}

		@Override
		public int contentWidth(boolean preview) {
			return 128;
		}

		@Override
		public int contentHeight(boolean preview) {
			return 30;
		}

		@Override
		protected void renderContent(GuiGraphics g, int x, int y, boolean preview) {
			LivingEntity target = VantageClient.get().combat().target();
			String name = target != null ? target.getDisplayName().getString() : "Opponent";
			float health = target != null ? target.getHealth() : 14;
			float max = target != null ? Math.max(1, target.getMaxHealth()) : 20;
			double distance = target != null && MC.player != null ? target.distanceTo(MC.player) : 3.1;
			int armor = target != null ? target.getArmorValue() : 20;

			int textX = x;
			if (head.enabled()) {
				if (target instanceof AbstractClientPlayer player) {
					PlayerFaceRenderer.draw(g, player.getSkin(), x, y, 30);
				} else if (target == null && MC.player != null) {
					PlayerFaceRenderer.draw(g, MC.player.getSkin(), x, y, 30);
				} else {
					Render2D.rounded(g, x, y, 30, 30, 4, 0x40FFFFFF);
					String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
					drawText(g, initial, x + 15 - textWidth(initial) / 2, y + 11, text());
				}
				textX += 34;
			}
			int width = 128 - (textX - x);
			drawText(g, dev.vantage.client.render.Text.ellipsize(name, width), textX, y, text());
			float fraction = healthAnim.target(Math.max(0, Math.min(1, health / max))).get();
			Render2D.rounded(g, textX, y + 11, width, 5, 2, 0x50000000);
			int barColor = Colors.lerp(0xFFFF5C7A, healthColor.color(chromaOffset()), fraction);
			Render2D.rounded(g, textX, y + 11, Math.max(2, Math.round(width * fraction)), 5, 2, fade(barColor, opacity.floatValue()));
			String info = String.format(Locale.ROOT, "%.1f ❤  %.1fm  %d ⛨", health / 2f, distance, armor);
			drawText(g, info, textX, y + 20, accent());
			if (target instanceof Player player && player.isBlocking()) {
				drawText(g, "Blocking", x + 128 - textWidth("Blocking"), y, 0xFFFFB547);
			}
		}
	}
}
