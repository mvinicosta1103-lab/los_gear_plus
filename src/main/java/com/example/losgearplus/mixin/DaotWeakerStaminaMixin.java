package com.example.losgearplus.mixin;

import com.example.losgearplus.weaker.WeakerArmoredConfig;
import com.example.losgearplus.weaker.WeakerArmoredManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

/**
 * Armored Weaker "mais econômico": o DAOT escreve direto no mapa {@code playerStamina}; então, como no
 * DaotTeaseShiftStaminaMixin, guardamos a stamina no começo e devolvemos a parte poupada no fim.
 * Cobre: transformação (handleTitanShift), dreno por tick na forma de titã (tickShifterStamina),
 * habilidades (drainStamina) e rugido (handleTitanRoar). Alvos só pelo NOME (todos únicos).
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class DaotWeakerStaminaMixin {

	@Shadow
	@Final
	private static Map<UUID, Float> playerStamina;

	@Unique private static Float losgearplus$shiftBefore;
	@Unique private static Float losgearplus$roarBefore;
	@Unique private static Float losgearplus$drainBefore;
	@Unique private static final Map<UUID, Float> losgearplus$tickBefore = new HashMap<>();

	// ---- custo de transformação ----
	@Inject(method = "handleTitanShift", at = @At("HEAD"), remap = false, require = 0)
	private static void losgearplus$shiftHead(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		losgearplus$shiftBefore = WeakerArmoredManager.isWeakerSelected(player) ? playerStamina.get(player.getUUID()) : null;
	}

	@Inject(method = "handleTitanShift", at = @At("RETURN"), remap = false, require = 0)
	private static void losgearplus$shiftReturn(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		Float before = losgearplus$shiftBefore;
		losgearplus$shiftBefore = null;
		WeakerArmoredManager.keep(playerStamina, player.getUUID(), before, WeakerArmoredConfig.SHIFT_COST_FACTOR);
	}

	// ---- rugido ----
	@Inject(method = "handleTitanRoar", at = @At("HEAD"), remap = false, require = 0)
	private static void losgearplus$roarHead(ServerPlayer player, CallbackInfo ci) {
		losgearplus$roarBefore = WeakerArmoredManager.ACTIVE.contains(player.getUUID()) ? playerStamina.get(player.getUUID()) : null;
	}

	@Inject(method = "handleTitanRoar", at = @At("RETURN"), remap = false, require = 0)
	private static void losgearplus$roarReturn(ServerPlayer player, CallbackInfo ci) {
		Float before = losgearplus$roarBefore;
		losgearplus$roarBefore = null;
		WeakerArmoredManager.keep(playerStamina, player.getUUID(), before, WeakerArmoredConfig.STAMINA_DRAIN_FACTOR);
	}

	// ---- habilidades (drainStamina(UUID, float)) ----
	@Inject(method = "drainStamina", at = @At("HEAD"), remap = false, require = 0)
	private static void losgearplus$drainHead(UUID id, float amount, CallbackInfo ci) {
		losgearplus$drainBefore = WeakerArmoredManager.ACTIVE.contains(id) ? playerStamina.get(id) : null;
	}

	@Inject(method = "drainStamina", at = @At("RETURN"), remap = false, require = 0)
	private static void losgearplus$drainReturn(UUID id, float amount, CallbackInfo ci) {
		Float before = losgearplus$drainBefore;
		losgearplus$drainBefore = null;
		WeakerArmoredManager.keep(playerStamina, id, before, WeakerArmoredConfig.STAMINA_DRAIN_FACTOR);
	}

	// ---- dreno contínuo (titã montado) ----
	@Inject(method = "tickShifterStamina", at = @At("HEAD"), remap = false, require = 0)
	private static void losgearplus$tickHead(MinecraftServer server, CallbackInfo ci) {
		losgearplus$tickBefore.clear();
		for (UUID id : WeakerArmoredManager.ACTIVE) {
			Float s = playerStamina.get(id);
			if (s != null) losgearplus$tickBefore.put(id, s);
		}
	}

	@Inject(method = "tickShifterStamina", at = @At("RETURN"), remap = false, require = 0)
	private static void losgearplus$tickReturn(MinecraftServer server, CallbackInfo ci) {
		for (Map.Entry<UUID, Float> e : losgearplus$tickBefore.entrySet()) {
			WeakerArmoredManager.keep(playerStamina, e.getKey(), e.getValue(), WeakerArmoredConfig.STAMINA_DRAIN_FACTOR);
		}
		losgearplus$tickBefore.clear();
	}
}
