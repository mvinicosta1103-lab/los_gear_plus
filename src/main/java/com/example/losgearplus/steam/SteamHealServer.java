package com.example.losgearplus.steam;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Steam Heal: tecla que faz o shifter se curar com vapor.
 *
 * <ul>
 *   <li>Quem pode: jogador com qualquer tag de shifter do DAOT (a mesma lista que o DAOT usa na regeneração).</li>
 *   <li>Quem cura: o titã que o jogador está montando (se for um {@code daot.ShifterTitan}) e o próprio jogador.</li>
 *   <li>Quanto: {@link #HEAL_FRACTION} da vida máxima de cada um, com {@link #COOLDOWN_TICKS} de recarga.</li>
 * </ul>
 * Ajuste as três constantes abaixo para balancear.
 */
public final class SteamHealServer {
	private SteamHealServer() {}

	/** Fração da vida máxima curada por uso (0.35 = 35%). */
	public static final float HEAL_FRACTION = 0.35f;
	/** Recarga entre usos, em ticks (20 = 1 s). */
	public static final int COOLDOWN_TICKS = 20 * 15;

	/** Tags de shifter do DAOT (conferidas em ModNetworking.tickShifterRegeneration, jar 2.5.0). */
	private static final Set<String> SHIFTER_TAGS = Set.of(
			"attack", "colossal", "armored", "beast", "female", "warhammer",
			"founder", "triple_t", "ogre_shifter", "jaw", "cart_shifter");

	private static final Class<?> SHIFTER_TITAN = findShifterTitan();

	/** Tick de jogo em que cada jogador pode usar de novo. */
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private static Class<?> findShifterTitan() {
		try {
			return Class.forName("daot.ShifterTitan");
		} catch (Throwable t) {
			return null; // sem DAOT: só o jogador (que também não terá tag de shifter)
		}
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(SteamHealPayload.TYPE, SteamHealPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SteamHealPayload.TYPE,
				(payload, context) -> use(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY_AT.remove(handler.getPlayer().getUUID()));
	}

	private static boolean isShifter(ServerPlayer player) {
		for (String tag : player.getTags()) {
			if (SHIFTER_TAGS.contains(tag)) return true;
		}
		return false;
	}

	private static void use(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) return;
		if (!isShifter(player)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.steam_heal.not_shifter"), true);
			return;
		}

		ServerLevel level = player.serverLevel();
		long now = level.getGameTime();
		long ready = READY_AT.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			long seconds = (ready - now + 19) / 20;
			player.displayClientMessage(Component.translatable("los_gear_plus.steam_heal.cooldown", seconds), true);
			return;
		}

		Entity vehicle = player.getVehicle();
		LivingEntity titan = vehicle instanceof LivingEntity living && SHIFTER_TITAN != null
				&& SHIFTER_TITAN.isInstance(vehicle) ? living : null;

		boolean needs = player.getHealth() < player.getMaxHealth()
				|| (titan != null && titan.getHealth() < titan.getMaxHealth());
		if (!needs) {
			player.displayClientMessage(Component.translatable("los_gear_plus.steam_heal.full"), true);
			return;
		}

		heal(player);
		if (titan != null) heal(titan);
		steam(level, titan != null ? titan : player);
		READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS);
	}

	private static void heal(LivingEntity entity) {
		entity.heal(entity.getMaxHealth() * HEAL_FRACTION);
	}

	/** Nuvem de vapor em volta do corpo + chiado. */
	private static void steam(ServerLevel level, LivingEntity body) {
		double w = Math.max(0.5, body.getBbWidth() * 0.5);
		double h = Math.max(0.5, body.getBbHeight());
		level.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + h * 0.5, body.getZ(),
				Math.min(120, (int) (20 + h * 6)), w, h * 0.35, w, 0.03);
		level.playSound(null, body.getX(), body.getY(), body.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
	}
}
