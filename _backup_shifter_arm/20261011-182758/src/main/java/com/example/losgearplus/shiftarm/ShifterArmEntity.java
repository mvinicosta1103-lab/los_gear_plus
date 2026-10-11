package com.example.losgearplus.shiftarm;

import com.example.losgearplus.limb.LimbData;
import daot.ShifterTitan;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Shifter Arm (partial shift of the arm): the shifter stays visible and normal, and a giant titan arm is attached
 * to the right shoulder. It can punch, grab a being, and throw it far away (the landing hurts like a fall).
 *
 * <p>The entity only follows its owner (position + look direction) and carries the state machine; the animation is
 * driven by the synced state. All gameplay runs on the server.
 */
public class ShifterArmEntity extends Entity implements GeoEntity {
    public static final int IDLE = 0;
    public static final int PUNCH = 1;
    public static final int GRAB = 2;
    public static final int HOLDING = 3;
    public static final int THROW = 4;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(ShifterArmEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(ShifterArmEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final RawAnimation A_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation A_PUNCH = RawAnimation.begin().thenPlay("punch");
    private static final RawAnimation A_GRAB = RawAnimation.begin().thenPlay("grab");
    private static final RawAnimation A_HOLD = RawAnimation.begin().thenLoop("idle while holding");
    private static final RawAnimation A_THROW = RawAnimation.begin().thenPlay("throw");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // server only
    private int stateTicks;
    private int cooldown;
    private int holdTicks;
    private LivingEntity held;
    private boolean effectDone;

    public ShifterArmEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, IDLE);
        builder.define(DATA_OWNER, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    // ------------------------------------------------------------------ owner / state

    public void setOwner(ServerPlayer owner) {
        this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
    }

    public UUID ownerId() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public boolean isOwnedBy(Entity e) {
        UUID id = ownerId();
        return id != null && e != null && id.equals(e.getUUID());
    }

    public int armState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setState(int state) {
        this.entityData.set(DATA_STATE, state);
        this.stateTicks = 0;
        this.effectDone = false;
    }

    public boolean isHolding() {
        return armState() == HOLDING;
    }

    // ------------------------------------------------------------------ actions (called by the manager)

    /** Primary: punch; throws instead if a being is held. */
    public void primary(ServerPlayer owner) {
        if (cooldown > 0) return;
        int s = armState();
        if (s == HOLDING) {
            setState(THROW);
        } else if (s == IDLE) {
            setState(PUNCH);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.5f);
        }
    }

    /** Grab; releases (drops) the held being if already holding. */
    public void grab(ServerPlayer owner) {
        if (cooldown > 0) return;
        int s = armState();
        if (s == HOLDING) {
            releaseHeld();
            setState(IDLE);
            cooldown = ShifterArmConfig.ACTION_COOLDOWN_TICKS;
        } else if (s == IDLE) {
            setState(GRAB);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        ServerPlayer owner = ownerPlayer();
        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || owner.getVehicle() instanceof ShifterTitan
                || !LimbData.of(owner).armUsable(false)) {
            this.discard();
            return;
        }
        // follow: same feet position, facing where the owner looks (horizontally)
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        this.setYRot(owner.getYRot());
        this.setXRot(0.0f);

        stateTicks++;
        if (cooldown > 0) cooldown--;

        switch (armState()) {
            case PUNCH -> tickPunch(owner);
            case GRAB -> tickGrab(owner);
            case HOLDING -> tickHolding(owner);
            case THROW -> tickThrow(owner);
            default -> { }
        }
    }

    private void tickPunch(ServerPlayer owner) {
        if (!effectDone && stateTicks >= ShifterArmConfig.PUNCH_IMPACT_TICK) {
            effectDone = true;
            punchImpact(owner);
        }
        if (stateTicks >= ShifterArmConfig.PUNCH_TICKS) {
            setState(IDLE);
            cooldown = ShifterArmConfig.ACTION_COOLDOWN_TICKS;
        }
    }

    private void tickGrab(ServerPlayer owner) {
        if (!effectDone && stateTicks >= ShifterArmConfig.GRAB_CLOSE_TICK) {
            effectDone = true;
            LivingEntity target = findTargets(owner).stream().findFirst().orElse(null);
            if (target != null) {
                held = target;
                holdTicks = 0;
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
            }
        }
        if (stateTicks >= ShifterArmConfig.GRAB_TICKS) {
            if (held != null) {
                setState(HOLDING);
            } else {
                owner.displayClientMessage(Component.translatable("los_gear_plus.shifter_arm.nothing_to_grab"), true);
                setState(IDLE);
                cooldown = ShifterArmConfig.ACTION_COOLDOWN_TICKS;
            }
        }
        if (held != null) pullHeld(owner);
    }

    private void tickHolding(ServerPlayer owner) {
        if (!stillHolding(owner)) {
            releaseHeld();
            setState(IDLE);
            cooldown = ShifterArmConfig.ACTION_COOLDOWN_TICKS;
            return;
        }
        holdTicks++;
        pullHeld(owner);
    }

    private void tickThrow(ServerPlayer owner) {
        if (held != null && !stillHolding(owner)) held = null;
        if (held != null && stateTicks < ShifterArmConfig.THROW_RELEASE_TICK) pullHeld(owner);
        if (!effectDone && stateTicks >= ShifterArmConfig.THROW_RELEASE_TICK) {
            effectDone = true;
            if (held != null) {
                LivingEntity thrown = held;
                held = null;
                Vec3 look = owner.getLookAngle();
                Vec3 v = new Vec3(look.x, Math.max(look.y, 0.0), look.z).normalize()
                        .scale(ShifterArmConfig.THROW_SPEED).add(0.0, ShifterArmConfig.THROW_LIFT, 0.0);
                thrown.setDeltaMovement(v);
                thrown.hurtMarked = true;
                thrown.hasImpulse = true;
                ShifterArmManager.markThrown(thrown);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.5f, 0.5f);
            }
        }
        if (stateTicks >= ShifterArmConfig.THROW_TICKS) {
            setState(IDLE);
            cooldown = ShifterArmConfig.ACTION_COOLDOWN_TICKS;
        }
    }

