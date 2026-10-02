package com.example.losgearplus.mixin;

import java.util.Map;
import java.util.UUID;
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
 * Tease Shift (tecla K do DAOT) sem gasto de stamina.
 *
 * O DAOT desconta a stamina dentro de {@code ModNetworking.handleTeaseShift} (10% do máximo; 20% para o Colossal),
 * escrevendo direto no mapa {@code playerStamina}. Em vez de reescrever o método, guardamos a stamina no começo e,
 * se ele a diminuiu, devolvemos o valor original no fim. A checagem "Not enough stamina" (mínimo de 25%) continua
 * valendo: ela só impede o Tease Shift com a barra quase vazia.
 *
 * O alvo é escolhido só pelo NOME ({@code handleTeaseShift} não tem sobrecarga), então o mesmo mixin serve para
 * intermediary e Mojang. Código do servidor, roda numa thread só: os campos estáticos abaixo são seguros.
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class DaotTeaseShiftStaminaMixin {

	@Shadow
	@Final
	private static Map<UUID, Float> playerStamina;

	@Unique
	private static Float losgearplus$staminaBefore;

	@Inject(method = "handleTeaseShift", at = @At("HEAD"), remap = false)
	private static void losgearplus$saveStamina(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		losgearplus$staminaBefore = playerStamina.get(player.getUUID());
	}

	@Inject(method = "handleTeaseShift", at = @At("RETURN"), remap = false)
	private static void losgearplus$refundStamina(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		UUID id = player.getUUID();
		Float before = losgearplus$staminaBefore;
		losgearplus$staminaBefore = null;
		Float now = playerStamina.get(id);
		if (now == null) return;
		if (before == null) {
			playerStamina.remove(id); // sem entrada = stamina cheia (o DAOT usa o máximo como padrão)
		} else if (now < before) {
			playerStamina.put(id, before);
		}
	}
}
