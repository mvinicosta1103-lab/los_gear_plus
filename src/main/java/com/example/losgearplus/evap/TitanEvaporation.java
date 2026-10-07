package com.example.losgearplus.evap;

import daot.ShifterTitan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Evaporação dos corpos de titã: ficam pretos enquanto soltam fumaça e então somem por completo.
 *
 * <ul>
 *   <li><b>Titã puro</b> morto: o DAOT remove o corpo em {@code TitanBody.tickCorpse}; o mixin
 *       {@code DaotTitanCorpseEvaporationMixin} troca isso por {@link #tickPureCorpse(Mob)}. O cliente deduz o
 *       escurecimento do próprio {@code deathTime}, então não precisa de pacote.</li>
 *   <li><b>Titã de shifter</b> sem portador (saiu do corpo ou o corpo foi derrotado e ele desmontou): este
 *       tick de mundo marca quem já teve portador (tag persistente) e, sem portador por
 *       {@link EvaporationRules#SHIFTER_DELAY_TICKS}, inicia a evaporação e avisa os clientes. Se o portador
 *       voltar ao corpo no meio, tudo é cancelado.</li>
 * </ul>
 */
public final class TitanEvaporation {
	private TitanEvaporation() {}

	/** Tag (salva no NBT do titã) de que este corpo já teve um shifter montado. */
	public static final String HAD_RIDER_TAG = "los_gear_plus.had_rider";

	/** uuid do titã -> tick de jogo em que a evaporação começa. */
	private static final Map<UUID, Long> STARTS = new HashMap<>();
	/** Titãs cujo início já foi avisado aos clientes. */
	private static final Set<UUID> ANNOUNCED = new HashSet<>();
	private static final List<LivingEntity> SCRATCH = new ArrayList<>();

	public static void init() {
		PayloadTypeRegistry.playS2C().register(EvaporateSyncPayload.TYPE, EvaporateSyncPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(TitanEvaporation::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STARTS.clear();
			ANNOUNCED.clear();
		});
	}

	// ---- titã de shifter ----------------------------------------------------------------------------------

	private static void tickLevel(ServerLevel level) {
		if ((level.getGameTime() & 1L) != 0L) return; // a cada 2 ticks basta (tudo é por tempo de jogo)
		// Coleta primeiro: discard() tira a entidade da lista na hora e quebraria a iteração.
		SCRATCH.clear();
		for (Entity e : level.getAllEntities()) {
			if (e instanceof ShifterTitan && e instanceof LivingEntity le && !le.isRemoved()) SCRATCH.add(le);
		}
		for (LivingEntity titan : SCRATCH) tickShifter(level, titan, (ShifterTitan) titan);
		SCRATCH.clear();
	}

	private static void tickShifter(ServerLevel level, LivingEntity titan, ShifterTitan st) {
		long now = level.getGameTime();
		UUID id = titan.getUUID();

		if (hasRider(titan, st)) {
			if (!titan.getTags().contains(HAD_RIDER_TAG)) titan.addTag(HAD_RIDER_TAG);
			if (STARTS.remove(id) != null && ANNOUNCED.remove(id)) broadcast(titan, 0); // portador voltou: cancela
			return;
		}
		if (!titan.getTags().contains(HAD_RIDER_TAG)) return; // titã que nunca teve portador não evapora

		Long start = STARTS.get(id);
		if (start == null) {
			STARTS.put(id, now + EvaporationRules.SHIFTER_DELAY_TICKS);
			return;
		}
		if (now < start) return;

		long elapsed = now - start;
		if (elapsed >= EvaporationRules.DURATION_TICKS) {
			STARTS.remove(id);
			ANNOUNCED.remove(id);
			finish(level, titan);
			return;
		}
		int remaining = (int) (EvaporationRules.DURATION_TICKS - elapsed);
		if (ANNOUNCED.add(id)) {
			broadcast(titan, remaining);
			hiss(level, titan, 6.0f);
		} else if (elapsed % 20L == 0L) {
			broadcast(titan, remaining); // reforço para quem entrou no alcance depois
			hiss(level, titan, 3.0f);
		}
		smoke(level, titan, elapsed / (float) EvaporationRules.DURATION_TICKS, 2);
	}

	private static boolean hasRider(LivingEntity titan, ShifterTitan st) {
		try {
			if (st.getShifterUUID() != null) return true;
		} catch (Throwable ignored) {
			// campo ainda não pronto: cai no teste de passageiro
		}
		for (Entity passenger : titan.getPassengers()) {
			if (passenger instanceof Player) return true;
		}
		return false;
	}

	private static void broadcast(Entity titan, int remainingTicks) {
		EvaporateSyncPayload payload = new EvaporateSyncPayload(titan.getId(), remainingTicks);
		for (ServerPlayer sp : PlayerLookup.tracking(titan)) ServerPlayNetworking.send(sp, payload);
	}

	// ---- titã puro ----------------------------------------------------------------------------------------

	/** Chamado todo tick pelo mixin no lugar de {@code TitanBody.tickCorpse}. */
	public static void tickPureCorpse(Mob titan) {
		if (titan.level().isClientSide || titan.isRemoved() || !(titan.level() instanceof ServerLevel level)) return;
		int elapsed = titan.deathTime - EvaporationRules.PURE_START_DEATH_TICKS;
		if (elapsed < 0) return;
		if (elapsed >= EvaporationRules.DURATION_TICKS) {
			finish(level, titan);
			return;
		}
		if (elapsed == 0) hiss(level, titan, 6.0f);
		else if (elapsed % 20 == 0) hiss(level, titan, 3.0f);
		if ((elapsed & 1) == 0) smoke(level, titan, elapsed / (float) EvaporationRules.DURATION_TICKS, 2);
	}

	// ---- efeitos ------------------------------------------------------------------------------------------

	private static void finish(ServerLevel level, LivingEntity titan) {
		smoke(level, titan, 1.0f, 5); // último estouro de fumaça
		hiss(level, titan, 8.0f);
		titan.discard();
	}

	/** Fumaça espalhada pelo corpo todo; mais densa conforme evapora. {@code boost} multiplica a quantidade. */
	private static void smoke(ServerLevel level, LivingEntity titan, float progress, int boost) {
		AABB box = titan.getBoundingBox();
		double cx = (box.minX + box.maxX) * 0.5;
		double cy = box.minY + box.getYsize() * 0.5;
		double cz = (box.minZ + box.maxZ) * 0.5;
		double dx = box.getXsize() * 0.35;
		double dy = box.getYsize() * 0.45;
		double dz = box.getZsize() * 0.35;
		int big = Math.round((6 + 22 * progress) * boost);
		int cosy = Math.round((2 + 6 * progress) * boost);
		int ash = Math.round((3 + 10 * progress) * boost);
		for (ServerPlayer sp : PlayerLookup.tracking(titan)) {
			send(level, sp, ParticleTypes.LARGE_SMOKE, cx, cy, cz, big, dx, dy, dz, 0.01);
			send(level, sp, ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, cosy, dx, dy, dz, 0.02);
			send(level, sp, ParticleTypes.ASH, cx, cy, cz, ash, dx, dy, dz, 0.0);
		}
	}

	private static void send(ServerLevel level, ServerPlayer sp, ParticleOptions type, double x, double y, double z,
			int count, double dx, double dy, double dz, double speed) {
		// longDistance = true: titãs são enormes e são vistos de muito além dos 32 blocos padrão das partículas.
		level.sendParticles(sp, type, true, x, y, z, count, dx, dy, dz, speed);
	}

	private static void hiss(ServerLevel level, LivingEntity titan, float volume) {
		level.playSound(null, titan.getX(), titan.getY() + titan.getBbHeight() * 0.5, titan.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, volume, 0.5f);
	}
}