    // ------------------------------------------------------------------ helpers

    private ServerPlayer ownerPlayer() {
        UUID id = ownerId();
        if (id == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(id);
    }

    private static Vec3 forward(Entity e) {
        double r = Math.toRadians(e.getYRot());
        return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
    }

    private static Vec3 right(Entity e) {
        double r = Math.toRadians(e.getYRot());
        return new Vec3(-Math.cos(r), 0.0, -Math.sin(r));
    }

    private Vec3 handPos(ServerPlayer owner) {
        return owner.position()
                .add(forward(owner).scale(ShifterArmConfig.HAND_FORWARD))
                .add(right(owner).scale(ShifterArmConfig.HAND_RIGHT))
                .add(0.0, ShifterArmConfig.HAND_UP, 0.0);
    }

    private AABB reachBox(ServerPlayer owner) {
        Vec3 c = owner.position().add(forward(owner).scale(ShifterArmConfig.REACH_FORWARD))
                .add(0.0, 1.0, 0.0);
        return new AABB(c, c).inflate(ShifterArmConfig.REACH_HALF_WIDTH, ShifterArmConfig.REACH_HALF_HEIGHT,
                ShifterArmConfig.REACH_HALF_WIDTH);
    }

    private boolean canHit(ServerPlayer owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator() || e instanceof ShifterTitan) return false;
        if (e.isPassengerOfSameVehicle(owner) || owner.isPassengerOfSameVehicle(e)) return false;
        return !(e instanceof Player p) || owner.canHarmPlayer(p);
    }

    /** Beings in the hit zone that can be grabbed, closest to the hand first. */
    private List<LivingEntity> findTargets(ServerPlayer owner) {
        Vec3 hand = handPos(owner);
        return level().getEntitiesOfClass(LivingEntity.class, reachBox(owner), e -> canHit(owner, e)
                        && e.getBbHeight() <= ShifterArmConfig.MAX_GRAB_HEIGHT
                        && (ShifterArmConfig.CAN_GRAB_PLAYERS || !(e instanceof Player)))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(hand))).toList();
    }

    private void punchImpact(ServerPlayer owner) {
        Vec3 fwd = forward(owner);
        boolean hit = false;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, reachBox(owner), x -> canHit(owner, x))) {
            DamageSource src = level().damageSources().playerAttack(owner);
            if (e.hurt(src, ShifterArmConfig.PUNCH_DAMAGE)) {
                e.setDeltaMovement(e.getDeltaMovement().add(
                        fwd.x * ShifterArmConfig.PUNCH_KNOCKBACK, ShifterArmConfig.PUNCH_LIFT,
                        fwd.z * ShifterArmConfig.PUNCH_KNOCKBACK));
                e.hurtMarked = true;
                hit = true;
            }
        }
        if (level() instanceof ServerLevel sl) {
            Vec3 p = handPos(owner);
            sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (hit) sl.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 24, 0.6, 0.6, 0.6, 0.3);
            sl.playSound(null, p.x, p.y, p.z, hit ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.PLAYER_ATTACK_WEAK,
                    SoundSource.PLAYERS, hit ? 0.7f : 1.0f, hit ? 1.4f : 0.6f);
        }
    }

    private boolean stillHolding(ServerPlayer owner) {
        return held != null && held.isAlive() && !held.isRemoved() && held.level() == level()
                && holdTicks < ShifterArmConfig.MAX_HOLD_TICKS
                && held.distanceTo(owner) <= ShifterArmConfig.MAX_HOLD_DISTANCE;
    }

    /** Pulls the held being to the hand with velocity (works smoothly for mobs and players). */
    private void pullHeld(ServerPlayer owner) {
        if (held == null) return;
        Vec3 hand = handPos(owner);
        Vec3 center = held.position().add(0.0, held.getBbHeight() * 0.5, 0.0);
        Vec3 d = hand.subtract(center);
        double len = d.length();
        Vec3 v = len > 1.0 ? d.scale(1.0 / len) : d.scale(0.6);
        held.setDeltaMovement(v);
        held.hurtMarked = true;
        held.fallDistance = 0.0f;
    }

    private void releaseHeld() {
        if (held != null) {
            held.setDeltaMovement(held.getDeltaMovement().scale(0.3));
            held.hurtMarked = true;
            held = null;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) releaseHeld();
        super.remove(reason);
    }

    // ------------------------------------------------------------------ entity flags

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(6.0);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "arm", 2, state -> switch (armState()) {
            case PUNCH -> state.setAndContinue(A_PUNCH);
            case GRAB -> state.setAndContinue(A_GRAB);
            case HOLDING -> state.setAndContinue(A_HOLD);
            case THROW -> state.setAndContinue(A_THROW);
            default -> state.setAndContinue(A_IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
