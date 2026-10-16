package com.example.losgearplus.codex.stacks;

import com.example.losgearplus.codex.CodexServer;
import com.example.losgearplus.codex.TitanCodex;
import com.example.losgearplus.codex.TitanSkill;
import daot.ShifterTitan;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Detecta "um shifter matou outro" e entrega o stack.
 *
 * <p>No DAOT o titã NÃO morre de verdade: ao chegar a 0 de vida ele fica "derrotado" e o piloto continua vivo. Por
 * isso o abate é contado em dois momentos, e o primeiro que acontecer vale (a mesma vítima só rende stack uma vez
 * a cada 5 minutos):
 * <ol>
 *   <li>o titã de um shifter é derrotado ({@code ALLOW_DEATH}, só observamos, nunca cancelamos);</li>
 *   <li>o shifter morre em forma humana ({@code AFTER_DEATH}).</li>
 * </ol>
 * O assassino é quem causou o dano: o titã de outro shifter (o dono vira o assassino), ou o próprio jogador (lâmina).
 *
 * <p>Regras: o assassino precisa ser shifter; não ganha stack do titã que já possui por natureza; ganha +1 nível do
 * titã da vítima (máximo {@link StackData#MAX_LEVEL}) E os stacks que a vítima carregava, que ela perde ("os stacks
 * passam de mão em mão").
 */
public final class StackKills {
	private StackKills() {}

	private static final int CREDIT_COOLDOWN_TICKS = 20 * 60 * 5;
	private static final Map<UUID, Integer> CREDITED = new HashMap<>();

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ShifterTitan titan) onTitanDefeated(entity, titan, source);
			return true; // só observamos
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer victim) onPlayerDied(victim, source);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CREDITED.clear());
	}

	private static void onTitanDefeated(LivingEntity entity, ShifterTitan titan, DamageSource source) {
		UUID victimId = titan.getShifterUUID();
		MinecraftServer server = entity.getServer();
		if (victimId == null || server == null) return; // titã sem dono (puro)
		StackSource type = StackSource.ofTitan(entity);
		if (type == null) return;
		credit(server, victimId, server.getPlayerList().getPlayer(victimId), EnumSet.of(type), source);
	}

	private static void onPlayerDied(ServerPlayer victim, DamageSource source) {
		MinecraftServer server = victim.getServer();
		if (server == null || !StackSource.isShifter(victim)) return;
		credit(server, victim.getUUID(), victim, StackSource.owned(victim), source);
	}

	private static ServerPlayer killerOf(MinecraftServer server, DamageSource source) {
		Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		if (attacker instanceof ServerPlayer p) return p;
		if (attacker instanceof ShifterTitan t && t.getShifterUUID() != null) return server.getPlayerList().getPlayer(t.getShifterUUID());
		return null;
	}

	private static void credit(MinecraftServer server, UUID victimId, ServerPlayer victim, Set<StackSource> victimTypes, DamageSource source) {
		ServerPlayer killer = killerOf(server, source);
		if (killer == null || killer.getUUID().equals(victimId) || !StackSource.isShifter(killer)) return;

		int now = server.getTickCount();
		Integer last = CREDITED.get(victimId);
		if (last != null && now - last < CREDIT_COOLDOWN_TICKS) return;
		CREDITED.put(victimId, now);

		Set<StackSource> own = StackSource.owned(killer);
		Map<StackSource, Integer> gained = new LinkedHashMap<>();
		for (StackSource t : victimTypes) if (!own.contains(t)) gained.merge(t, 1, Integer::sum);
		boolean tookStacks = false;
		if (victim != null) {
			for (Map.Entry<String, Integer> e : StackData.all(victim).entrySet()) {
				StackSource s = StackSource.byId(e.getKey());
				if (s != null && !own.contains(s)) {
					gained.merge(s, e.getValue(), Integer::sum);
					tookStacks = true;
				}
			}
			if (StackData.hasAny(victim)) {
				StackData.clear(victim);
				victim.displayClientMessage(Component.translatable("los_gear_plus.codex.stack.lost"), false);
				CodexServer.sync(victim, false);
			}
		}
		if (gained.isEmpty()) return;

		for (Map.Entry<StackSource, Integer> e : gained.entrySet()) {
			int before = StackData.level(killer, e.getKey());
			int after = StackData.add(killer, e.getKey(), e.getValue());
			announce(killer, e.getKey(), before, after);
		}
		if (tookStacks) killer.displayClientMessage(Component.translatable("los_gear_plus.codex.stack.took"), false);
		CodexServer.sync(killer, false);
	}

	/** Mensagem + som + partículas: "Você absorveu o poder do Titã Blindado (Stack II): Punhos Endurecidos, ..." */
	private static void announce(ServerPlayer killer, StackSource source, int before, int after) {
		List<Component> names = new ArrayList<>();
		for (TitanSkill s : TitanCodex.all()) if (s instanceof StackSkill ss && ss.source() == source) names.add(s.name());
		Component skills = Component.empty();
		for (int i = 0; i < names.size(); i++) skills = skills.copy().append(i == 0 ? Component.empty() : Component.literal(", ")).append(names.get(i));

		Component sourceName = Component.translatable("los_gear_plus.codex.source." + source.id);
		killer.displayClientMessage(Component.translatable(before == 0 ? "los_gear_plus.codex.stack.gained" : "los_gear_plus.codex.stack.levelup",
				sourceName, roman(after), skills), false);

		ServerLevel level = killer.serverLevel();
		level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, killer.getX(), killer.getY() + killer.getBbHeight() * 0.5, killer.getZ(),
				40, 0.6, 0.8, 0.6, 0.3);
	}

	public static String roman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			default -> "III";
		};
	}
}
