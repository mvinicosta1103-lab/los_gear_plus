package com.example.losgearplus.steam;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Steam Heal: liga/desliga com a tecla (J) e funciona como um efeito de regeneração contínua, sem recarga.
 *
 * <ul>
 *   <li>Quem pode: jogador com qualquer tag de shifter do DAOT (a mesma lista que o DAOT usa na regeneração).</li>
 *   <li>Liga ao apertar a tecla e só desliga ao apertar de novo (ou ao morrer, sair do servidor ou perder a tag de shifter).</li>
 *   <li>Enquanto ligado cura o titã que o jogador está montando (se for um {@code daot.ShifterTitan}) e o próprio jogador.</li>
 *   <li>Usa também o efeito de regeneração do próprio DAOT: o mesmo Regeneration (ambiente, com ícone, sem
 *       redemoinho) que {@code ModNetworking.tickShifterRegeneration} aplica (nível II; III para o ogre_shifter),
 *       a partícula de vapor do DAOT ({@code DannysAot.PLAYER_DISMOUNT_PARTICLE}) e o chiado.</li>
 *   <li>Visual extra: fumaça de fogueira (campfire) subindo do corpo.</li>
 * </ul>
 * Ajuste {@link #HEAL_FRACTION_PER_SECOND} para balancear a velocidade da cura.
 */
public final class SteamHealServer {
	private SteamHealServer() {}

	/** Fração da vida máxima curada POR SEGUNDO (0.01 = 1% por segundo; 100% em ~100 s). */
	public static final float HEAL_FRACTION_PER_SECOND = 0.01f;
	/** De quantos em quantos ticks a cura é aplicada (5 = 4 vezes por segundo). */
	private static final int HEAL_INTERVAL_TICKS = 5;
	/** De quantos em quantos ticks sai um pouco de fumaça. */
	private static final int SMOKE_INTERVAL_TICKS = 2;

	/** Liga/desliga cada parte visual (o efeito de Regeneration e a cura em % ficam sempre). */
	private static final boolean DAOT_STEAM_PARTICLES = true;
	private static final boolean CAMPFIRE_SMOKE = true;
	private static final boolean HISS_SOUND = true;

	/** Efeito Regeneration igual ao do DAOT: 100 ticks renovados antes de acabar; amplificador 1 (II) ou 2 (III, ogre_shifter). */
	private static final int REGEN_DURATION = 100;
	private static final int REGEN_RENEW_BELOW = 40;

	/** Tags de shifter do DAOT (conferidas em ModNetworking.tickShifterRegeneration, jar 2.5.0). */
	private static final Set<String> SHIFTER_TAGS = com.example.losgearplus.shifter.ShifterTypes.TAGS;

	private static final Class<?> SHIFTER_TITAN = findShifterTitan();

	/** Jogadores com o Steam Heal ligado (só em memória, como o ODMG Mode). */
	private static final Set<UUID> ACTIVE = new HashSet<>();

	private static boolean daotParticleLooked;
	private static ParticleOptions daotParticle;

	/** {@code daot.DannysAot.PLAYER_DISMOUNT_PARTICLE} (vapor do DAOT), resolvido por reflexão na primeira vez que precisa. */
	private static ParticleOptions daotParticle() {
		if (!daotParticleLooked) {
			daotParticleLooked = true;
			try {
				Object value = Class.forName("daot.DannysAot").getField("PLAYER_DISMOUNT_PARTICLE").get(null);
				if (value instanceof ParticleOptions options) daotParticle = options;
			} catch (Throwable ignored) {
				// sem DAOT / campo renomeado: fica só a fumaça de campfire
			}
		}
		return daotParticle;
	}

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
				(payload, context) -> toggle(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(SteamHealServer::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.getPlayer().getUUID()));
	}

	public static boolean isActive(ServerPlayer player) {
		return ACTIVE.contains(player.getUUID());
	}

	private static boolean isShifter(ServerPlayer player) {
		for (String tag : player.getTags()) {
			if (SHIFTER_TAGS.contains(tag)) return true;
		}
		return false;
	}

	private static void toggle(ServerPlayer player) {
		UUID id = player.getUUID();
		if (ACTIVE.remove(id)) {
			clearRegen(player);
			player.displayClientMessage(Component.translatable("los_gear_plus.steam_heal.off"), true);
			return;
		}
		if (!player.isAlive() || player.isSpectator()) return;
		if (!isShifter(player)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.steam_heal.not_shifter"), true);
			return;
		}
		ACTIVE.add(id);
		player.displayClientMessage(Component.translatable("los_gear_plus.steam_heal.on"), true);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (UUID id : new HashSet<>(ACTIVE)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			// Morreu, saiu ou deixou de ser shifter: o efeito acaba (e não volta sozinho ao respawnar).
			if (player == null || !player.isAlive() || player.isSpectator() || !isShifter(player)) {
				ACTIVE.remove(id);
				if (player != null) clearRegen(player);
				continue;
			}

			Entity vehicle = player.getVehicle();
			LivingEntity titan = vehicle instanceof LivingEntity living && SHIFTER_TITAN != null
					&& SHIFTER_TITAN.isInstance(vehicle) ? living : null;
			ServerLevel level = player.serverLevel();
			long time = level.getGameTime();

			keepRegen(player);

			if (time % HEAL_INTERVAL_TICKS == 0) {
				heal(player);
				if (titan != null) heal(titan);
			}
			LivingEntity body = titan != null ? titan : player;
			if (DAOT_STEAM_PARTICLES) daotSteam(level, body);
			if (time % SMOKE_INTERVAL_TICKS == 0 && CAMPFIRE_SMOKE) {
				smoke(level, body);
			}
			if (HISS_SOUND && time % 10 == 0) {
				level.playSound(null, body.getX(), body.getY() + body.getBbHeight() * 0.5, body.getZ(),
						SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.15f, 1.0f);
			}
		}
	}

	/** Mantém o Regeneration do DAOT no jogador (renova quando está acabando; mesmos parâmetros do DAOT). */
	private static void keepRegen(ServerPlayer player) {
		MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
		if (current != null && current.getDuration() >= REGEN_RENEW_BELOW) return;
		int amplifier = player.getTags().contains("ogre_shifter") ? 2 : 1;
		// (duração, amplificador, ambiente = true, partículas = false, ícone = true), igual ao DAOT.
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION, amplifier, true, false, true));
	}

	/** Ao desligar: tira só o Regeneration "ambiente" (o do DAOT/nosso); uma poção de verdade do jogador fica. */
	private static void clearRegen(ServerPlayer player) {
		MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
		if (current != null && current.isAmbient()) player.removeEffect(MobEffects.REGENERATION);
	}

	/** Vapor do DAOT: mesma partícula e mesmos números da regeneração dele (1 por tick, espalhada pelo corpo). */
	private static void daotSteam(ServerLevel level, LivingEntity body) {
		ParticleOptions particle = daotParticle();
		if (particle == null) return;
		double w = Math.max(0.4, body.getBbWidth() * 0.5);
		double h = Math.max(0.5, body.getBbHeight());
		int count = Math.min(6, 1 + (int) (h / 2.0));
		for (int i = 0; i < count; i++) {
			double x = body.getX() + (level.random.nextDouble() - 0.5) * 1.6 * w;
			double y = body.getY() + h * 0.5 + (level.random.nextDouble() - 0.5) * 0.6 * h;
			double z = body.getZ() + (level.random.nextDouble() - 0.5) * 1.6 * w;
			level.sendParticles(particle, x, y, z, 1, 0.0, 0.02, 0.0, 0.01);
		}
	}

	private static void heal(LivingEntity entity) {
		if (entity.getHealth() >= entity.getMaxHealth()) return;
		float perTick = HEAL_FRACTION_PER_SECOND / 20.0f;
		entity.heal(entity.getMaxHealth() * perTick * HEAL_INTERVAL_TICKS);
	}

	/** Fumaça de fogueira subindo em volta do corpo (a mesma partícula da campfire, não a nuvem branca comum). */
	private static void smoke(ServerLevel level, LivingEntity body) {
		double w = Math.max(0.4, body.getBbWidth() * 0.5);
		double h = Math.max(0.5, body.getBbHeight());
		int count = Math.min(10, 1 + (int) (h / 1.5));
		for (int i = 0; i < count; i++) {
			double x = body.getX() + (level.random.nextDouble() - 0.5) * 2.0 * w;
			double y = body.getY() + level.random.nextDouble() * h;
			double z = body.getZ() + (level.random.nextDouble() - 0.5) * 2.0 * w;
			// count = 0: o servidor manda UMA partícula com velocidade = (dx, dy, dz) * speed (sobe devagar, como a campfire).
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0, 0.0, 1.0, 0.0, 0.07);
		}
	}

	/** Fumaça leve da regeneração passiva do titã (sem o vapor e o chiado do Steam Heal ligado). */
	public static void passiveSmoke(ServerLevel level, LivingEntity body) {
		smoke(level, body);
	}
}
