package com.example.losgearplus.codex.stacks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Buffs temporários de dano de um titã, usados pelas skills de stack: um multiplicador para o dano que ELE causa
 * ({@code dealt}) e outro para o dano que ELE recebe ({@code taken}). Quem aplica é o
 * {@code LivingEntityStackBuffMixin}, no mesmo ponto onde o sistema de membros já mexe no dano. Um buff novo
 * substitui o anterior do mesmo titã. Só em memória.
 */
public final class StackBuffs {
	private StackBuffs() {}

	private record Buff(int endTick, double dealt, double taken, ParticleOptions aura) {}

	private static final Map<UUID, Buff> BUFFS = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StackBuffs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BUFFS.clear());
	}

	public static void add(LivingEntity titan, double dealt, double taken, int ticks, ParticleOptions aura) {
		MinecraftServer server = titan.getServer();
		if (server == null) return;
		BUFFS.put(titan.getUUID(), new Buff(server.getTickCount() + ticks, dealt, taken, aura));
	}

	/** Multiplicador do dano que esta entidade CAUSA (1.0 = sem buff). */
	public static double dealt(Entity attacker) {
		Buff b = active(attacker);
		return b == null ? 1.0 : b.dealt();
	}

	/** Multiplicador do dano que esta entidade RECEBE (1.0 = sem buff). */
	public static double taken(Entity victim) {
		Buff b = active(victim);
		return b == null ? 1.0 : b.taken();
	}

	private static Buff active(Entity e) {
		if (e == null || BUFFS.isEmpty()) return null;
		Buff b = BUFFS.get(e.getUUID());
		if (b == null) return null;
		MinecraftServer server = e.getServer();
		return server == null || b.endTick() <= server.getTickCount() ? null : b;
	}

	private static void tick(MinecraftServer server) {
		if (BUFFS.isEmpty()) return;
		int now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Buff>> it = BUFFS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Buff> e = it.next();
			Buff b = e.getValue();
			if (b.endTick() <= now) {
				it.remove();
				continue;
			}
			if (b.aura() == null || now % 6 != 0) continue; // aura em volta do titã enquanto o buff dura
			for (ServerLevel level : server.getAllLevels()) {
				Entity titan = level.getEntity(e.getKey());
				if (titan == null) continue;
				double w = Math.max(0.5, titan.getBbWidth() * 0.5), h = Math.max(1.0, titan.getBbHeight());
				level.sendParticles(b.aura(), titan.getX(), titan.getY() + h * 0.5, titan.getZ(), 6, w, h * 0.35, w, 0.02);
				break;
			}
		}
	}
}
