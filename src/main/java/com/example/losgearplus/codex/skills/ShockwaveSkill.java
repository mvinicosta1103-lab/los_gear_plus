package com.example.losgearplus.codex.skills;

import com.example.losgearplus.codex.CodexScheduler;
import com.example.losgearplus.codex.GroundBreaker;
import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.FemaleTitanEntity;
import daot.ModSounds;
import daot.ShifterTitan;
import daot.WarhammerTitanEntity;
import daot.network.TitanImpactShakePayload;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shockwave: o titã SOCA O CHÃO com a animação de ground smash do próprio DAOT ({@code triggerAbility(1)}, a mesma
 * do Ground Stomp) e, no instante em que o punho bate (tick 14), o chão se parte: cratera, rachaduras que se
 * espalham, entulho voando, tremor de câmera, e tudo em volta leva dano e é arremessado.
 *
 * <p>Só os titãs que têm essa animação: Attack, Armored, Female e War Hammer (este precisa estar com o martelo).
 * Se o titã não pode atacar agora (recarga do golpe do DAOT, derrubado...), a skill não gasta nada. O DAOT também
 * aplica o dano e a quebra de blocos do ground smash dele no mesmo ponto; os dois efeitos se somam.
 */
public final class ShockwaveSkill extends AbstractCustomSkill {
	/** Tick da animação em que o punho bate no chão (o mesmo do DAOT: GROUNDSMASH_EFFECT_TICK). */
	private static final int IMPACT_DELAY_TICKS = 14;
	/** O DAOT faz o ground smash 5 blocos à frente do titã. */
	private static final double IMPACT_DISTANCE = 5.0;
	private static final float DAMAGE = 10.0f;
	private static final double KNOCKBACK = 1.6;

	public ShockwaveSkill() {
		super("shockwave", Set.of("attack", "armored", "female", "warhammer"), 20 * 15, 0.08f);
	}

	@Override
	public boolean needsArms() {
		return true;
	}

	@Override
	public boolean activate(ServerPlayer player, LivingEntity titan) {
		if (!triggerGroundSmash(titan)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.cant_now"), true);
			return false;
		}
		ServerLevel level = player.serverLevel();
		CodexScheduler.runLater(level.getServer(), IMPACT_DELAY_TICKS, () -> impact(player, titan, level));
		return true;
	}

	/**
	 * Liga a animação de ground smash do DAOT (habilidade 1 desses titãs) e confere que ela realmente começou:
	 * o DAOT ignora o pedido em silêncio se o titã estiver em recarga, derrubado, sem martelo, etc.
	 */
	private static boolean triggerGroundSmash(LivingEntity titan) {
		if (titan instanceof AttackTitanEntity t) {
			if (t.isTitanAttacking()) return false;
			t.triggerAbility(1);
			return t.isTitanAttacking() && t.getAttackNumber() == 3;
		}
		if (titan instanceof ArmoredTitanEntity t) {
			if (t.isTitanAttacking()) return false;
			t.triggerAbility(1);
			return t.isTitanAttacking() && t.getAttackNumber() == 3;
		}
		if (titan instanceof FemaleTitanEntity t) {
			if (t.isTitanAttacking()) return false;
			t.triggerAbility(1);
			return t.isTitanAttacking() && t.getAttackNumber() == 3;
		}
		if (titan instanceof WarhammerTitanEntity t) {
			if (t.isTitanAttacking()) return false;
			t.triggerAbility(1);
			return t.isTitanAttacking() && t.getAttackNumber() == 3;
		}
		return false;
	}

	/** O punho bateu: quebra o chão e joga todo mundo para longe. */
	private static void impact(ServerPlayer player, LivingEntity titan, ServerLevel level) {
		if (!player.isAlive() || !titan.isAlive() || titan.isRemoved()) return;
		if (titan instanceof ShifterTitan st && st.isIncapacitated()) return; // foi derrubado no meio do golpe

		double scale = Math.max(0.8, Math.min(1.8, titan.getBbHeight() / 13.0));
		float yaw = (float) Math.toRadians(titan.getYRot());
		Vec3 center = new Vec3(titan.getX() - Math.sin(yaw) * IMPACT_DISTANCE, titan.getY(), titan.getZ() + Math.cos(yaw) * IMPACT_DISTANCE);

		GroundBreaker.slam(level, center, scale);
		damageAndThrow(player, titan, level, center, 9.0 * scale);

		level.playSound(null, center.x, center.y, center.z, ModSounds.TITAN_STOMP, SoundSource.PLAYERS, 6.0f, 0.7f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 5.0f, 0.5f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3.0f, 0.4f);

		TitanImpactShakePayload shake = new TitanImpactShakePayload(center.x, center.y, center.z, 4.0f); // tremor de câmera do DAOT
		for (ServerPlayer nearby : PlayerLookup.around(level, center, 80.0)) {
			if (ServerPlayNetworking.canSend(nearby, TitanImpactShakePayload.TYPE)) ServerPlayNetworking.send(nearby, shake);
		}
	}

	private static void damageAndThrow(ServerPlayer player, LivingEntity titan, ServerLevel level, Vec3 center, double radius) {
		DamageSource source = level.damageSources().playerAttack(player);
		AABB box = new AABB(center, center).inflate(radius, 3.0, radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				t -> t != titan && t != player && t.isAlive() && !(t instanceof ShifterTitan) && !isTitanPart(t))) {
			Vec3 away = target.position().subtract(center).multiply(1.0, 0.0, 1.0);
			double dist = away.length();
			if (dist > radius) continue;
			away = dist < 1.0E-3 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			double falloff = 1.0 - 0.6 * (dist / radius); // perto do punho dói mais
			target.hurt(source, (float) (DAMAGE * falloff));
			target.push(away.x * KNOCKBACK * falloff, 0.6, away.z * KNOCKBACK * falloff);
			target.hurtMarked = true;
		}
	}

	/** Nuca, olho, perna, mão e garra do titã são entidades à parte: não levam o golpe. */
	private static boolean isTitanPart(Entity e) {
		return e.getClass().getSimpleName().matches(".*(Nape|Eye|Leg|Hand|Grab)(Entity)?$");
	}
}
