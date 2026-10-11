package com.example.losgearplus.shiftarm;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.limb.LimbData;
import com.example.losgearplus.partial.PartialShiftManager;
import com.example.losgearplus.partial.PartialTitanVariant;
import com.example.losgearplus.shifter.ShifterTypes;
import daot.DismountSmokeHelper;
import daot.ShifterMarkTracker;
import daot.ShifterTitan;
import daot.network.ModNetworking;
import daot.network.PreshiftEffectPayload;
import daot.network.PreshiftStartPayload;
import daot.network.TitanSpawnPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Shifter Arm, server side. Picked in the shifter form menu (H) as "Arm": a partial transformation where only the
 * ARM transforms and the shifter stands still, controlling it.
 *
 * <p>Sequence (the same effects as the normal DAOT shift):
 * <ol>
 *   <li>pre-shift (DAOT payloads: particles, hand-bite / charge-up / bell sounds, blood) and the shifter is rooted;</li>
 *   <li>after {@link ShifterArmConfig#PRESHIFT_TICKS} ticks: lightning, ground particles, transformation blast + camera
 *       shake, the DAOT spawn effects (TitanSpawnPayload: sound, particles, roar of the variant), body smoke, a brief
 *       Resistance, and the shifter mark;</li>
 *   <li>the arm entity appears; pick "Arm" again (or die / enter a titan / leave) to retract it: steam like a dismount.</li>
 * </ol>
 * Any shifter (tags in {@link ShifterTypes}) can use it. No stamina cost and no mastery count, like Partial Shifting.
 */
public final class ShifterArmManager {
    private ShifterArmManager() {}

    private static final ResourceLocation ROOT_ID = LosGearPlus.id("shifter_arm_root");

    private static final Map<UUID, ShifterArmEntity> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> TOGGLE_COOLDOWN_UNTIL = new HashMap<>();
    private static final List<Thrown> THROWN = new ArrayList<>();

    private static final class Thrown {
        final LivingEntity entity;
        int age;
        double lastSpeed;

        Thrown(LivingEntity entity) {
            this.entity = entity;
        }
    }

    public static boolean hasArm(UUID owner) {
        ShifterArmEntity a = ACTIVE.get(owner);
        return a != null && !a.isRemoved();
    }

    /** Pre-shift running or arm out: blocks the normal shift, Force Shift and the mark fade (via hasPartial). */
    public static boolean isBusy(UUID owner) {
        return PENDING.containsKey(owner) || hasArm(owner);
    }

    public static void init() {
        ShifterArmEntities.init();
        PayloadTypeRegistry.playC2S().register(ShifterArmActionPayload.TYPE, ShifterArmActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShifterArmActionPayload.TYPE,
                (payload, context) -> handle(context.player(), payload.action()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickPending(server);
            tickThrown(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer p = handler.getPlayer();
            UUID id = p.getUUID();
            PENDING.remove(id);
            ShifterArmEntity arm = ACTIVE.get(id);
            if (arm != null) arm.discard();
            unroot(p);
            TOGGLE_COOLDOWN_UNTIL.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear();
            PENDING.clear();
            TOGGLE_COOLDOWN_UNTIL.clear();
            THROWN.clear();
        });
    }

    private static void handle(ServerPlayer p, int action) {
        if (!p.isAlive() || p.isSpectator()) return;
        if (action == ShifterArmActionPayload.TOGGLE) {
            toggle(p);
            return;
        }
        ShifterArmEntity arm = ACTIVE.get(p.getUUID());
        if (arm == null || arm.isRemoved()) return;
        if (action == ShifterArmActionPayload.PRIMARY) arm.primary(p);
        else if (action == ShifterArmActionPayload.GRAB) arm.grab(p);
    }

    // ------------------------------------------------------------------ on / off

    private static void toggle(ServerPlayer p) {
        UUID id = p.getUUID();
        if (PENDING.containsKey(id)) return; // already transforming
        ShifterArmEntity existing = ACTIVE.get(id);
        if (existing != null && !existing.isRemoved()) {
            existing.discard(); // onArmRemoved does the rest (steam, mark, unroot)
            msg(p, "los_gear_plus.shifter_arm.off");
            return;
        }
        if (!ShifterTypes.isShifter(p)) {
            msg(p, "los_gear_plus.shifter_arm.not_shifter");
            return;
        }
        if (p.getVehicle() != null || p.getVehicle() instanceof ShifterTitan || PartialShiftManager.hasPartialTitan(id)) {
            msg(p, "los_gear_plus.shifter_arm.in_titan");
            return;
        }
        if (!LimbData.of(p).armUsable(false)) {
            msg(p, "los_gear_plus.shifter_arm.no_arm");
            return;
        }
        int now = p.getServer().getTickCount();
        Integer until = TOGGLE_COOLDOWN_UNTIL.get(id);
        if (!p.isCreative() && until != null && now < until) {
            p.displayClientMessage(Component.translatable("los_gear_plus.shifter_arm.cooldown",
                    (until - now + 19) / 20), true);
            return;
        }
        startPreshift(p);
        TOGGLE_COOLDOWN_UNTIL.put(id, now + ShifterArmConfig.TOGGLE_COOLDOWN_TICKS);
    }

    /** Same pre-shift the DAOT does (tease flavour: particles + sounds, no titan cinematic), and the shifter is rooted. */
    private static void startPreshift(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        ServerPlayNetworking.send(p, new PreshiftStartPayload(false, true));
        PreshiftEffectPayload effect = new PreshiftEffectPayload(p.getId(), false);
        for (ServerPlayer nearby : new HashSet<>(PlayerLookup.tracking(p))) {
            if (nearby != p) ServerPlayNetworking.send(nearby, effect);
        }
        BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.sendParticles(blood, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.3, 0.4, 0.3, 0.1);
        level.sendParticles(blood, p.getX(), p.getY() + 1.5, p.getZ(), 20, 0.1, 0.1, 0.1, 0.15);
        root(p);
        PENDING.put(p.getUUID(), ShifterArmConfig.PRESHIFT_TICKS);
    }

    private static void tickPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || p.isSpectator() || p.getVehicle() != null) {
                it.remove();
                if (p != null) unroot(p);
                continue;
            }
            ShifterArmEntity.keepOwnerVisible(p); // also during the pre-shift
            int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            it.remove();
            spawnArm(p);
        }
    }

    /** The moment of the transformation: same effects as the DAOT spawn (and the repo's partial titan). */
    private static void spawnArm(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        PartialTitanVariant variant = PartialTitanVariant.of(p);
        ShifterArmEntity arm = ShifterArmEntities.SHIFTER_ARM.create(level);
        if (arm == null) {
            unroot(p);
            return;
        }
        double x = p.getX(), y = p.getY(), z = p.getZ();
        arm.moveTo(x, y, z, p.getYRot(), 0.0f);
        arm.setOwner(p, variant.id());
        level.addFreshEntity(arm);
        ACTIVE.put(p.getUUID(), arm);

        ModNetworking.spawnShiftLightning(level, x, y, z);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(p.blockPosition().below())),
                x, y + 0.2, z, 60, 2.0, 0.3, 2.0, 0.2);
        PartialShiftManager.transformationBlast(level, p, new Vec3(x, y, z));

        TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, arm.getId(), variant.spawnType());
        Set<ServerPlayer> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int) x, (int) y, (int) z)));
        recipients.add(p);
        for (ServerPlayer r : recipients) ServerPlayNetworking.send(r, spawnPayload);

        DismountSmokeHelper.onShifterSpawn(p); // body smoke around the shifter
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
        ShifterMarkTracker.markEntered(p, arm);
        ShifterArmEntity.keepOwnerVisible(p);
        p.displayClientMessage(Component.translatable("los_gear_plus.shifter_arm.on"), true);
    }

    /** Server-side cleanup when the arm entity goes away for any reason (retract, death, titan form, quit). */
    static void onArmRemoved(ShifterArmEntity arm) {
        UUID id = arm.ownerId();
        if (id == null) return;
        if (ACTIVE.get(id) != arm) return;
        ACTIVE.remove(id);
        MinecraftServer server = arm.getServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
        if (p == null) return;
        unroot(p);
        if (p.isAlive()) {
            ShifterMarkTracker.markFullyDismounted(p, arm);
            DismountSmokeHelper.armFullDismount(id, p.getX(), p.getY(), p.getZ(), server.getTickCount());
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.4, p.getZ(), 30, 0.5, 0.5, 0.5, 0.05);
        }
    }

    // ------------------------------------------------------------------ rooting (the shifter stands still)

    private static void root(ServerPlayer p) {
        unroot(p);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addTransientModifier(new AttributeModifier(ROOT_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.addTransientModifier(new AttributeModifier(ROOT_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void unroot(ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(ROOT_ID);
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(ROOT_ID);
    }

    private static void msg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key), true);
    }

    // ------------------------------------------------------------------ thrown beings

    /** Called by the arm when it lets go of a being with a throw. */
    public static void markThrown(LivingEntity e) {
        THROWN.removeIf(t -> t.entity == e);
        THROWN.add(new Thrown(e));
    }

    private static void tickThrown(MinecraftServer server) {
        if (THROWN.isEmpty()) return;
        Iterator<Thrown> it = THROWN.iterator();
        while (it.hasNext()) {
            Thrown t = it.next();
            LivingEntity e = t.entity;
            if (e.isRemoved() || !e.isAlive() || t.age > ShifterArmConfig.THROW_TRACK_MAX_TICKS) {
                it.remove();
                continue;
            }
            t.age++;
            boolean landed = t.age > 3 && (e.onGround() || e.isInWater() || e.isInLava());
            if (landed) {
                it.remove();
                continue;
            }
            if (t.age > 1 && e.horizontalCollision && t.lastSpeed > ShifterArmConfig.WALL_SLAM_SPEED) {
                e.hurt(e.level().damageSources().flyIntoWall(), ShifterArmConfig.WALL_SLAM_DAMAGE);
                t.lastSpeed = 0.0;
            }
            t.lastSpeed = Math.max(t.lastSpeed * 0.9, e.getDeltaMovement().horizontalDistance());
            // the vanilla fall damage on landing uses this distance (armor, Feather Falling, water... all apply)
            float want = Math.min(ShifterArmConfig.THROW_FALL_MAX, ShifterArmConfig.THROW_FALL_BONUS + t.age * 0.15f);
            if (e.fallDistance < want) e.fallDistance = want;
        }
    }
}
