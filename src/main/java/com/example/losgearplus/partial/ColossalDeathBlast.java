package com.example.losgearplus.partial;

import daot.ModSounds;
import daot.ShifterTitan;
import daot.network.ShiftShakePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Explosões do Colossal parcial:
 *  1) exitBlast    -> quando ele morre ou o shifter sai (começa a cair), na posição dele;
 *  2) landingBlast -> quando o corpo bate no chão, no PONTO ONDE ELE CAIU, deixando a área em chamas.
 *
 * O shifter dono do titã nunca é ferido, empurrado nem incendiado: ele é excluído do dano
 * e nenhum fogo é colocado perto dele.
 */
public final class ColossalDeathBlast {

    // ======================= AJUSTES =======================

    /** Explosão da saída/morte. */
    private static final double EXIT_RADIUS = 26.0;
    private static final float EXIT_DAMAGE = 30.0F;

    /** Explosão do impacto no chão. */
    private static final double LAND_RADIUS = 38.0;
    private static final float LAND_DAMAGE = 45.0F;
    private static final int FIRE_PATCHES = 90;

    /**
     * Para que lado o corpo cai e a que distância o centro do corpo bate no chão.
     * +1 = direita do titã (olhando na direção em que ele olha), -1 = esquerda.
     * Se a explosão aparecer do lado errado, troque o sinal; se ficar perto/longe demais,
     * mude a distância.
     */
    private static final int FALL_SIDE = 1;
    private static final double FALL_DISTANCE = 28.0;

    /** Nenhum fogo é colocado a menos de X blocos do shifter. */
    private static final double SAFE_RADIUS_AROUND_OWNER = 8.0;

    // =======================================================

    private ColossalDeathBlast() {}

    /** Explosão ao morrer / quando o shifter sai. Chame no onExit() do Colossal parcial. */
    public static void exitBlast(ServerLevel level, PartialShifterTitanEntity titan, ServerPlayer owner) {
        Vec3 base = titan.position();

        for (double h : new double[] {4.0, 12.0, 20.0, 28.0}) {
            particles(level, ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + h, base.z, 2, 4.0, 1.5, 4.0, 0.0);
        }
        particles(level, ParticleTypes.LARGE_SMOKE, base.x, base.y + 10.0, base.z, 200, 8.0, 8.0, 8.0, 0.1);
        particles(level, ParticleTypes.FLAME, base.x, base.y + 8.0, base.z, 120, 7.0, 6.0, 7.0, 0.12);

        sound(level, ModSounds.BOOM, base.x, base.y + 10.0, base.z, 12.0F, 0.6F);
        sound(level, ModSounds.FLESH_IMPACT_4, base.x, base.y + 10.0, base.z, 10.0F, 0.5F);

        damageArea(level, titan, owner, base, EXIT_RADIUS, EXIT_DAMAGE, 1.8, 0);
        shake(level, base);
    }

    /** Explosão quando o corpo bate no chão. Chame no lugar do corpo do fallImpact(). */
    public static void landingBlast(ServerLevel level, PartialShifterTitanEntity titan, ServerPlayer owner) {
        Vec3 c = landingPoint(level, titan);
        Vec3 axis = sideAxis(titan);

        // várias explosões ao longo do corpo caído
        for (double along : new double[] {-12.0, 0.0, 12.0}) {
            double x = c.x + axis.x * along;
            double z = c.z + axis.z * along;
            double y = surfaceY(level, x, z, c.y);
            particles(level, ParticleTypes.EXPLOSION_EMITTER, x, y + 2.0, z, 3, 3.0, 1.0, 3.0, 0.0);
        }
        particles(level, ParticleTypes.LARGE_SMOKE, c.x, c.y + 2.0, c.z, 300, LAND_RADIUS * 0.4, 3.0, LAND_RADIUS * 0.4, 0.15);
        particles(level, ParticleTypes.FLAME, c.x, c.y + 2.0, c.z, 250, LAND_RADIUS * 0.4, 2.0, LAND_RADIUS * 0.4, 0.1);
        particles(level, ParticleTypes.LAVA, c.x, c.y + 2.0, c.z, 100, LAND_RADIUS * 0.35, 2.0, LAND_RADIUS * 0.35, 0.0);

        sound(level, ModSounds.BOOM, c.x, c.y + 2.0, c.z, 14.0F, 0.5F);
        sound(level, ModSounds.FLESH_IMPACT_4, c.x, c.y + 2.0, c.z, 12.0F, 0.4F);

        damageArea(level, titan, owner, c, LAND_RADIUS, LAND_DAMAGE, 2.5, 40);

        for (int i = 0; i < FIRE_PATCHES; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            double dist = level.random.nextDouble() * LAND_RADIUS * 0.85;
            placeFire(level, owner, c.x + Math.cos(ang) * dist, c.z + Math.sin(ang) * dist);
        }

        shake(level, c);
    }

