package com.example.losgearplus.mixin;

import com.example.losgearplus.weaker.WeakArmoredTitanEntity;
import com.example.losgearplus.weaker.WeakerArmoredManager;
import com.example.losgearplus.weaker.WeakerEntities;
import daot.ArmoredTitanEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transformação em Armored Titan: se o jogador escolheu o Weaker, o {@code new ArmoredTitanEntity} do DAOT vira
 * {@code new WeakArmoredTitanEntity}. O resto da sequência (raio, explosão, mastery, ascensão...) é o do DAOT.
 * {@code spawnArmoredTitan} é private static em ModNetworking; alvo só pelo NOME (único), igual aos outros mixins.
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class DaotWeakerSpawnMixin {

	@Inject(method = "spawnArmoredTitan", at = @At("HEAD"), remap = false)
	private static void los_gear_plus$weakHead(ServerPlayer player, ServerLevel level, double x, double y, double z,
			float yaw, CallbackInfo ci) {
		WeakerArmoredManager.PENDING.set(WeakerArmoredManager.isWeakerSelected(player));
	}

	@Inject(method = "spawnArmoredTitan", at = @At("RETURN"), remap = false)
	private static void los_gear_plus$weakReturn(ServerPlayer player, ServerLevel level, double x, double y, double z,
			float yaw, CallbackInfo ci) {
		WeakerArmoredManager.PENDING.remove();
	}

	@Redirect(method = "spawnArmoredTitan", at = @At(value = "NEW", target = "daot/ArmoredTitanEntity"), remap = false)
	private static ArmoredTitanEntity los_gear_plus$weakNew(EntityType<? extends Monster> type, Level level) {
		if (Boolean.TRUE.equals(WeakerArmoredManager.PENDING.get())) {
			return new WeakArmoredTitanEntity(WeakerEntities.WEAK_ARMORED_TITAN, level);
		}
		return new ArmoredTitanEntity(type, level);
	}
}
