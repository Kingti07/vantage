package dev.vantage.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side statistics derived only from information the client already has (own inputs, entity hurt
 * animations and synced health). Nothing here changes gameplay or sends packets.
 * <ul>
 *   <li>CPS: mouse presses in the last second</li>
 *   <li>Reach: distance from the eyes to the target's hitbox at the moment of the last landed hit</li>
 *   <li>Combo: landed hits in a row without being hurt (resets after 3 s without a hit)</li>
 *   <li>Hits / kills: landed hits and deaths of entities you hit in the last 5 s, per session</li>
 *   <li>W-taps: landed hits preceded by a sprint reset since the previous hit</li>
 * </ul>
 */
public final class CombatTracker {
	private static final long COMBO_TIMEOUT_MS = 3000;
	private static final long KILL_WINDOW_MS = 5000;

	private final Deque<Long> leftClicks = new ArrayDeque<>();
	private final Deque<Long> rightClicks = new ArrayDeque<>();

	private record Pending(LivingEntity target, long time, double reach, boolean wtap) {
	}

	private Pending pending;
	private final Map<Integer, Long> recentlyHit = new HashMap<>();
	private LivingEntity target;
	private long targetTime;
	private double lastReach = -1;
	private long lastReachTime;
	private int combo;
	private long lastLandedHit;
	private int hits;
	private int kills;
	private int wtaps;
	private int lastOwnHurtTime;
	private boolean wasSprinting;
	private boolean sprintStoppedSinceHit;
	private boolean sprintResetSinceHit;
	private Vec3 lastPosition;
	private double speed;
	private int deaths;
	private boolean wasDead;
	private long sessionStart = System.currentTimeMillis();
	/** Where and in which dimension you died last, or null. */
	private DeathPoint lastDeath;

	/** A death position, recorded on the client from your own position. */
	public record DeathPoint(Vec3 position, String dimension, long time) {
	}

	public void onClick(int button) {
		long now = System.currentTimeMillis();
		if (button == 0) {
			leftClicks.addLast(now);
		} else if (button == 1) {
			rightClicks.addLast(now);
		}
	}

	public int leftCps() {
		return count(leftClicks);
	}

	public int rightCps() {
		return count(rightClicks);
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
			clicks.pollFirst();
		}
		return clicks.size();
	}

	/** Called right before the client sends an attack (from {@code MultiPlayerGameMode.attack}). */
	public void onAttack(LocalPlayer player, Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		double reach = Math.sqrt(entity.getBoundingBox().distanceToSqr(player.getEyePosition()));
		pending = new Pending(living, System.currentTimeMillis(), reach, sprintResetSinceHit);
		target = living;
		targetTime = System.currentTimeMillis();
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		long now = System.currentTimeMillis();

		// Speed in blocks per second (horizontal).
		Vec3 position = player.position();
		if (lastPosition != null) {
			double dx = position.x - lastPosition.x;
			double dz = position.z - lastPosition.z;
			speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
		}
		lastPosition = position;

		// Sprint resets for W-tap detection.
		boolean sprinting = player.isSprinting();
		if (wasSprinting && !sprinting) {
			sprintStoppedSinceHit = true;
		} else if (!wasSprinting && sprinting && sprintStoppedSinceHit) {
			sprintResetSinceHit = true;
		}
		wasSprinting = sprinting;

		// Deaths: the moment your own health reaches zero.
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			deaths++;
			lastDeath = new DeathPoint(player.position(), player.level().dimension().identifier().getPath(), now);
		}
		wasDead = dead;

		// Own damage breaks the combo.
		if (player.hurtTime > lastOwnHurtTime && player.hurtTime > 0) {
			combo = 0;
		}
		lastOwnHurtTime = player.hurtTime;

		// Confirm landed hits via the target's hurt animation.
		if (pending != null) {
			if (now - pending.time() > 600 || pending.target().isRemoved()) {
				pending = null;
			} else if (pending.target().hurtTime >= 8) {
				hits++;
				combo = now - lastLandedHit > COMBO_TIMEOUT_MS ? 1 : combo + 1;
				lastLandedHit = now;
				lastReach = pending.reach();
				lastReachTime = now;
				if (pending.wtap()) {
					wtaps++;
				}
				sprintStoppedSinceHit = false;
				sprintResetSinceHit = false;
				recentlyHit.put(pending.target().getId(), now);
				pending = null;
			}
		}
		if (combo > 0 && now - lastLandedHit > COMBO_TIMEOUT_MS) {
			combo = 0;
		}

		// Kills: entities we hit recently that are now dying.
		if (mc.level != null) {
			Iterator<Map.Entry<Integer, Long>> iterator = recentlyHit.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<Integer, Long> entry = iterator.next();
				Entity entity = mc.level.getEntity(entry.getKey());
				if (now - entry.getValue() > KILL_WINDOW_MS) {
					iterator.remove();
				} else if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
					kills++;
					iterator.remove();
				}
			}
		}
		if (target != null && (target.isRemoved() || now - targetTime > 10_000 || target.distanceTo(player) > 24)) {
			target = null;
		}
	}

	public void resetSession() {
		leftClicks.clear();
		rightClicks.clear();
		pending = null;
		recentlyHit.clear();
		target = null;
		lastReach = -1;
		combo = 0;
		hits = 0;
		kills = 0;
		wtaps = 0;
		lastPosition = null;
		speed = 0;
		deaths = 0;
		wasDead = false;
		lastDeath = null;
		sessionStart = System.currentTimeMillis();
	}

	public int deaths() {
		return deaths;
	}

	public DeathPoint lastDeath() {
		return lastDeath;
	}

	/** Milliseconds since you joined the current world or server. */
	public long sessionMillis() {
		return System.currentTimeMillis() - sessionStart;
	}

	public LivingEntity target() {
		return target;
	}

	/** Reach of the last landed hit, or -1 if none in the last few seconds. */
	public double lastReach(long maxAgeMs) {
		return System.currentTimeMillis() - lastReachTime <= maxAgeMs ? lastReach : -1;
	}

	public int combo() {
		return combo;
	}

	public int hits() {
		return hits;
	}

	public int kills() {
		return kills;
	}

	public int wtaps() {
		return wtaps;
	}

	public double speed() {
		return speed;
	}
}
