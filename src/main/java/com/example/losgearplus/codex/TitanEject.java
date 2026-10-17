package com.example.losgearplus.codex;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
import daot.ModSounds;
import daot.ShifterTitan;
import daot.WarhammerTitanEntity;
import daot.network.CartEjectAnimPayload;
import daot.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ejeta o piloto do titã como o Smoke Screen do Cart Titan ({@code ejectPilot}): inicia o dismount do DAOT (que deixa
 * o corpo do titã ajoelhado, sem piloto), tira o jogador e o arremessa para trás e para cima. Usado pelo Steam Surge
 * e pela cristalização do Iron Skin. Note: Cart, Jaw, Ogre e Founding ESTENDEM o Attack no DAOT, então o ramo do
 * Attack cobre todos eles.
 */
public final class TitanEject {
	private TitanEject() {}

	private static final double BACK_SPEED = 3.2;
	private static final double UP_SPEED = 1.3;
	private static final int STEALTH_TICKS = 100;

	/**
	 * @param stealth true = o piloto fica invisível por 5 s (stealth do DAOT), como no Smoke Screen do Cart
	 * @return false se não deu para ejetar (titã já saindo, derrubado, empalado...); nada muda nesse caso
	 */
	public static boolean eject(ServerPlayer player, LivingEntity titan, boolean stealth) {
		if (!(titan instanceof ShifterTitan shifter) || shifter.isDismounting()) return false;

		Vec3 from = player.position().add(0.0, 0.5, 0.0); // onde o piloto está sentado, antes de sair
		float yaw = (float) Math.toRadians(titan.getYRot());

		if (!startDismount(titan, player)) return false;
		player.stopRiding();
		if (player.isPassenger()) return false;

		if (stealth) { // a ordem importa: invisível DEPOIS de sair
			player.setInvisible(true);
			ModNetworking.scheduleStealthInvisibility(player.getUUID(), STEALTH_TICKS);
		}

		Vec3 launch = new Vec3(Math.sin(yaw) * BACK_SPEED, UP_SPEED, -Math.cos(yaw) * BACK_SPEED);
		player.teleportTo(from.x, from.y, from.z);
		player.setDeltaMovement(launch);
		player.hurtMarked = true;
		sendAnimation(player, launch);
		player.serverLevel().playSound(null, from.x, from.y, from.z, ModSounds.STEAM_POOF, SoundSource.PLAYERS, 4.0f, 0.8f);
		return true;
	}

	/** Cada titã tem o seu {@code startDismounting} (não há classe-base em comum, a não ser o Attack). */
	private static boolean startDismount(LivingEntity titan, ServerPlayer player) {
		if (titan instanceof WarhammerTitanEntity t) t.startDismounting(player);
		else if (titan instanceof ColossalTitanEntity t) t.startDismounting(player);
		else if (titan instanceof BeastTitanEntity t) t.startDismounting(player);
		else if (titan instanceof FemaleTitanEntity t) t.startDismounting(player);
		else if (titan instanceof ArmoredTitanEntity t) t.startDismounting(player);
		else if (titan instanceof AttackTitanEntity t) t.startDismounting(player); // Attack, Cart, Jaw, Ogre, Founding
		else return false;
		return titan instanceof ShifterTitan s && s.isDismounting();
	}

	/** A animação de "voar para fora" do DAOT, para o próprio jogador e quem o está vendo. */
	private static void sendAnimation(ServerPlayer player, Vec3 launch) {
		CartEjectAnimPayload anim = new CartEjectAnimPayload(player.getId(), launch.x, launch.y, launch.z, false);
		if (ServerPlayNetworking.canSend(player, CartEjectAnimPayload.TYPE)) ServerPlayNetworking.send(player, anim);
		for (ServerPlayer nearby : PlayerLookup.tracking(player)) {
			if (ServerPlayNetworking.canSend(nearby, CartEjectAnimPayload.TYPE)) ServerPlayNetworking.send(nearby, anim);
		}
	}
}
