package com.example.losgearplus.shifter;

import daot.ShifterTitan;
import daot.network.ModNetworking;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Force Shifting: a shifter about to die is forced to transform (it uses DAOT's own
 * {@code ModNetworking.forceShiftPlayer}) and stays invincible, with yellow energy beams coming out of the eyes
 * and mouth, until the transformation is done.
 *
 * <pre>
 *  Requirements : shifter tag + Shifter Mastery level >= MIN_LEVEL + enabled by the player
 *                 + not already in titan form + cooldown over + transformations left (Cart Titan: always)
 *  Trigger      : a hit that would leave the player at or under HEALTH_THRESHOLD of the max HP
 *  Toggle       : /forceshift [on|off|toggle|status]  (any player) - stored as the player tag OFF_TAG
 *  Admin (op 2) : /forceshift on|off <players>        enable / disable for the given players
 *                 /forceshift reset [players]         clear cooldown, protection and used transformations
 *                 /forceshift infinite on|off [players]  no cooldown and no transformation limit (INFINITE_TAG)
 * </pre>
 * Tune the constants below to balance.
 */
public final class ShifterForceShift {
    public static final int MIN_LEVEL = 2;
    /** Fraction of max HP: a hit that leaves the player at or under this triggers the force shift. */
    public static final float HEALTH_THRESHOLD = 0.15f;
    /** Ticks before it can trigger again (3600 = 3 minutes). */
    public static final int COOLDOWN_TICKS = 3600;
    /** Safety limit for the invincibility if the titan never appears (300 = 15 s). */
    public static final int MAX_PROTECT_TICKS = 300;
    /** Extra invincibility after the titan appears (40 = 2 s). */
    public static final int GRACE_TICKS = 40;
    /** Player tag that means "force shifting disabled" (default is enabled). */
    public static final String OFF_TAG = "los_forceshift_off";
    /** Player tag that means "infinite force shifting": no cooldown and no transformation limit. */
    public static final String INFINITE_TAG = "los_forceshift_infinite";

