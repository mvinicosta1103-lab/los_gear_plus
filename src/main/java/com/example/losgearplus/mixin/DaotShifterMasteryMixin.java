package com.example.losgearplus.mixin;

import com.example.losgearplus.shifter.ShifterMastery;
import com.example.losgearplus.shifter.ShifterMasterySync;
import daot.ShifterTitan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shifter Mastery System inside {@code daot.network.ModNetworking} (DAOT 2.5.1.1):
 * <ul>
 *   <li>{@code handleTitanShift} (transform key): blocks the transformation when the level's transformations are
 *       used up, counts it when it really starts (the player enters {@code pendingBites}), grants mastery XP and
 *       refunds part of the stamina cost according to the level. Leaving titan form (player riding a
 *       {@code ShifterTitan}) is never blocked or counted.</li>
 *   <li>{@code tickShifterStamina}: lowers the stamina drain while in titan form (stay transformed longer).</li>
 *   <li>{@code isBeastStaminaImmune}: at level 9 the player is immune to every stamina cost (transforming,
 *       titan form, Awaken, abilities that use {@code drainStamina}).</li>
 * </ul>
 * Targets are matched by NAME only (no overloads), like {@link DaotTeaseShiftStaminaMixin}.
 * Server thread only, so the static fields below are safe.
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class DaotShifterMasteryMixin {

	@Shadow
	@Final
	private static Map<UUID, Float> playerStamina;

	@Shadow
	@Final
	private static Map<UUID, Float> playerMaxStamina;

	@Shadow
	@Final
	private static Map<UUID, Object> pendingBites;

	@Shadow
	@Final
	private static Map<UUID, Object> pendingShifts;

	@Unique
	private static boolean losgearplus$hadBite;

	@Unique
	private static Float losgearplus$staminaBeforeShift;

	@Unique
	private static long losgearplus$lastTick;

	@Unique
	private static final Map<UUID, Float> losgearplus$tickSnapshot = new HashMap<>();

	// ---- transforming -----------------------------------------------------------------------------------------

	@Inject(method = "handleTitanShift", at = @At("HEAD"), cancellable = true, remap = false)
	private static void losgearplus$gateShift(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		UUID id = player.getUUID();
		losgearplus$hadBite = pendingBites.containsKey(id);
		losgearplus$staminaBeforeShift = playerStamina.get(id);

		if (player.isSpectator()) return;
		if (player.getVehicle() instanceof ShifterTitan) return; // leaving titan form: never blocked or counted
		if (losgearplus$hadBite || pendingShifts.containsKey(id)) return; // already transforming

		if (ShifterMastery.ruleLevel(id) >= ShifterMastery.MAX_LEVEL) {
			// Full control: no entry = full stamina (DAOT uses the maximum as the default).
			playerStamina.remove(id);
			losgearplus$staminaBeforeShift = null;
			return;
		}

		if (!ShifterMastery.canShift(player)) {
			int lvl = ShifterMastery.getLevel(id);
			int max = ShifterMastery.maxTransforms(id);
			long secs = ShifterMastery.ticksUntilReset(id) / 20L;
			player.displayClientMessage(Component.literal(
					"Mastery level " + lvl + ": all " + max + " transformation(s) used. Ready again in "
							+ (secs / 60) + "m" + (secs % 60) + "s."), true);
			ci.cancel();
		}
	}

	@Inject(method = "handleTitanShift", at = @At("RETURN"), remap = false)
	private static void losgearplus$afterShift(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		UUID id = player.getUUID();
		boolean hadBite = losgearplus$hadBite;
		Float before = losgearplus$staminaBeforeShift;
		losgearplus$hadBite = false;
		losgearplus$staminaBeforeShift = null;

		if (hadBite || !pendingBites.containsKey(id)) return; // no new transformation started

		// The refund uses the level the shifter had when transforming (before any level-up from this shift).
		float mult = ShifterMastery.shiftCostMultiplier(id);
		ShifterMastery.registerShift(player);
		ShifterMastery.addXp(player, ShifterMastery.XP_PER_TRANSFORMATION);
		ShifterMasterySync.send(player, true);

		if (mult >= 1f) return;
		if (before == null) before = playerMaxStamina.get(id); // no entry = it was full
		Float now = playerStamina.get(id);
		if (before == null || now == null || now >= before) return;
		playerStamina.put(id, now + (before - now) * (1f - mult));
	}

	// ---- titan-form drain ------------------------------------------------------------------------------

	@Inject(method = "tickShifterStamina", at = @At("HEAD"), remap = false)
	private static void losgearplus$snapshotStamina(MinecraftServer server, CallbackInfo ci) {
		losgearplus$tickSnapshot.clear();
		losgearplus$tickSnapshot.putAll(playerStamina);
	}

	@Inject(method = "tickShifterStamina", at = @At("RETURN"), remap = false)
	private static void losgearplus$masteryDrain(MinecraftServer server, CallbackInfo ci) {
		long tick = server.getTickCount();
		int dt = (int) Math.max(1L, Math.min(40L, tick - losgearplus$lastTick));
		losgearplus$lastTick = tick;

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!(p.getVehicle() instanceof ShifterTitan)) continue;
			UUID id = p.getUUID();
			float mult = ShifterMastery.drainMultiplier(id);
			if (mult <= 0f) continue; // level 9: infinite time, stamina is not touched

			Float max = playerMaxStamina.get(id);
			float maxStamina = max == null ? 100f : max;
			Float before = losgearplus$tickSnapshot.get(id);
			if (before == null) before = maxStamina; // no entry = it was full

			// We own the titan-form drain: whatever DAOT did this tick (drain, regen, Beast Titan / royal blood
			// bonuses) is discarded and replaced by the mastery drain, so stamina never goes up while transformed.
			float perTick = maxStamina / (ShifterMastery.BASE_TITAN_SECONDS * 20f) * mult;
			playerStamina.put(id, Math.max(0f, before - perTick * dt));
		}
		losgearplus$tickSnapshot.clear();
	}

	// ---- level 9: no stamina cost --------------------------------------------------------------------

	@Inject(method = "isBeastStaminaImmune", at = @At("RETURN"), cancellable = true, remap = false)
	private static void losgearplus$masterImmune(UUID id, CallbackInfoReturnable<Boolean> cir) {
		// Level 9 is immune to every stamina cost; below that the Beast Titan's natural immunity is removed so
		// it spends stamina like any other titan.
		cir.setReturnValue(ShifterMastery.ruleLevel(id) >= ShifterMastery.MAX_LEVEL);
	}
}