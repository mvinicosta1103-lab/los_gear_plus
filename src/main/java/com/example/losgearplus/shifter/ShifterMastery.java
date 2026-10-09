package com.example.losgearplus.shifter;

import daot.ShifterTitan;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Shifter Mastery System: levels 0..9 that decide how many times a shifter can transform, how much a
 * transformation costs in stamina and how fast stamina drains while in titan form.
 *
 * <pre>
 *  Level   Transformations   Transform cost   Titan-form drain (time transformed)
 *  0       1                 100%             200% (60s)
 *  1       1                 100%             170% (~70s)
 *  2       1                 100%             145% (~83s)
 *  3       3                 100%             125% (~96s)
 *  4       3                 100%             110% (~109s)
 *  5       3                 100%             100% (120s)
 *  6       6                  75%              80% (150s)
 *  7       6                  65%              65% (~185s)
 *  8       6                  55%              50% (240s)
 *  9       unlimited           0%               0%   (full control: no stamina spent at all)
 * </pre>
 *
 * <b>Cart Titan:</b> the Cart shifter (tag {@code cart_shifter}) ignores this table: it always uses the rules of
 * level 9 (unlimited transformations, no stamina cost or drain), whatever its real level. Its level still grows
 * and still gives the HP / strength / damage reduction bonuses ({@link ShifterMasteryStats}).
 * Use {@link #ruleLevel(UUID)} (or the UUID overloads) wherever a RULE is applied, and {@link #getLevel(UUID)}
 * only to show or store the real level.
 *
 * <b>Progression:</b> mastery XP is earned every time the shifter transforms
 * ({@link #XP_PER_TRANSFORMATION}) and for every second spent in titan form ({@link #XP_PER_SECOND_TRANSFORMED}).
 * The level follows the XP through {@link #XP_TO_REACH}.
 *
 * <b>Cooldown:</b> used transformations go back to zero {@link #RESET_TICKS} ticks after the first transformation
 * of the window (or instantly with the cooldown command). Tune every table below to balance.
 */
public final class ShifterMastery {
	private ShifterMastery() {}

	public static final int MAX_LEVEL = 9;
	public static final int UNLIMITED = Integer.MAX_VALUE;

	/** Time until used transformations reset (12000 ticks = 10 minutes). */
	public static final long RESET_TICKS = 12000L;

	/** Seconds a full stamina bar lasts in titan form at a drain multiplier of 1.0 (level 5). */
	public static final float BASE_TITAN_SECONDS = 120f;

	/** Mastery XP granted each time a transformation starts. */
	public static final int XP_PER_TRANSFORMATION = 50;
	/** Mastery XP granted for each full second spent in titan form. */
	public static final int XP_PER_SECOND_TRANSFORMED = 1;

	/** Total XP needed to REACH each level (index = level). */
	public static final int[] XP_TO_REACH = { 0, 100, 300, 600, 1000, 1600, 2400, 3400, 4800, 7000 };

	//                                          level: 0  1  2  3  4  5  6  7  8  9
	private static final int[] MAX_TRANSFORMS = { 1, 1, 1, 3, 3, 3, 6, 6, 6, UNLIMITED };
	private static final float[] SHIFT_COST = { 1f, 1f, 1f, 1f, 1f, 1f, 0.75f, 0.65f, 0.55f, 0f };
	/**
	 * Titan-form drain, relative to the base rate (100% = {@link #BASE_TITAN_SECONDS} seconds from full stamina to
	 * empty). Above 1 = drains faster (low level), below 1 = lasts longer, 0 = infinite (level 9).
	 */
	private static final float[] DRAIN = { 2f, 1.7f, 1.45f, 1.25f, 1.1f, 1f, 0.8f, 0.65f, 0.5f, 0f };

	private static MinecraftServer server;
	private static ShifterMasteryData data;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			data = s.overworld().getDataStorage().computeIfAbsent(ShifterMasteryData.factory(), ShifterMasteryData.NAME);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			data = null;
		});

		// Time spent transformed: once per second, every player riding a titan earns mastery XP.
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (data == null || s.getTickCount() % 20 != 0) return;
			for (ServerPlayer p : s.getPlayerList().getPlayers()) {
				if (p.getVehicle() instanceof ShifterTitan
						&& !(p.getVehicle() instanceof com.example.losgearplus.partial.PartialShifterTitanEntity)) {
					addXp(p, XP_PER_SECOND_TRANSFORMED); // partial shifting does not count as transformed time
				}
			}
		});
	}

	// ---- level & XP ------------------------------------------------------------------------------------------

	public static int getLevel(UUID id) {
		if (data == null) return 0;
		ShifterMasteryData.Entry e = data.peek(id);
		return e == null ? 0 : e.level;
	}

	public static int getXp(UUID id) {
		if (data == null) return 0;
		ShifterMasteryData.Entry e = data.peek(id);
		return e == null ? 0 : e.xp;
	}

	/** XP earned inside the current level (0 at the start of the level). */
	public static int xpIntoLevel(UUID id) {
		int level = getLevel(id);
		return Math.max(0, getXp(id) - XP_TO_REACH[level]);
	}

	/** XP needed to go from {@code level} to the next one; -1 at the maximum level. */
	public static int xpForNextLevel(int level) {
		level = clamp(level);
		return level >= MAX_LEVEL ? -1 : XP_TO_REACH[level + 1] - XP_TO_REACH[level];
	}

	/** Sets the level directly; XP is set to the start of that level. */
	public static void setLevel(UUID id, int level) {
		if (data == null) return;
		ShifterMasteryData.Entry e = data.get(id);
		e.level = clamp(level);
		e.xp = XP_TO_REACH[e.level];
		data.setDirty();
	}

	public static void maximize(UUID id) {
		setLevel(id, MAX_LEVEL);
	}

	/** Back to level 0, no XP and no transformations used. */
	public static void reset(UUID id) {
		if (data == null) return;
		ShifterMasteryData.Entry e = data.get(id);
		e.level = 0;
		e.xp = 0;
		e.uses = 0;
		e.windowStart = 0;
		data.setDirty();
	}

	/** Clears only the cooldown: used transformations go back to zero. */
	public static void resetCooldown(UUID id) {
		if (data == null) return;
		ShifterMasteryData.Entry e = data.get(id);
		e.uses = 0;
		e.windowStart = 0;
		data.setDirty();
	}

	/** Grants mastery XP and levels the player up when a threshold is crossed. */
	public static void addXp(ServerPlayer player, int amount) {
		if (data == null || amount <= 0) return;
		ShifterMasteryData.Entry e = data.get(player.getUUID());
		if (e.level >= MAX_LEVEL) return;

		e.xp = Math.min(XP_TO_REACH[MAX_LEVEL], e.xp + amount);
		int newLevel = e.level;
		while (newLevel < MAX_LEVEL && e.xp >= XP_TO_REACH[newLevel + 1]) newLevel++;
		data.setDirty();

		if (newLevel > e.level) {
			e.level = newLevel;
			player.sendSystemMessage(Component.literal("Shifter Mastery increased to level " + newLevel + "!"));
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
			ShifterMasteryStats.apply(player); // +1 extra heart for the new level, right away
			ShifterMasterySync.send(player, true);
		}
	}

	// ---- Cart Titan override -----------------------------------------------------------------------------------

	/** True if the player is online and is the Cart Titan shifter (tag {@code cart_shifter}). */
	public static boolean isCartShifter(UUID id) {
		if (server == null) return false;
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		return p != null && p.getTags().contains(ShifterTypes.CART_TAG);
	}

	/** Level used for the RULES (transformations, cost, drain): the Cart Titan always counts as the maximum. */
	public static int ruleLevel(UUID id) {
		return isCartShifter(id) ? MAX_LEVEL : getLevel(id);
	}

	// ---- rules per level -------------------------------------------------------------------------------------

	public static int maxTransforms(int level) {
		return MAX_TRANSFORMS[clamp(level)];
	}

	public static float shiftCostMultiplier(int level) {
		return SHIFT_COST[clamp(level)];
	}

	public static float drainMultiplier(int level) {
		return DRAIN[clamp(level)];
	}

	/** Same rules, but by player: already applies the Cart Titan override. */
	public static int maxTransforms(UUID id) {
		return maxTransforms(ruleLevel(id));
	}

	public static float shiftCostMultiplier(UUID id) {
		return shiftCostMultiplier(ruleLevel(id));
	}

	public static float drainMultiplier(UUID id) {
		return drainMultiplier(ruleLevel(id));
	}

	public static boolean isMaster(UUID id) {
		return getLevel(id) >= MAX_LEVEL;
	}

	// ---- transformation count --------------------------------------------------------------------------------

	private static long now() {
		return server == null ? 0L : server.overworld().getGameTime();
	}

	private static ShifterMasteryData.Entry fresh(UUID id) {
		ShifterMasteryData.Entry e = data.get(id);
		if (e.uses > 0 && now() - e.windowStart >= RESET_TICKS) {
			e.uses = 0;
			data.setDirty();
		}
		return e;
	}

	/** Transformations already used in the current window. */
	public static int usedTransforms(UUID id) {
		return data == null ? 0 : fresh(id).uses;
	}

	/** Can the player start a transformation right now? The Cart Titan always can. */
	public static boolean canShift(ServerPlayer player) {
		if (data == null || isCartShifter(player.getUUID())) return true;
		ShifterMasteryData.Entry e = fresh(player.getUUID());
		return e.uses < maxTransforms(e.level);
	}

	/** Called when a transformation really started. The Cart Titan does not spend transformations. */
	public static void registerShift(ServerPlayer player) {
		if (data == null || isCartShifter(player.getUUID())) return;
		ShifterMasteryData.Entry e = fresh(player.getUUID());
		if (e.uses == 0) e.windowStart = now();
		e.uses++;
		data.setDirty();
	}

	/** Ticks left until used transformations reset (0 if no countdown is running). */
	public static long ticksUntilReset(UUID id) {
		if (data == null) return 0L;
		ShifterMasteryData.Entry e = fresh(id);
		return e.uses == 0 ? 0L : Math.max(0L, RESET_TICKS - (now() - e.windowStart));
	}

	private static int clamp(int level) {
		return Math.max(0, Math.min(MAX_LEVEL, level));
	}
}