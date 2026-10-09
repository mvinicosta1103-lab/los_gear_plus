package com.example.losgearplus.partial;

import com.example.losgearplus.shifter.ShifterTypes;
import daot.ModSounds;
import daot.ShifterTitan;
import daot.network.ModNetworking;
import daot.network.ShiftShakePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Partial Shifting, lado servidor: recebe a tecla, valida, cria o titã parcial e aplica o "dano de transformação".
 *
 * <p>Qualquer jogador com tag de shifter ({@link ShifterTypes}) pode usar, de qualquer tipo (cart, jaw, female...).
 * A tecla alterna: transforma; apertando de novo (ou agachando) volta ao normal.
 */
public final class PartialShiftManager {
    private PartialShiftManager() {}

    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new HashMap<>();

    public static void init() {
        PayloadTypeRegistry.playC2S().register(PartialShiftPayload.TYPE, PartialShiftPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PartialShiftPayload.TYPE,
                (payload, context) -> toggle(context.player()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                COOLDOWN_UNTIL.remove(handler.getPlayer().getUUID()));
    }

    private static void toggle(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator()) return;

        Entity vehicle = p.getVehicle();
        if (vehicle instanceof PartialShifterTitanEntity titan && titan.isOwnedBy(p)) {
            titan.requestDismount();
            return;
        }
        if (!ShifterTypes.isShifter(p)) {
            msg(p, "los_gear_plus.partial.not_shifter");
            return;
        }
        if (vehicle != null) {
            msg(p, vehicle instanceof ShifterTitan ? "los_gear_plus.partial.already_titan"
                    : "los_gear_plus.partial.dismount_first");
            return;
        }
        if (!p.onGround()) {
            msg(p, "los_gear_plus.partial.need_ground");
            return;
        }
        int now = p.getServer().getTickCount();
        Integer until = COOLDOWN_UNTIL.get(p.getUUID());
        if (!p.isCreative() && until != null && now < until) {
            p.displayClientMessage(Component.translatable("los_gear_plus.partial.cooldown",
                    (until - now + 19) / 20), true);
            return;
        }
        spawn(p, PartialTitanVariant.of(p));
        COOLDOWN_UNTIL.put(p.getUUID(), now + PartialShiftConfig.COOLDOWN_TICKS);
    }

    private static void msg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key), true);
    }

    private static void spawn(ServerPlayer p, PartialTitanVariant variant) {
        ServerLevel level = p.serverLevel();
        PartialShifterTitanEntity titan = PartialEntities.PARTIAL_SHIFTER_TITAN.create(level);
        if (titan == null) return;

        double x = p.getX(), y = p.getY(), z = p.getZ();
        titan.moveTo(x, y, z, p.getYRot(), 0.0f);
        titan.setYBodyRot(p.getYRot());
        titan.setYHeadRot(p.getYRot());
        titan.setOwner(p, variant);
        level.addFreshEntity(titan);

        p.startRiding(titan, true);
        p.setInvisible(true); // igual ao DAOT; o cliente também esconde quem monta um ShifterTitan

        // Efeitos de transformação: raio, som, explosão, tremor de tela.
        ModNetworking.spawnShiftLightning(level, x, y, z);
        level.playSound(null, x, y, z, ModSounds.TITAN_SHIFT, SoundSource.PLAYERS, 3.0f, 1.2f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(p.blockPosition().below())),
                x, y + 0.2, z, 60, 2.0, 0.3, 2.0, 0.2);
        transformationBlast(level, p, new Vec3(x, y, z));
        ShiftShakePayload shake = new ShiftShakePayload(x, y, z);
        for (ServerPlayer other : level.players()) {
            ServerPlayNetworking.send(other, shake);
        }
        p.displayClientMessage(Component.translatable("los_gear_plus.partial.on",
                Component.translatable(variant.nameKey())), true);
    }

    /**
     * "Dano de transformação": o mesmo cálculo do DAOT (dano com queda quadrática pela distância + empurrão),
     * que é privado lá e por isso replicado aqui. Poupa o próprio shifter e qualquer ShifterTitan / quem o monta.
     */
    private static void transformationBlast(ServerLevel level, ServerPlayer shifter, Vec3 center) {
        double r = PartialShiftConfig.EXPLOSION_RADIUS;
        AABB box = new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == shifter || target instanceof ShifterTitan) continue;
            if (target instanceof Player pl && pl.getVehicle() instanceof ShifterTitan) continue;
            double distance = target.position().distanceTo(center);
            if (distance > r) continue;

            double intensity = 1.0 - distance / r;
            intensity *= intensity;
            float damage = (float) (PartialShiftConfig.EXPLOSION_DAMAGE * intensity);
            if (damage > 0.0f) {
                target.hurt(level.damageSources().explosion(null, shifter), damage);
            }
            Vec3 away = target.position().subtract(center);
            away = away.lengthSqr() > 0.001 ? away.normalize()
                    : new Vec3(level.random.nextDouble() - 0.5, 0.5, level.random.nextDouble() - 0.5).normalize();
            double k = PartialShiftConfig.EXPLOSION_KNOCKBACK * intensity;
            target.setDeltaMovement(target.getDeltaMovement().add(away.x * k, away.y * k * 0.5, away.z * k));
            target.hurtMarked = true;
        }
    }

    /**
     * Chamado pelo mixin de dano: o jogador montado no titã parcial não leva o golpe, quem leva é o titã.
     * (O mixin do DAOT só protege uma lista fixa de classes de titã, por isso isto é necessário.)
     *
     * @return true se o golpe deve ser cancelado para o jogador
     */
    public static boolean absorb(ServerPlayer rider, PartialShifterTitanEntity titan, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // /kill, void
        if (!titan.isOwnedBy(rider)) return false;
        if (titan.phase() == PartialShifterTitanEntity.PHASE_ACTIVE) {
            titan.hurt(source, amount);
        }
        return true;
    }
}
