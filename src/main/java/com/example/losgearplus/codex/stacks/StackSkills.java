package com.example.losgearplus.codex.stacks;

import com.example.losgearplus.codex.CodexScheduler;
import com.example.losgearplus.codex.GroundBreaker;
import com.example.losgearplus.codex.SmokeCurtain;
import com.example.losgearplus.codex.TitanCodex;
import com.example.losgearplus.codex.TitanIds;
import com.example.losgearplus.codex.CodexPayloads;
import com.example.losgearplus.codex.TitanEject;
import com.example.losgearplus.codex.TitanJump;
import daot.AttackTitanEntity;
import daot.FemaleTitanEntity;
import daot.ModSounds;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * As habilidades que cada stack concede. Cada uma é uma {@link StackSkill} (id {@code los_gear_plus:stack_<nome>}) e
 * age sobre o titã que o jogador está montando, qualquer que seja. Todos os números ficam no construtor
 * (recarga, custo de stamina) e no corpo de {@code activate}; o nível do stack entra por {@code power()}.
 *
 * <pre>
 *  Armored   Hardened Fists  punhos endurecidos (nativo do DAOT ou camada de cristal): +dano por 15 s
 *            Wall Leap       o pulo do próprio titã, reto para cima
 *  Attack    Frenzy          fúria: +dano e regeneração por 12 s
 *  Female    Crystal Shell   casca de cristal: leva bem menos dano por 8 s
 *  Colossal  Heat Wave       onda de calor: dano e fogo em área
 *  Beast     Savage Roar     rugido: lentidão e fraqueza nos inimigos, +dano para você
 *  Warhammer Crystal Spikes  fenda de espetos de cristal que corre para a frente
 *  Jaw       Pounce          o pulo do titã para a frente, que esmaga o chão onde cai
 *  Cart      Smoke Bomb      nuvem de fumaça que cega os inimigos e esconde o titã
 *  Ogre      Iron Skin       cristalização total: ejeta você e o corpo vira estátua indestrutível
 * </pre>
 */
public final class StackSkills {
	private StackSkills() {}

	/** O "titã" do Codex onde ficam as skills de stack (3 slots, 3 teclas). */
	public static final String TAB = TitanIds.STACK;

	public static void registerAll() {
		TitanCodex.register(new HardenedFists());
		TitanCodex.register(new WallLeap());
		TitanCodex.register(new Frenzy());
		TitanCodex.register(new CrystalShell());
		TitanCodex.register(new HeatWave());
		TitanCodex.register(new SavageRoar());
		TitanCodex.register(new CrystalSpikes());
		TitanCodex.register(new Pounce());
		TitanCodex.register(new SmokeBomb());
		TitanCodex.register(new IronSkin());
	}

	// ---- utilidades ----------------------------------------------------------------------------------------