    /** Fumaça/brasas que ficam no local do impacto depois da queda (use no tickFalling). */
    public static void landingSmoke(ServerLevel level, PartialShifterTitanEntity titan) {
        Vec3 c = landingPoint(level, titan);
        particles(level, ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.0, c.z, 30, 12.0, 1.5, 12.0, 0.03);
        particles(level, ParticleTypes.FLAME, c.x, c.y + 2.0, c.z, 20, 12.0, 3.0, 12.0, 0.02);
    }

    /** Ponto no chão onde o corpo do titã cai. */
    public static Vec3 landingPoint(ServerLevel level, PartialShifterTitanEntity titan) {
        Vec3 axis = sideAxis(titan);
        double x = titan.getX() + axis.x * FALL_DISTANCE;
        double z = titan.getZ() + axis.z * FALL_DISTANCE;
        return new Vec3(x, surfaceY(level, x, z, titan.getY()), z);
    }

    // ------------------------------------------------------

    /** Direção lateral (para onde o corpo cai), unitária e horizontal. */
    private static Vec3 sideAxis(PartialShifterTitanEntity titan) {
        double yaw = Math.toRadians(titan.getYRot());
        return new Vec3(-Math.cos(yaw) * FALL_SIDE, 0.0, -Math.sin(yaw) * FALL_SIDE);
    }

    private static double surfaceY(ServerLevel level, double x, double z, double fallback) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!level.isLoaded(new BlockPos(bx, 64, bz))) return fallback;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    private static boolean isProtected(LivingEntity t, PartialShifterTitanEntity titan, ServerPlayer owner) {
        if (t == titan || t == owner) return true;
        if (t instanceof ShifterTitan) return true;
        if (t.getVehicle() instanceof ShifterTitan) return true;
        if (t instanceof Player p && p.isSpectator()) return true;
        String n = t.getClass().getSimpleName();
        return n.endsWith("NapeEntity") || n.endsWith("EyeEntity") || n.endsWith("HandEntity");
    }

    private static void damageArea(ServerLevel level, PartialShifterTitanEntity titan, ServerPlayer owner,
                                   Vec3 c, double r, float maxDamage, double knock, int fireTicks) {
        AABB box = new AABB(c.x - r, c.y - 8.0, c.z - r, c.x + r, c.y + r, c.z + r);
        DamageSource source = level.damageSources().explosion(titan, owner);

        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (isProtected(t, titan, owner)) continue;

            Vec3 flat = new Vec3(t.getX() - c.x, 0.0, t.getZ() - c.z);
            double dist = flat.length();
            if (dist > r) continue;

            double k = 1.0 - dist / r;
            k *= k;

            float dmg = (float) (maxDamage * k);
            if (dmg > 0.5F) t.hurt(source, dmg);
            if (fireTicks > 0) {
                t.setRemainingFireTicks(Math.max(t.getRemainingFireTicks(), fireTicks + (int) (160 * k)));
            }

            Vec3 away = dist > 0.01 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
            double kb = knock * k;
            t.setDeltaMovement(t.getDeltaMovement().add(away.x * kb, 0.6 * kb, away.z * kb));
            t.hurtMarked = true;
        }
    }

    private static void placeFire(ServerLevel level, ServerPlayer owner, double x, double z) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
        if (owner != null && owner.distanceToSqr(x, owner.getY(), z)
                < SAFE_RADIUS_AROUND_OWNER * SAFE_RADIUS_AROUND_OWNER) return;

        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!level.isLoaded(new BlockPos(bx, 64, bz))) return;

        int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        BlockPos pos = new BlockPos(bx, by, bz);
        BlockPos under = pos.below();
        if (level.isEmptyBlock(pos) && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
            BlockState fire = BaseFireBlock.getState(level, pos);
            if (!fire.isAir()) level.setBlock(pos, fire, 11);
        }
    }

    private static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z,
                                  int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : PlayerLookup.around(level, new Vec3(x, y, z), 192.0)) {
            level.sendParticles(sp, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private static void sound(ServerLevel level, SoundEvent sound, double x, double y, double z, float vol, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, vol, pitch);
    }

    private static void shake(ServerLevel level, Vec3 c) {
        ShiftShakePayload payload = new ShiftShakePayload(c.x, c.y, c.z);
        for (ServerPlayer other : level.players()) {
            ServerPlayNetworking.send(other, payload);
        }
    }
}
