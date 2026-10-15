package com.example.losgearplus.codex.skills;

import daot.ShifterTitan;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
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
 * EXEMPLO de skill própria: onda de choque em volta do titã (dano + empurrão). Copie esta classe para criar as suas.
 * Só Attack, Armored e War Hammer podem equipar; precisa de braço funcionando.
 */
public final class ShockwaveSkill extends AbstractCustomSkill {
	private static final float DAMAGE = 12.0f;
	private static final double KNOCKBACK = 1.4;

	public ShockwaveSkill() {
		super("shockwave", Set.of("attack", "armored", "warhammer"), 20 * 15, 0.08f);
	}

	@Override
	public boolean needsArms() {
		return true;
	}

	@Override
	public boolean activate(ServerPlayer player, LivingEntity titan) {
		ServerLevel level = player.serverLevel();
		double radius = Math.max(6.0, titan.getBbWidth() * 3.0);
		AABB box = titan.getBoundingBox().inflate(radius, 2.0, radius);
		DamageSource source = level.damageSources().playerAttack(player);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				t -> t != titan && t != player && t.isAlive() && !(t instanceof ShifterTitan) && !isTitanPart(t))) {
			Vec3 away = target.position().subtract(titan.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			target.hurt(source, DAMAGE);
			target.push(away.x * KNOCKBACK, 0.5, away.z * KNOCKBACK);
			target.hurtMarked = true;
		}

		// Anel de poeira no chão + som grave.
		for (int i = 0; i < 48; i++) {
			double a = Math.PI * 2.0 * i / 48.0;
			level.sendParticles(ParticleTypes.CLOUD, titan.getX() + Math.cos(a) * radius * 0.5, titan.getY() + 0.2,
					titan.getZ() + Math.sin(a) * radius * 0.5, 0, Math.cos(a), 0.05, Math.sin(a), 0.6);
		}
		level.playSound(null, titan.getX(), titan.getY(), titan.getZ(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 3.0f, 0.5f);
		return true;
	}

	/** Nuca, olho, perna, mão e garra do titã são entidades à parte: não levam o golpe. */
	private static boolean isTitanPart(Entity e) {
		return e.getClass().getSimpleName().matches(".*(Nape|Eye|Leg|Hand|Grab)(Entity)?$");
	}
}
