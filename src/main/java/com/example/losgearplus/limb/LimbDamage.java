package com.example.losgearplus.limb;

import daot.BladeItem;
import daot.ShifterTitan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gatilho por dano. Chamado no começo de {@code LivingEntity#hurt} (ver LivingEntityLimbMixin) e devolve o dano
 * final: se o golpe decepou uma parte, o dano é limitado para a vítima NÃO morrer nesse golpe.
 *
 * <ul>
 *   <li>Vítima humana: o próprio jogador.</li>
 *   <li>Vítima titã: a entidade {@code ShifterTitan}; a perda vai para o estado do shifter (dono do titã),
 *       então ao se destransformar o corpo humano já está sem o membro.</li>
 *   <li>Atacantes: jogador com lâmina do DAOT ({@code BladeItem}) ou qualquer titã do DAOT.</li>
 * </ul>
 */
public final class LimbDamage {
	private LimbDamage() {}

	private enum Source { NONE, BLADE, TITAN }

	private static final Map<UUID, Long> LAST_LOSS = new HashMap<>();

	public static float onHurt(LivingEntity victim, DamageSource src, float amount) {
		if (victim.level().isClientSide || amount <= 0f) return amount;
		if (victim.invulnerableTime > 10 || victim.isDeadOrDying() || victim.isInvulnerableTo(src)) return amount;

		boolean titanVictim = victim instanceof ShifterTitan;
		ServerPlayer owner = ownerOf(victim);
		if (owner == null || owner.isCreative() || owner.isSpectator()) return amount;

		Source kind = classify(src);
		if (kind == Source.NONE) return amount;

		long now = victim.level().getGameTime();
		Long last = LAST_LOSS.get(owner.getUUID());
		if (last != null && now - last < LimbRules.LOSS_COOLDOWN_TICKS) return amount;

		RandomSource rnd = owner.getRandom();
		float chance = kind == Source.TITAN ? LimbRules.TITAN_HIT
				: (titanVictim ? LimbRules.BLADE_VS_TITAN : LimbRules.BLADE_VS_HUMAN);
		if (rnd.nextFloat() >= chance) return amount;

		LimbPart part = pick(rnd, LimbData.of(owner), kind == Source.BLADE);
		if (part == null || !LimbManager.lose(owner, part, true)) return amount;
		LAST_LOSS.put(owner.getUUID(), now);

		// "Sem morrer": o golpe que decepa nunca mata (deixa pelo menos 1 de vida).
		float hp = victim.getHealth();
		return amount >= hp ? Math.max(0f, hp - 1f) : amount;
	}

	private static ServerPlayer ownerOf(LivingEntity victim) {
		if (victim instanceof ServerPlayer sp) return sp;
		if (victim instanceof ShifterTitan st && victim.getServer() != null) {
			UUID id = st.getShifterUUID();
			return id == null ? null : victim.getServer().getPlayerList().getPlayer(id);
		}
		return null;
	}

	private static Source classify(DamageSource src) {
		Entity attacker = src.getEntity();
		if (attacker == null) return Source.NONE;
		if (attacker instanceof ShifterTitan) return Source.TITAN;
		String cls = attacker.getClass().getName();
		if (cls.startsWith("daot.") && attacker.getClass().getSimpleName().contains("Titan")) return Source.TITAN;
		if (attacker instanceof ServerPlayer sp
				&& (sp.getMainHandItem().getItem() instanceof BladeItem || sp.getOffhandItem().getItem() instanceof BladeItem)) {
			return Source.BLADE;
		}
		return Source.NONE;
	}

	/** Sorteio ponderado entre as partes que ainda estão inteiras. Olhos só em golpe de lâmina. */
	private static LimbPart pick(RandomSource rnd, LimbState st, boolean eyes) {
		int total = 0;
		for (LimbPart p : LimbPart.VALUES) {
			if (st.isIntact(p) && (eyes || !p.isEye())) total += LimbRules.dropWeight(p);
		}
		if (total <= 0) return null;
		int roll = rnd.nextInt(total);
		for (LimbPart p : LimbPart.VALUES) {
			if (!st.isIntact(p) || (!eyes && p.isEye())) continue;
			roll -= LimbRules.dropWeight(p);
			if (roll < 0) return p;
		}
		return null;
	}
}