    private static final DustParticleOptions YELLOW = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.1f), 1.3f);

    private static final Map<UUID, Integer> PROTECTED_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new HashMap<>();
    private static final Set<UUID> GRACE_APPLIED = new HashSet<>();

    private ShifterForceShift() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("forceshift")
                        .executes(ctx -> toggle(ctx.getSource()))
                        .then(Commands.literal("toggle").executes(ctx -> toggle(ctx.getSource())))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))

                        // /forceshift on | /forceshift on <players> (op)
                        .then(Commands.literal("on")
                                .executes(ctx -> set(ctx.getSource(), true))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> setFor(ctx, true))))
                        // /forceshift off | /forceshift off <players> (op)
                        .then(Commands.literal("off")
                                .executes(ctx -> set(ctx.getSource(), false))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> setFor(ctx, false))))

                        // /forceshift reset [players] (op)
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> reset(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))

                        // /forceshift infinite on|off [players] (op)
                        .then(Commands.literal("infinite")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("on")
                                        .executes(ctx -> infinite(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException()), true))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> infinite(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), true))))
                                .then(Commands.literal("off")
                                        .executes(ctx -> infinite(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException()), false))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> infinite(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), false)))))));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTickCount();
            Iterator<Map.Entry<UUID, Integer>> it = PROTECTED_UNTIL.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> e = it.next();
                UUID id = e.getKey();
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || !p.isAlive() || now >= e.getValue()) {
                    it.remove();
                    GRACE_APPLIED.remove(id);
                    continue;
                }
                if (p.getVehicle() instanceof ShifterTitan) {
                    // Transformed: keep the protection only for a short grace period.
                    if (GRACE_APPLIED.add(id)) e.setValue(Math.min(e.getValue(), now + GRACE_TICKS));
                } else {
                    effects(p, now);
                }
            }
        });
    }

    // ---- player preference -----------------------------------------------------------------------------------

    public static boolean isEnabled(ServerPlayer p) {
        return !p.getTags().contains(OFF_TAG);
    }

    public static boolean isInfinite(ServerPlayer p) {
        return p.getTags().contains(INFINITE_TAG);
    }

    private static boolean eligible(ServerPlayer p) {
        return ShifterTypes.isShifter(p) && ShifterMastery.getLevel(p.getUUID()) >= MIN_LEVEL;
    }

    private static int toggle(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        return set(src, !isEnabled(p));
    }

    private static int set(CommandSourceStack src, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (on) p.removeTag(OFF_TAG); else p.addTag(OFF_TAG);
        report(p);
        return on ? 1 : 0;
    }

    /** Admin: enable / disable force shifting for other players. */
    private static int setFor(CommandContext<CommandSourceStack> ctx, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            if (on) p.removeTag(OFF_TAG); else p.addTag(OFF_TAG);
            report(p);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Force Shifting " + (on ? "ON" : "OFF")
                + " for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    /** Admin: clears cooldown, protection and used transformations so it can trigger again right away. */
    private static int reset(CommandSourceStack src, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) {
            UUID id = p.getUUID();
            COOLDOWN_UNTIL.remove(id);
            PROTECTED_UNTIL.remove(id);
            GRACE_APPLIED.remove(id);
            ShifterMastery.resetCooldown(id);
            ShifterMasterySync.send(p, true);
            p.displayClientMessage(Component.literal("Force Shifting reset (cooldown cleared)."), true);
        }
        src.sendSuccess(() -> Component.literal("Force Shifting reset for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    /** Admin: infinite = no cooldown and no transformation limit. */
    private static int infinite(CommandSourceStack src, Collection<ServerPlayer> targets, boolean on) {
        for (ServerPlayer p : targets) {
            if (on) p.addTag(INFINITE_TAG); else p.removeTag(INFINITE_TAG);
            COOLDOWN_UNTIL.remove(p.getUUID());
            p.displayClientMessage(Component.literal("Force Shifting infinito: " + (on ? "ON" : "OFF")), true);
        }
        src.sendSuccess(() -> Component.literal("Infinite Force Shifting " + (on ? "ON" : "OFF")
                + " for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        report(p);
        return isEnabled(p) ? 1 : 0;
    }

    private static void report(ServerPlayer p) {
        String msg = "Force Shifting: " + (isEnabled(p) ? "ON" : "OFF") + (isInfinite(p) ? " (infinito)" : "");
        if (!ShifterTypes.isShifter(p)) {
            msg += " (you are not a shifter)";
        } else if (ShifterMastery.getLevel(p.getUUID()) < MIN_LEVEL) {
            msg += " (needs Shifter Mastery level " + MIN_LEVEL + ")";
        }
        p.displayClientMessage(Component.literal(msg), true);
        p.sendSystemMessage(Component.literal(msg));
    }

    // ---- trigger (called by ForceShiftMixin at the start of LivingEntity.hurt) --------------------------------

    /** @return true if the hit must be cancelled (player protected or force shift just triggered). */
    public static boolean onHurt(ServerPlayer p, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // /kill, void
        int now = p.getServer().getTickCount();
        UUID id = p.getUUID();

        Integer until = PROTECTED_UNTIL.get(id);
        if (until != null && now < until) return true; // invincible while transforming

        if (!canTrigger(p, now)) return false;
        if (p.getHealth() - amount > Math.max(1.0f, p.getMaxHealth() * HEALTH_THRESHOLD)) return false;

        trigger(p, now);
        return true;
    }

    private static boolean canTrigger(ServerPlayer p, int now) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator()) return false;
        if (!isEnabled(p) || !eligible(p)) return false;
        if (p.getVehicle() instanceof ShifterTitan) return false;
        Integer cd = COOLDOWN_UNTIL.get(p.getUUID());
        if (!isInfinite(p) && cd != null && now < cd) return false;
        return isInfinite(p) || ShifterMastery.canShift(p);
    }

    private static void trigger(ServerPlayer p, int now) {
        UUID id = p.getUUID();
        PROTECTED_UNTIL.put(id, now + MAX_PROTECT_TICKS);
        if (!isInfinite(p)) COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
        GRACE_APPLIED.remove(id);

        // Never stay at a hair of life in case something slips through.
        p.setHealth(Math.max(p.getHealth(), p.getMaxHealth() * 0.2f));

        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.2f);
        p.displayClientMessage(Component.literal("FORCE SHIFTING!"), true);

        // DAOT's own transformation routine (preshift effect, titan spawn, ...).
        ModNetworking.forceShiftPlayer(p);
    }

    // ---- yellow energy from the eyes and mouth -----------------------------------------------------------------

    private static void effects(ServerPlayer p, int now) {
        ServerLevel level = (ServerLevel) p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();

        Vec3 front = eye.add(look.scale(0.3));
        Vec3[] origins = {
                front.add(right.scale(0.14)),          // right eye
                front.add(right.scale(-0.14)),         // left eye
                front.add(0, -0.28, 0)                 // mouth
        };
        for (Vec3 o : origins) {
            for (int i = 0; i < 6; i++) {
                Vec3 pt = o.add(look.scale(i * 0.35));
                level.sendParticles(YELLOW, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            level.sendParticles(ParticleTypes.END_ROD, o.x, o.y, o.z, 1, 0.03, 0.03, 0.03, 0.02);
        }
        if (now % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), 6, 0.4, 0.7, 0.4, 0.15);
        }
    }
}