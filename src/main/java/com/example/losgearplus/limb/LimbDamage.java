package com.example.losgearplus.limb;

import daot.BladeItem;
import daot.ShifterTitan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gatilho por dano. Chamado no começo de {@code LivingEntity#hurt} (ver LivingEntityLimbMixin) e devolve o dano
 * final: se o golpe decepou uma parte, o dano é limitado para a vítima NÃO morrer nesse golpe.
 *
 * <ul>
 *   <li>Vítima humana (o próprio jogador): SÓ perde membro para titãs (puros ou shifters), parte sorteada. Perder o
 *       membro dá dano extra ({@link LimbRules#HUMAN_LOSS_EXTRA_DAMAGE}).</li>
 *   <li>Vítima titã: a entidade {@code ShifterTitan}; a perda vai para o estado do shifter (dono do titã),
 *       então ao se destransformar o corpo humano já está sem o membro. A parte perdida depende de ONDE o golpe
 *       atingiu ({@link LimbHit}) e a chance cresce com o dano.</li>
 *   <li>Origens contra titã de shifter: lâmina do DAOT, titã, explosão (lança do trovão/canhão) e qualquer outro
 *       dano forte com atacante.</li>
 * </ul>
 */
public final class LimbDamage {
	private LimbDamage() {}

	private enum Source { NONE, BLADE, TITAN, BLAST, OTHER }

	private static final Map<UUID, Long> LAST_LOSS = new HashMap<>();

	public static float onHurt(LivingEntity victim, DamageSource src, float amount) {
		if (victim.level().isClientSide || amount <= 0f) return amount;
		if (victim.invulnerableTime > 10 || victim.isDeadOrDying() || victim.isInvulnerableTo(src)) return amount;

		boolean titanVictim = victim instanceof ShifterTitan;
		ServerPlayer owner = ownerOf(victim);
		if (owner == null || owner.isCreative() || owner.isSpectator()) return amount;

		Source kind = classify(src);
		if (kind == Source.NONE) return amount;
		// humano: só titã (puro ou shifter) arranca membro
		if (!titanVictim && kind != Source.TITAN) return amount;

		long now = victim.level().getGameTime();
		Long last = LAST_LOSS.get(owner.getUUID());
		if (last != null && now - last < LimbRules.LOSS_COOLDOWN_TICKS) return amount;

		RandomSource rnd = owner.getRandom();
		LimbState state = LimbData.of(owner);
		LimbPart part;
		float extra = 0f;

		if (titanVictim) {
			float maxHealth = victim.getMaxHealth();
			if (kind == Source.OTHER && amount < maxHealth * LimbRules.OTHER_MIN_DAMAGE_FRACTION) return amount;
			// 1) ONDE foi atingido: tronco, cabeça ou parte já perdida não decepa nada
			part = LimbHit.partHit(victim, src, kind == Source.TITAN, kind == Source.BLADE, state);
			if (part == null) return amount;
			// 2) chance: base da origem + bônus pelo dano relativo à vida do titã
			float chance = LimbRules.titanVictimChance(baseChance(kind), amount, maxHealth);
			if (rnd.nextFloat() >= chance) return amount;
		} else {
			if (rnd.nextFloat() >= LimbRules.TITAN_HIT) return amount;
			part = pick(rnd, state, false);
			if (part == null) return amount;
			extra = LimbRules.HUMAN_LOSS_EXTRA_DAMAGE * (part.isUpper() ? 1f : 0.5f);
		}

		if (!LimbManager.lose(owner, part, victim, true)) return amount;
		LAST_LOSS.put(owner.getUUID(), now);

		// "Sem morrer": o golpe que decepa nunca mata (deixa pelo menos 1 de vida).
		float total = amount + extra;
		float hp = victim.getHealth();
		return total >= hp ? Math.max(0f, hp - 1f) : total;
	}

	private static float baseChance(Source kind) {
		switch (kind) {
			case BLADE: return LimbRules.BLADE_VS_TITAN;
			case TITAN: return LimbRules.TITAN_HIT;
			case BLAST: return LimbRules.BLAST_VS_TITAN;
			default: return LimbRules.OTHER_VS_TITAN;
		}
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
		Entity direct = src.getDirectEntity();
		if (attacker instanceof ShifterTitan) return Source.TITAN;
		if (attacker != null) {
			String cls = attacker.getClass().getName();
			if (cls.startsWith("daot.") && attacker.getClass().getSimpleName().contains("Titan")) return Source.TITAN;
		}
		// explosão (tag do jogo) ou projétil/arma explosiva do DAOT (lança do trovão, canhão): heurística pelo nome
		if (src.is(DamageTypeTags.IS_EXPLOSION)) return Source.BLAST;
		if (direct != null && direct != attacker && direct.getClass().getName().startsWith("daot.")) {
			String n = direct.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
			if (n.contains("spear") || n.contains("cannon") || n.contains("thunder")) return Source.BLAST;
		}
		if (attacker == null) return Source.NONE;
		if (attacker instanceof ServerPlayer sp
				&& (sp.getMainHandItem().getItem() instanceof BladeItem || sp.getOffhandItem().getItem() instanceof BladeItem)) {
			return Source.BLADE;
		}
		return Source.OTHER;
	}

	/** Sorteio ponderado entre as partes que ainda estão inteiras (humanos). Olhos só quando {@code eyes}. */
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