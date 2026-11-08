package dev.vantage.client.module.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vantage.client.module.Category;
import dev.vantage.client.module.Module;
import dev.vantage.client.setting.BooleanSetting;
import dev.vantage.client.setting.ColorSetting;
import dev.vantage.client.setting.NumberSetting;
import dev.vantage.client.util.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Floating numbers showing damage dealt to (and optionally healing of) nearby entities. Values come from the
 * health the server already syncs to the client.
 */
public final class DamageIndicatorsModule extends Module {
	private static final int LIFETIME_TICKS = 25;

	private record Indicator(Vec3 position, float amount, int bornTick) {
	}

	public final NumberSetting range = add(new NumberSetting("range", "Range", "Track entities within this distance.", 24, 8, 64, 4, " blocks"));
	public final ColorSetting damageColor = add(new ColorSetting("damageColor", "Damage colour", "Colour of damage numbers.", 0xFFFF5C7A));
	public final BooleanSetting healing = add(new BooleanSetting("healing", "Show healing", "Also show green numbers when entities heal.", false));
	public final ColorSetting healColor = add(new ColorSetting("healColor", "Healing colour", "Colour of healing numbers.", 0xFF3DDC97));
	public final NumberSetting size = add(new NumberSetting("size", "Size", "Text size.", 1.0, 0.5, 2.5, 0.1, "x"));
	public final BooleanSetting throughWalls = add(new BooleanSetting("throughWalls", "Through walls", "Draw numbers on top of blocks.", true));
	public final BooleanSetting hearts = add(new BooleanSetting("hearts", "Show as hearts", "Divide by two and add a heart symbol.", true));

	private final Map<Integer, Float> lastHealth = new HashMap<>();
	private final List<Indicator> indicators = new ArrayList<>();
	private final Random random = new Random();
	private int tick;

	public DamageIndicatorsModule() {
		super("damageindicators", "Damage Indicators", "Floating numbers for damage dealt to entities.", Category.PVP, false);
		healColor.visibleWhen(healing::enabled);
	}

	@Override
	public void onTick() {
		tick++;
		if (MC.level == null || MC.player == null) {
			lastHealth.clear();
			indicators.clear();
			return;
		}
		double maxDistance = range.get() * range.get();
		Map<Integer, Float> seen = new HashMap<>();
		for (Entity entity : MC.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || entity == MC.player || entity.distanceToSqr(MC.player) > maxDistance) {
				continue;
			}
			float health = living.getHealth();
			Float previous = lastHealth.get(entity.getId());
			if (previous != null) {
				float delta = previous - health;
				if (delta > 0.05f || (healing.enabled() && delta < -0.05f)) {
					Vec3 pos = new Vec3(entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
						entity.getY() + entity.getBbHeight() * (0.6 + random.nextDouble() * 0.4),
						entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth());
					indicators.add(new Indicator(pos, delta, tick));
				}
			}
			seen.put(entity.getId(), health);
		}
		lastHealth.clear();
		lastHealth.putAll(seen);
		indicators.removeIf(i -> tick - i.bornTick() > LIFETIME_TICKS);
		if (indicators.size() > 200) {
			indicators.subList(0, indicators.size() - 200).clear();
		}
	}

	@Override
	protected void onDisable() {
		indicators.clear();
		lastHealth.clear();
	}

	/** Draws the indicators as camera-facing text (called from the world render event). */
	public void render(WorldRenderContext context) {
		if (!isEnabled() || indicators.isEmpty()) {
			return;
		}
		Vec3 camera = MC.gameRenderer.getMainCamera().position();
		var rotation = MC.gameRenderer.getMainCamera().rotation();
		PoseStack poses = context.matrices();
		MultiBufferSource consumers = context.consumers();
		Font font = MC.font;
		float partial = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Iterator<Indicator> iterator = indicators.iterator();
		while (iterator.hasNext()) {
			Indicator indicator = iterator.next();
			float age = (tick - indicator.bornTick()) + partial;
			float life = age / LIFETIME_TICKS;
			if (life >= 1) {
				continue;
			}
			float amount = Math.abs(indicator.amount());
			String text = hearts.enabled() ? String.format(Locale.ROOT, "%.1f❤", amount / 2f) : String.format(Locale.ROOT, "%.1f", amount);
			boolean heal = indicator.amount() < 0;
			int color = Colors.fade(heal ? healColor.color() : damageColor.color(), 1 - life * life);
			poses.pushPose();
			poses.translate(indicator.position().x - camera.x, indicator.position().y - camera.y + age * 0.02, indicator.position().z - camera.z);
			poses.mulPose(rotation);
			float scale = 0.025f * size.floatValue() * (1 + 0.3f * Math.max(0, 1 - age / 4f));
			poses.scale(scale, -scale, scale);
			float x = -font.width(text) / 2f;
			font.drawInBatch(text, x, 0, color, false, poses.last().pose(), consumers,
				throughWalls.enabled() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
			poses.popPose();
		}
	}
}