	private static Vec3 forward(Entity e) {
		float yaw = (float) Math.toRadians(e.getYRot());
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	/** Titã de ~13 blocos = 1.0. */
	private static double scale(LivingEntity titan) {
		return Math.max(0.8, Math.min(1.8, titan.getBbHeight() / 13.0));
	}

	private static boolean isTitanPart(Entity e) {
		return e.getClass().getSimpleName().matches(".*(Nape|Eye|Leg|Hand|Grab)(Entity)?$");
	}

	/** Inimigos num raio (horizontal) em volta de um ponto: tudo menos o dono, o titã dele, aliados de time e partes de titã. */
	private static List<LivingEntity> enemies(ServerLevel level, LivingEntity titan, ServerPlayer owner, Vec3 center, double radius) {
		AABB box = new AABB(center, center).inflate(radius, Math.max(4.0, radius * 0.5), radius);
		return level.getEntitiesOfClass(LivingEntity.class, box, t -> {
			if (t == titan || t == owner || !t.isAlive() || t.isAlliedTo(owner) || isTitanPart(t)) return false;
			double dx = t.getX() - center.x, dz = t.getZ() - center.z;
			return dx * dx + dz * dz <= radius * radius;
		});
	}

	private static void away(LivingEntity target, Vec3 from, double horizontal, double vertical) {
		Vec3 d = target.position().subtract(from).multiply(1.0, 0.0, 1.0);
		d = d.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : d.normalize();
		target.push(d.x * horizontal, vertical, d.z * horizontal);
		target.hurtMarked = true;
	}

	private static void sound(ServerLevel level, Entity at, SoundEvent s, float volume, float pitch) {
		level.playSound(null, at.getX(), at.getY() + at.getBbHeight() * 0.5, at.getZ(), s, SoundSource.PLAYERS, volume, pitch);
	}

	private static void burst(ServerLevel level, Entity titan, ParticleOptions p, int count) {
		double w = Math.max(0.5, titan.getBbWidth() * 0.5), h = Math.max(1.0, titan.getBbHeight());
		level.sendParticles(p, titan.getX(), titan.getY() + h * 0.5, titan.getZ(), count, w, h * 0.4, w, 0.05);
	}

	private static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions p, int points) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2.0 * i / points;
			level.sendParticles(p, c.x + Math.cos(a) * radius, c.y + 0.5, c.z + Math.sin(a) * radius, 0, Math.cos(a), 0.02, Math.sin(a), 0.5);
		}
	}

	/** Só o Attack "puro" e a Female têm braço endurecido de verdade no DAOT (Cart, Jaw, Ogre e Founding herdam o estado do Attack, mas não o desenham). */
	private static boolean nativeHardening(LivingEntity t) {
		return t.getClass() == AttackTitanEntity.class || t instanceof FemaleTitanEntity;
	}

	private static int hardeningMode(LivingEntity t) {
		return t instanceof FemaleTitanEntity f ? f.getHardeningMode() : ((AttackTitanEntity) t).getHardeningMode();
	}

	private static void setHardeningMode(LivingEntity t, int mode) {
		if (t instanceof FemaleTitanEntity f) f.setHardeningMode(mode);
		else if (t instanceof AttackTitanEntity a) a.setHardeningMode(mode);
	}

	// ---- ARMORED ---------------------------------------------------------------------------------------------

	/**
	 * Punhos endurecidos por 15 s: +dano causado (x1.4 / 1.5 / 1.6). No Attack e na Female usa o ENDURECIMENTO NATIVO do
	 * DAOT (textura, animação e, no Attack, a escalada de paredes); nos outros titãs o DAOT não tem braço endurecido,
	 * então os punhos ganham uma camada de cristal desenhada no cliente.
	 */
	static final class HardenedFists extends StackSkill {
		private static final int DURATION = 20 * 15;

		HardenedFists() { super(StackSource.ARMORED, "stack_hardened_fists", 20 * 30, 0.12f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			ServerLevel level = player.serverLevel();
			if (nativeHardening(titan)) {
				if (hardeningMode(titan) != 0) return false; // já está endurecido: não gasta nada
				setHardeningMode(titan, titan instanceof FemaleTitanEntity ? 3 : 1);
				CodexScheduler.runLater(level.getServer(), DURATION, () -> {
					if (titan.isAlive() && hardeningMode(titan) != 0) setHardeningMode(titan, 0);
				});
			} else {
				long now = level.getGameTime();
				CrystalTitans.broadcast(titan, CodexPayloads.CrystalFx.ARMS, now, now + DURATION);
			}
			StackBuffs.add(titan, 1.0 + 0.4 * power(player), 0.85, DURATION, ParticleTypes.CRIT);
			burst(level, titan, ParticleTypes.CRIT, 40);
			sound(level, titan, SoundEvents.ANVIL_LAND, 2.0f, 1.6f);
			return true;
		}
	}

	/** Salto de escalada: usa o PULO do próprio titã (animação e impulso do DAOT), reto para cima, por cima de muros. */
	static final class WallLeap extends StackSkill {
		WallLeap() { super(StackSource.ARMORED, "stack_wall_leap", 20 * 12, 0.10f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			if (!TitanJump.start(titan, player, false)) {
				player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.cant_jump"), true);
				return false;
			}
			ServerLevel level = player.serverLevel();
			burst(level, titan, ParticleTypes.CLOUD, 30);
			sound(level, titan, SoundEvents.ANVIL_LAND, 2.0f, 0.5f);
			return true;
		}
	}

	// ---- ATTACK ----------------------------------------------------------------------------------------------

	/** Fúria: +dano causado (x1.4 / 1.5 / 1.6) e regeneração por 12 s. */
	static final class Frenzy extends StackSkill {
		Frenzy() { super(StackSource.ATTACK, "stack_frenzy", 20 * 40, 0.15f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			StackBuffs.add(titan, 1.0 + 0.4 * power(player), 1.0, 20 * 12, ParticleTypes.ANGRY_VILLAGER);
			titan.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 12, level(player) - 1));
			ServerLevel level = player.serverLevel();
			burst(level, titan, ParticleTypes.ANGRY_VILLAGER, 25);
			sound(level, titan, SoundEvents.RAVAGER_ROAR, 3.0f, 0.5f);
			return true;
		}
	}

	// ---- FEMALE ----------------------------------------------------------------------------------------------

	/** Casca de cristal: dano recebido x0.55 / 0.45 / 0.35 por 8 s. */
	static final class CrystalShell extends StackSkill {
		CrystalShell() { super(StackSource.FEMALE, "stack_crystal_shell", 20 * 35, 0.12f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			StackBuffs.add(titan, 1.0, 0.65 - 0.1 * level(player), 20 * 8, ParticleTypes.END_ROD);
			ServerLevel level = player.serverLevel();
			burst(level, titan, ParticleTypes.END_ROD, 50);
			sound(level, titan, SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0f, 0.6f);
			return true;
		}
	}

	// ---- COLOSSAL --------------------------------------------------------------------------------------------

	/** Onda de calor: dano (8 x poder) e fogo em quem está perto, empurrando para fora. */
	static final class HeatWave extends StackSkill {
		HeatWave() { super(StackSource.COLOSSAL, "stack_heat_wave", 20 * 25, 0.15f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			ServerLevel level = player.serverLevel();
			double radius = 12.0 * scale(titan);
			Vec3 c = titan.position();
			DamageSource src = level.damageSources().playerAttack(player);
			for (LivingEntity t : enemies(level, titan, player, c, radius)) {
				t.hurt(src, (float) (8.0 * power(player)));
				t.setRemainingFireTicks(20 * 5);
				away(t, c, 1.3, 0.5);
			}
			for (int i = 1; i <= 4; i++) ring(level, c, radius * i / 4.0, ParticleTypes.FLAME, 36); // onda que se expande
			sound(level, titan, SoundEvents.FIRE_EXTINGUISH, 4.0f, 0.4f);
			sound(level, titan, SoundEvents.BLAZE_SHOOT, 3.0f, 0.5f);
			return true;
		}
	}

	// ---- BEAST -----------------------------------------------------------------------------------------------

	/** Rugido: lentidão e fraqueza nos inimigos num raio de 20 blocos; você causa +15% de dano por 10 s. */
	static final class SavageRoar extends StackSkill {
		SavageRoar() { super(StackSource.BEAST, "stack_savage_roar", 20 * 30, 0.12f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			ServerLevel level = player.serverLevel();
			Vec3 c = titan.position();
			int duration = (int) (20 * 8 * power(player));
			for (LivingEntity t : enemies(level, titan, player, c, 20.0)) {
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
				t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
				away(t, c, 0.6, 0.2);
			}
			StackBuffs.add(titan, 1.0 + 0.15 * power(player), 1.0, 20 * 10, null);
			for (int i = 1; i <= 3; i++) ring(level, c, 6.0 * i, ParticleTypes.SONIC_BOOM, 10);
			sound(level, titan, SoundEvents.ENDER_DRAGON_GROWL, 4.0f, 0.6f);
			return true;
		}
	}

	// ---- WARHAMMER -------------------------------------------------------------------------------------------

	/** Fenda de cristal: uma rachadura corre para a frente (1,5 bloco por tick), ferindo e lançando quem está nela. */
	static final class CrystalSpikes extends StackSkill {
		CrystalSpikes() { super(StackSource.WARHAMMER, "stack_crystal_spikes", 20 * 20, 0.12f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			ServerLevel level = player.serverLevel();
			Vec3 f = forward(titan);
			Vec3 origin = titan.position().add(f.scale(3.0 * scale(titan)));
			int length = (int) Math.round(18 * scale(titan));
			sound(level, titan, SoundEvents.AMETHYST_BLOCK_BREAK, 4.0f, 0.5f);
			step(level, titan, player, origin, f, 0, length, power(player));
			return true;
		}

		private static void step(ServerLevel level, LivingEntity titan, ServerPlayer player, Vec3 origin, Vec3 dir, int i, int length, double power) {
			if (i >= length || !titan.isAlive()) return;
			Vec3 p = origin.add(dir.scale(1.5 * i));
			DamageSource src = level.damageSources().playerAttack(player);
			for (LivingEntity t : enemies(level, titan, player, p, 2.5)) {
				t.hurt(src, (float) (9.0 * power));
				t.push(0.0, 0.9, 0.0);
				t.hurtMarked = true;
			}
			GroundBreaker.cleave(level, p, origin, 2);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1.0, p.z, 8, 0.4, 0.8, 0.4, 0.05);
			if (i % 3 == 0) level.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 0.8f);
			CodexScheduler.runLater(level.getServer(), 1, () -> step(level, titan, player, origin, dir, i + 1, length, power));
		}
	}

	// ---- JAW -------------------------------------------------------------------------------------------------

	/**
	 * Bote: usa o PULO do próprio titã, para onde o piloto olha, e ao aterrissar (o chão volta a ser tocado depois de
	 * ter saído dele) esmaga o chão ferindo quem está perto.
	 */
	static final class Pounce extends StackSkill {
		Pounce() { super(StackSource.JAW, "stack_pounce", 20 * 14, 0.10f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			if (!TitanJump.start(titan, player, true)) {
				player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.cant_jump"), true);
				return false;
			}
			ServerLevel level = player.serverLevel();
			sound(level, titan, SoundEvents.RAVAGER_ROAR, 2.0f, 1.2f);
			CodexScheduler.runLater(level.getServer(), 4, () -> watch(level, titan, player, 0, false));
			return true;
		}

		/** Espera o titã sair do chão e voltar a pisar (no máximo 8 s). */
		private void watch(ServerLevel level, LivingEntity titan, ServerPlayer player, int ticks, boolean airborne) {
			if (!titan.isAlive() || !player.isAlive() || ticks > 160) return;
			boolean ground = titan.onGround();
			if (airborne && ground) {
				land(level, titan, player);
				return;
			}
			boolean inAir = airborne || !ground;
			CodexScheduler.runLater(level.getServer(), 1, () -> watch(level, titan, player, ticks + 1, inAir));
		}

		private void land(ServerLevel level, LivingEntity titan, ServerPlayer player) {
			Vec3 c = titan.position();
			double radius = 6.0 * scale(titan);
			DamageSource src = level.damageSources().playerAttack(player);
			for (LivingEntity t : enemies(level, titan, player, c, radius)) {
				t.hurt(src, (float) (12.0 * power(player)));
				away(t, c, 1.2, 0.5);
			}
			GroundBreaker.slam(level, c, 0.6 * scale(titan));
			sound(level, titan, ModSounds.TITAN_STOMP, 4.0f, 1.1f);
		}
	}

	// ---- CART ------------------------------------------------------------------------------------------------

	/** Bomba de fumaça: nuvem de 7 s que cega os inimigos (e os atrasa) e deixa o titã levando 20% menos dano. */
	static final class SmokeBomb extends StackSkill {
		SmokeBomb() { super(StackSource.CART, "stack_smoke_bomb", 20 * 45, 0.10f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			ServerLevel level = player.serverLevel();
			double radius = Math.max(6.0, titan.getBbWidth() * 1.5);
			SmokeCurtain.cloud(level, titan.getX(), titan.getY(), titan.getZ(), radius, Math.max(6.0, titan.getBbHeight()), 20 * 7);
			int blind = (int) (20 * 5 * power(player));
			for (LivingEntity t : enemies(level, titan, player, titan.position(), radius * 1.5)) {
				t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind, 0));
				t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, blind, 0));
			}
			StackBuffs.add(titan, 1.0, 0.8, 20 * 7, null);
			sound(level, titan, ModSounds.STEAM_POOF, 4.0f, 0.8f);
			return true;
		}
	}

	// ---- OGRE ------------------------------------------------------------------------------------------------

	/**
	 * Cristalização total: o titã se ajoelha (o dismount do DAOT), o piloto é EJETADO e o corpo vira uma estátua de
	 * cristal: imóvel e indestrutível; só um Thunder Spear o estilhaça. Ver {@link CrystalTitans}. Você perde o titã.
	 */
	static final class IronSkin extends StackSkill {
		IronSkin() { super(StackSource.OGRE, "stack_iron_skin", 20 * 60, 0.10f); }

		@Override
		public boolean activate(ServerPlayer player, LivingEntity titan) {
			if (CrystalTitans.isCrystal(titan) || !TitanEject.eject(player, titan, false)) {
				player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.cant_now"), true);
				return false;
			}
			CrystalTitans.start(titan);
			return true;
		}
	}
}
