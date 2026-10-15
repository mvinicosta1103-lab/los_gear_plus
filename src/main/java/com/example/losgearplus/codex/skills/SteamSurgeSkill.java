package com.example.losgearplus.codex.skills;

import com.example.losgearplus.codex.SmokeCurtain;
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
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Steam Surge: o titã solta uma grande cortina de fumaça que EJETA o shifter (igual ao Smoke Screen do Cart Titan)
 * e o protege: por 10 segundos nenhum golpe o atinge.
 *
 * <p>Fluxo (espelha {@code CartShifterTitanEntity.ejectPilot(true)}): inicia o dismount do titã, tira o piloto, joga
 * ele para trás e para cima, deixa invisível por 5 s (stealth do DAOT) e liga a cortina + proteção
 * ({@link SmokeCurtain}). O resto (imunidade à queda, o titã evaporando, a fumaça do dismount) o próprio DAOT faz
 * quando o piloto sai. Serve para todos os titãs do Codex. O ID continua {@code los_gear_plus:steam_surge}.
 */
public final class SteamSurgeSkill extends AbstractCustomSkill {
	// Mesmos valores do eject do Cart.
	private static final double EJECT_BACK_SPEED = 3.2;
	private static final double EJECT_UP_SPEED = 1.3;
	private static final int STEALTH_TICKS = 100;

	public SteamSurgeSkill() {
		super("steam_surge", Set.of(), 20 * 60, 0.15f);
	}

	@Override
	public boolean activate(ServerPlayer player, LivingEntity titan) {
		// Já está saindo (tecla B)? Então não há o que ejetar.
		if (!(titan instanceof ShifterTitan shifter) || shifter.isDismounting()) return false;

		Vec3 from = player.position().add(0.0, 0.5, 0.0); // onde o piloto está sentado, antes de sair
		float yaw = (float) Math.toRadians(titan.getYRot());
		double radius = Math.min(24.0, Math.max(5.0, titan.getBbWidth() * 1.2));
		double height = Math.min(40.0, Math.max(10.0, titan.getBbHeight() * 0.6));
		double cx = titan.getX(), cy = titan.getY(), cz = titan.getZ();

		// 1) dismount do próprio DAOT (libera a saída, fumaça da nuca, som). Pode recusar (knocked, empalado...).
		if (!startDismount(titan, player)) return false;
		player.stopRiding();
		if (player.isPassenger()) return false;

		// 2) stealth como no Smoke Screen do Cart (a ordem importa: invisível DEPOIS de sair)
		player.setInvisible(true);
		ModNetworking.scheduleStealthInvisibility(player.getUUID(), STEALTH_TICKS);

		// 3) arremesso para trás e para cima
		Vec3 launch = new Vec3(Math.sin(yaw) * EJECT_BACK_SPEED, EJECT_UP_SPEED, -Math.cos(yaw) * EJECT_BACK_SPEED);
		player.teleportTo(from.x, from.y, from.z);
		player.setDeltaMovement(launch);
		player.hurtMarked = true;
		sendEjectAnimation(player, launch);

		ServerLevel level = player.serverLevel();
		level.playSound(null, from.x, from.y, from.z, ModSounds.STEAM_POOF, SoundSource.PLAYERS, 4.0f, 0.8f);

		// 4) a cortina de fumaça e os 10 s de proteção
		SmokeCurtain.start(player, cx, cy, cz, radius, height);
		return true;
	}

	/** Cada titã tem o seu {@code startDismounting} (não há classe-base em comum no DAOT). */
	private static boolean startDismount(LivingEntity titan, ServerPlayer player) {
		if (titan instanceof WarhammerTitanEntity t) t.startDismounting(player);
		else if (titan instanceof ColossalTitanEntity t) t.startDismounting(player);
		else if (titan instanceof BeastTitanEntity t) t.startDismounting(player);
		else if (titan instanceof FemaleTitanEntity t) t.startDismounting(player);
		else if (titan instanceof ArmoredTitanEntity t) t.startDismounting(player);
		else if (titan instanceof AttackTitanEntity t) t.startDismounting(player);
		else return false;
		return titan instanceof ShifterTitan s && s.isDismounting();
	}

	/** A animação de "voar para fora" do DAOT (a mesma do Cart), para o próprio jogador e quem o está vendo. */
	private static void sendEjectAnimation(ServerPlayer player, Vec3 launch) {
		CartEjectAnimPayload anim = new CartEjectAnimPayload(player.getId(), launch.x, launch.y, launch.z, false);
		if (ServerPlayNetworking.canSend(player, CartEjectAnimPayload.TYPE)) ServerPlayNetworking.send(player, anim);
		for (ServerPlayer nearby : PlayerLookup.tracking(player)) {
			if (ServerPlayNetworking.canSend(nearby, CartEjectAnimPayload.TYPE)) ServerPlayNetworking.send(nearby, anim);
		}
	}
}
