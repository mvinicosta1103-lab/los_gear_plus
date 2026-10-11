package com.example.losgearplus.shiftarm;

import com.example.losgearplus.limb.LimbData;
import com.example.losgearplus.partial.PartialShiftManager;
import com.example.losgearplus.shifter.ShifterTypes;
import daot.ShifterTitan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shifter Arm, server side: key handling (on/off, punch/throw, grab) and the tracking of thrown beings so that
 * their landing hurts like a fall.
 *
 * <p>Any shifter (any titan, tags in {@link ShifterTypes}) can use it. It does not spend stamina and does not count
 * for the mastery system, like the Partial Shifting.
 */
public final class ShifterArmManager {
    private ShifterArmManager() {}

    private static final Map<UUID, ShifterArmEntity> ACTIVE = new HashMap<>();
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

    public static void init() {
        ShifterArmEntities.init();
        PayloadTypeRegistry.playC2S().register(ShifterArmActionPayload.TYPE, ShifterArmActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShifterArmActionPayload.TYPE,
                (payload, context) -> handle(context.player(), payload.action()));

        ServerTickEvents.END_SERVER_TICK.register(ShifterArmManager::tickThrown);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            ShifterArmEntity arm = ACTIVE.remove(id);
            if (arm != null) arm.discard();
            TOGGLE_COOLDOWN_UNTIL.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear();
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

    private static void toggle(ServerPlayer p) {
        ShifterArmEntity existing = ACTIVE.get(p.getUUID());
        if (existing != null && !existing.isRemoved()) {
            existing.discard();
            ACTIVE.remove(p.getUUID());
            msg(p, "los_gear_plus.shifter_arm.off");
            return;
        }
        if (!ShifterTypes.isShifter(p)) {
            msg(p, "los_gear_plus.shifter_arm.not_shifter");
            return;
        }
        if (p.getVehicle() instanceof ShifterTitan || PartialShiftManager.hasPartial(p.getUUID())) {
            msg(p, "los_gear_plus.shifter_arm.in_titan");
            return;
        }
        if (!LimbData.of(p).armUsable(false)) {
            msg(p, "los_gear_plus.shifter_arm.no_arm");
            return;
        }
        int now = p.getServer().getTickCount();
        Integer until = TOGGLE_COOLDOWN_UNTIL.get(p.getUUID());
        if (!p.isCreative() && until != null && now < until) {
            p.displayClientMessage(Component.translatable("los_gear_plus.shifter_arm.cooldown",
                    (until - now + 19) / 20), true);
            return;
        }
        ShifterArmEntity arm = ShifterArmEntities.SHIFTER_ARM.create(p.serverLevel());
        if (arm == null) return;
        arm.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
        arm.setOwner(p);
        p.serverLevel().addFreshEntity(arm);
        ACTIVE.put(p.getUUID(), arm);
        TOGGLE_COOLDOWN_UNTIL.put(p.getUUID(), now + ShifterArmConfig.TOGGLE_COOLDOWN_TICKS);
        msg(p, "los_gear_plus.shifter_arm.on");
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
            // flying fast into a wall hurts
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
