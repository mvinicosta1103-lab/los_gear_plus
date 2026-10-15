package com.example.losgearplus.codex;

import daot.DannysAot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.Level;

/**
 * Cortina de fumaça do Steam Surge: uma nuvem grande (a mesma partícula do Smoke Screen do Cart Titan) e a
 * proteção do shifter: durante {@link #PROTECT_TICKS} nenhum dano o atinge ("esquiva" de tudo). Só o que ignora
 * invulnerabilidade (void, /kill) passa.
 *
 * <p>Tudo em memória do servidor; os números ficam nas constantes abaixo.
 */
public final class SmokeCurtain {
	private SmokeCurtain() {}

	/** 10 segundos de proteção e de fumaça. */
	public static final int PROTECT_TICKS = 20 * 10;

	/** Quantidade de partículas por tick no começo (diminui nos últimos 3 s, como no Cart). */
	private static final int PUFFS_PER_TICK = 80;
	private static final int FADE_TICKS = 60;
	private static final double VIEW_RANGE_SQ = 160.0 * 160.0;

	private record Curtain(ResourceKey<Level> dimension, double x, double y, double z, double radius, double height, int endTick) {}

	private static final List<Curtain> CURTAINS = new ArrayList<>();
	/** Jogador -> tick do servidor em que a proteção acaba. */
	private static final Map<UUID, Integer> PROTECTED_UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> LAST_DODGE_FX = new HashMap<>();

	private static boolean particleLooked;
	private static ParticleOptions smokeParticle;

	/** A partícula do Smoke Screen do Cart ({@code DannysAot.CART_SMOKE_PARTICLE}); se sumir do DAOT, usa fumaça de fogueira. */
	private static ParticleOptions smokeParticle() {
		if (!particleLooked) {
			particleLooked = true;
			try {
				smokeParticle = (ParticleOptions) DannysAot.CART_SMOKE_PARTICLE;
			} catch (Throwable ignored) {
				// campo renomeado/removido: fica o fallback
			}
		}
		return smokeParticle != null ? smokeParticle : ParticleTypes.CAMPFIRE_COSY_SMOKE;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SmokeCurtain::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			PROTECTED_UNTIL.remove(id);
			LAST_DODGE_FX.remove(id);
		});
		// "Esquiva": enquanto protegido, o dano é cancelado ANTES de ser aplicado (queda, titãs, mobs, tudo).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && isProtected(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				dodgeEffect(player);
				return false;
			}
			return true;
		});
	}

	public static boolean isProtected(ServerPlayer player) {
		Integer end = PROTECTED_UNTIL.get(player.getUUID());
		return end != null && end > player.server.getTickCount();
	}

	/**
	 * Solta a cortina em volta de (x,y,z) e protege o jogador pelos próximos {@link #PROTECT_TICKS} ticks.
	 *
	 * @param radius raio horizontal da nuvem (blocos)
	 * @param height altura da nuvem (blocos)
	 */
	public static void start(ServerPlayer player, double x, double y, double z, double radius, double height) {
		int now = player.server.getTickCount();
		PROTECTED_UNTIL.put(player.getUUID(), now + PROTECT_TICKS);
		CURTAINS.add(new Curtain(player.level().dimension(), x, y, z, radius, height, now + PROTECT_TICKS));
		player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.smoke_start"), true);
	}

	private static void dodgeEffect(ServerPlayer player) {
		int now = player.server.getTickCount();
		Integer last = LAST_DODGE_FX.get(player.getUUID());
		if (last != null && now - last < 5) return; // vários golpes no mesmo instante: um efeito só
		LAST_DODGE_FX.put(player.getUUID(), now);
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
				8, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT,
				SoundSource.PLAYERS, 0.6f, 1.7f);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();

		// proteção que acabou
		if (!PROTECTED_UNTIL.isEmpty()) {
			Iterator<Map.Entry<UUID, Integer>> it = PROTECTED_UNTIL.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Integer> e = it.next();
				if (e.getValue() > now) continue;
				it.remove();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null) p.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.smoke_end"), true);
			}
		}

		// nuvens de fumaça
		if (CURTAINS.isEmpty()) return;
		Iterator<Curtain> it = CURTAINS.iterator();
		while (it.hasNext()) {
			Curtain c = it.next();
			ServerLevel level = server.getLevel(c.dimension());
			int left = c.endTick() - now;
			if (level == null || left <= 0) {
				it.remove();
				continue;
			}
			spawnPuffs(level, c, left);
		}
	}

	private static void spawnPuffs(ServerLevel level, Curtain c, int left) {
		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(c.x(), c.y(), c.z()) < VIEW_RANGE_SQ) viewers.add(p);
		}
		if (viewers.isEmpty()) return;

		float strength = Math.min(1.0f, left / (float) FADE_TICKS);
		int puffs = Math.round(PUFFS_PER_TICK * strength);
		ParticleOptions particle = smokeParticle();
		for (int i = 0; i < puffs; i++) {
			double angle = level.random.nextDouble() * Math.PI * 2.0;
			double dist = level.random.nextDouble() * c.radius();
			double px = c.x() + Math.cos(angle) * dist;
			double pz = c.z() + Math.sin(angle) * dist;
			double py = c.y() + level.random.nextDouble() * c.height();
			double speed = 0.35 + level.random.nextDouble() * 0.85;
			double vx = Math.cos(angle) * speed;
			double vz = Math.sin(angle) * speed;
			double vy = 0.08 + level.random.nextDouble() * 0.12;
			for (ServerPlayer viewer : viewers) {
				level.sendParticles(viewer, particle, true, px, py, pz, 0, vx, vy, vz, 1.0);
			}
		}
		if (left % 6 == 0) {
			level.playSound(null, c.x(), c.y() + c.height() * 0.5, c.z(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
					3.0f * Math.max(0.3f, strength), 0.35f + level.random.nextFloat() * 0.15f);
		}
	}
}
