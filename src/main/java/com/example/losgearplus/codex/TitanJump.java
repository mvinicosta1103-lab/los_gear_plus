package com.example.losgearplus.codex;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.FemaleTitanEntity;
import daot.WarhammerTitanEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Faz o titã PULAR usando o pulo do próprio DAOT ({@code triggerJump}, o mesmo que a tecla de pulo aciona), com a
 * animação, o impulso e a aterrissagem dele.
 *
 * <p>Por que não basta empurrar o titã pelo servidor: o titã montado é movido pelo CLIENTE do piloto (o
 * {@code travel} roda lá), então um {@code setDeltaMovement} no servidor é sobrescrito e o titã não sai do chão. O pulo
 * do DAOT cuida disso (ele guarda o impulso e o aplica no {@code travel}).
 *
 * <p>O DAOT lê o que o piloto está apertando ({@code zza}/{@code xxa}) para escolher a direção; aqui forçamos "para
 * frente" ou "parado" só durante a chamada e restauramos em seguida. O Colossal não tem pulo no DAOT.
 */
public final class TitanJump {
	private TitanJump() {}

	/**
	 * @param forward true = pula para onde o piloto olha; false = pula reto para cima
	 * @return true se o pulo começou (o DAOT ignora o pedido se o titã não está no chão, está atacando, etc.)
	 */
	public static boolean start(LivingEntity titan, ServerPlayer rider, boolean forward) {
		float oldForward = rider.zza;
		float oldStrafe = rider.xxa;
		rider.zza = forward ? 1.0f : 0.0f;
		rider.xxa = 0.0f;
		try {
			return trigger(titan, rider);
		} finally {
			rider.zza = oldForward;
			rider.xxa = oldStrafe;
		}
	}

	private static boolean trigger(LivingEntity titan, ServerPlayer rider) {
		if (titan instanceof WarhammerTitanEntity t) {
			t.triggerJump(rider);
			return t.isJumping();
		}
		if (titan instanceof BeastTitanEntity t) {
			t.triggerJump(rider);
			return t.isJumping();
		}
		if (titan instanceof FemaleTitanEntity t) {
			t.triggerJump(rider);
			return t.isJumping();
		}
		if (titan instanceof ArmoredTitanEntity t) {
			t.triggerJump(rider);
			return t.isJumping();
		}
		if (titan instanceof AttackTitanEntity t) { // Attack, Cart, Jaw, Ogre e Founding herdam do Attack
			t.triggerJump(rider);
			return t.isJumping();
		}
		return false;
	}
}
