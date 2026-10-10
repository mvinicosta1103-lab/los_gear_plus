package com.example.losgearplus.partial;

import com.example.losgearplus.limb.LimbLookup;
import com.example.losgearplus.limb.LimbRules;
import com.example.losgearplus.limb.LimbState;
import daot.DannysAot;
import daot.ModConfig;
import daot.ModSounds;
import daot.ShifterTitan;
import daot.network.ShiftShakePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Colossal Titan parcial: torso gigante que emerge do chão e, ao contrário dos outros parciais, ataca e usa
 * habilidades do Colossal. Ao sair do partial shifting ou ser derrotado, desaba ({@link #PHASE_FALLING}): no impacto
 * causa dano em área e uma explosão de fogo; depois a fase passa para evaporação (o {@code TitanEvaporation} do mod
 * escurece e evapora o corpo como nos outros titãs).
 *
 * <p>Habilidades (mesma numeração do DAOT): 1 vapor, 4 calor infernal. 2 (chute) e 3 (arrastar braço) não existem
 * no parcial (sem pernas / sem animação) e só mostram um aviso.
 */
public class PartialColossalTitanEntity extends PartialShifterTitanEntity {
    public static final int ACTION_ATTACK = 0;

    /** 0 = nenhum, 1/2 = attack_1/attack_2 (sincronizado para o cliente escolher a animação). */
    private static final EntityDataAccessor<Integer> DATA_ATTACK =
            SynchedEntityData.defineId(PartialColossalTitanEntity.class, EntityDataSerializers.INT);

    /** Slot da barra de habilidades aceso (0 = vapor, 3 = calor infernal, -1 = nenhum). Só para o HUD do dono. */
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_SLOT =
            SynchedEntityData.defineId(PartialColossalTitanEntity.class, EntityDataSerializers.INT);
    /** Recargas restantes (ticks, em passos de 5) do vapor e do calor infernal, para o HUD. */
    private static final EntityDataAccessor<Integer> DATA_STEAM_CD =
            SynchedEntityData.defineId(PartialColossalTitanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INFERNAL_CD =
            SynchedEntityData.defineId(PartialColossalTitanEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    // thenPlayAndHold: o servidor zera DATA_ATTACK no fim; assim a animação nunca recomeça sozinha.
    private static final RawAnimation ATTACK1_ANIM = RawAnimation.begin().thenPlayAndHold("attack_1");
    private static final RawAnimation ATTACK2_ANIM = RawAnimation.begin().thenPlayAndHold("attack_2");
    private static final RawAnimation FALL_ANIM = RawAnimation.begin().thenPlayAndHold("fall");

    /** Só cliente: ticks desde que saiu da fase ativa (suaviza o deslocamento do modelo na queda). */
    private int exitClientTicks;

    // ---- só servidor ----
    private int attackLeft;
    private int attackElapsed;
    private int attackCooldown;
    private int lastAttack;
    private boolean impactDone;

    private int steamLeft;
    private int steamElapsed;
    private int steamCooldown;

    private boolean infernal;
    private int infernalLeft;
    private int infernalElapsed;
    private int infernalCooldown;

    private int fallTicks;

    public PartialColossalTitanEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK, 0);
        builder.define(DATA_ACTIVE_SLOT, -1);
        builder.define(DATA_STEAM_CD, 0);
        builder.define(DATA_INFERNAL_CD, 0);
    }

    public int activeAbilitySlot() {
        return this.entityData.get(DATA_ACTIVE_SLOT);
    }

    public int steamCooldownTicks() {
        return this.entityData.get(DATA_STEAM_CD);
    }

    public int infernalCooldownTicks() {
        return this.entityData.get(DATA_INFERNAL_CD);
    }

    /** Atualiza o que o HUD mostra; só escreve quando muda (e as recargas em passos de 5 ticks, para poupar rede). */
    private void syncHud() {
        int slot = this.steamLeft > 0 ? 0 : (this.infernal ? 3 : -1);
        if (this.entityData.get(DATA_ACTIVE_SLOT) != slot) this.entityData.set(DATA_ACTIVE_SLOT, slot);
        int steam = this.steamLeft > 0 ? 0 : (this.steamCooldown + 4) / 5 * 5;
        if (this.entityData.get(DATA_STEAM_CD) != steam) this.entityData.set(DATA_STEAM_CD, steam);
        int inf = this.infernal ? 0 : (this.infernalCooldown + 4) / 5 * 5;
        if (this.entityData.get(DATA_INFERNAL_CD) != inf) this.entityData.set(DATA_INFERNAL_CD, inf);
    }

    // ---- ganchos da variante ----------------------------------------------------------------------------------

    @Override
    protected Vec3 seatPoint() {
        return new Vec3(0.0, PartialShiftConfig.COLOSSAL_SEAT_Y, PartialShiftConfig.COLOSSAL_SEAT_FORWARD);
    }

    @Override
    protected Vec3 emergePoint() {
        return new Vec3(0.0, PartialShiftConfig.COLOSSAL_EMERGE_Y, PartialShiftConfig.COLOSSAL_EMERGE_FORWARD);
    }

    @Override
    public float riseDepth() {
        return PartialShiftConfig.COLOSSAL_RISE_DEPTH;
    }

    @Override
    public int riseTicks() {
        return PartialShiftConfig.COLOSSAL_RISE_TICKS;
    }

    @Override
    protected double groundExitBack() {
        return PartialShiftConfig.COLOSSAL_GROUND_EXIT_BACK;
    }

    @Override
    protected int exitPhase() {
        return PHASE_FALLING;
    }

    @Override
    protected RawAnimation idleAnimation() {
        return IDLE_ANIM;
    }

    // ---- animação / render -------------------------------------------------------------------------------------

    @Override
    protected PlayState predicate(AnimationState<PartialShifterTitanEntity> state) {
        if (phase() != PHASE_ACTIVE) return state.setAndContinue(FALL_ANIM);
        int attack = this.entityData.get(DATA_ATTACK);
        if (attack == 1) return state.setAndContinue(ATTACK1_ANIM);
        if (attack == 2) return state.setAndContinue(ATTACK2_ANIM);
        return state.setAndContinue(IDLE_ANIM);
    }

    /**
     * A animação "fall" foi feita com o corpo {@code COLOSSAL_FALL_BASE_LIFT_BLOCKS} acima da pose de repouso.
     * O renderer desce o modelo esse tanto, em rampa de 5 ticks (o mesmo tempo da transição da animação, para não dar
     * salto no começo da queda).
     */
    @Override
    public double modelYOffset(float partialTick) {
        if (phase() == PHASE_ACTIVE) return 0.0;
        float k = Mth.clamp((this.exitClientTicks + partialTick) / 5.0f, 0.0f, 1.0f);
        return -PartialShiftConfig.COLOSSAL_FALL_BASE_LIFT_BLOCKS * k;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && phase() != PHASE_ACTIVE) this.exitClientTicks++;
    }

    // ---- ações (ataque / habilidades) --------------------------------------------------------------------------

    /** Chamado pelo receiver do {@link PartialColossalActionPayload}. */
    public void handleAction(ServerPlayer owner, int action) {
        if (phase() != PHASE_ACTIVE || owner.getVehicle() != this || !isOwnedBy(owner)) return;
        if (this.tickCount < riseTicks() || isEmerged()) return; // subindo ou com o dono emergido (fora do corpo)
        switch (action) {
            case ACTION_ATTACK -> tryAttack(owner);
            case 1 -> trySteam(owner);
            case 2 -> owner.displayClientMessage(Component.translatable("los_gear_plus.partial.colossal.no_kick"), true);
            case 3 -> owner.displayClientMessage(Component.translatable("los_gear_plus.partial.colossal.no_armdrag"), true);
            case 4 -> toggleInfernal(owner);
            default -> { }
        }
    }

    private void tryAttack(ServerPlayer owner) {
        if (this.attackLeft > 0 || this.attackCooldown > 0 || this.steamLeft > 0) return;
        LimbState limbs = LimbLookup.riderState(owner);
        if (limbs != null && !LimbRules.titanArmsOk(limbs)) {
            owner.displayClientMessage(Component.translatable("los_gear_plus.limb.titan_no_arms"), true);
            return;
        }
        int next = this.lastAttack == 1 ? 2 : 1;
        this.lastAttack = next;
        int length = next == 1 ? PartialShiftConfig.COLOSSAL_ATTACK1_TICKS : PartialShiftConfig.COLOSSAL_ATTACK2_TICKS;
        this.attackLeft = length;
        this.attackElapsed = 0;
        this.impactDone = false;
        this.attackCooldown = length + PartialShiftConfig.COLOSSAL_ATTACK_EXTRA_COOLDOWN;
        this.entityData.set(DATA_ATTACK, next);
        playSound((ServerLevel) this.level(), ModSounds.ATTACK_TITAN_ROAR, 6.0f, 0.4f, 18.0);
    }

    private void trySteam(ServerPlayer owner) {
        if (this.steamLeft > 0 || this.attackLeft > 0 || this.infernal) return;
        if (this.steamCooldown > 0) {
            cooldownMessage(owner, this.steamCooldown);
            return;
        }
        this.steamLeft = PartialShiftConfig.COLOSSAL_STEAM_TICKS;
        this.steamElapsed = 0;
        this.steamCooldown = PartialShiftConfig.COLOSSAL_STEAM_COOLDOWN;
        playSound((ServerLevel) this.level(), ModSounds.STEAM_POOF, 8.0f, 0.5f, 20.0);
    }

    private void toggleInfernal(ServerPlayer owner) {
        if (this.infernal) {
            stopInfernal();
            return;
        }
        if (this.steamLeft > 0 || this.attackLeft > 0) return;
        if (this.infernalCooldown > 0) {
            cooldownMessage(owner, this.infernalCooldown);
            return;
        }
        this.infernal = true;
        this.infernalLeft = PartialShiftConfig.COLOSSAL_INFERNAL_MAX_TICKS;
        this.infernalElapsed = 0;
        playSound((ServerLevel) this.level(), ModSounds.ATTACK_TITAN_ROAR, 8.0f, 0.3f, 20.0);
    }

    private void stopInfernal() {
        this.infernal = false;
        this.infernalCooldown = PartialShiftConfig.COLOSSAL_INFERNAL_COOLDOWN;
    }

    private static void cooldownMessage(ServerPlayer owner, int ticks) {
        owner.displayClientMessage(Component.translatable("los_gear_plus.partial.cooldown", (ticks + 19) / 20), true);
    }

    @Override
    protected void tickActive(ServerLevel level, ServerPlayer owner) {
        if (this.attackCooldown > 0) this.attackCooldown--;
        if (this.steamCooldown > 0 && this.steamLeft <= 0) this.steamCooldown--;
        if (this.infernalCooldown > 0 && !this.infernal) this.infernalCooldown--;

        if (this.attackLeft > 0) {
            this.attackElapsed++;
            this.attackLeft--;
            int impact = this.lastAttack == 1
                    ? PartialShiftConfig.COLOSSAL_ATTACK1_IMPACT : PartialShiftConfig.COLOSSAL_ATTACK2_IMPACT;
            if (!this.impactDone && this.attackElapsed >= impact) {
                this.impactDone = true;
                attackImpact(level, owner);
            }
            if (this.attackLeft <= 0) this.entityData.set(DATA_ATTACK, 0);
        }

        if (this.steamLeft > 0) {
            this.steamLeft--;
            this.steamElapsed++;
            if (this.steamElapsed >= PartialShiftConfig.COLOSSAL_STEAM_WARMUP && this.tickCount % 2 == 0) {
                steamBurst(level, owner, false, PartialShiftConfig.COLOSSAL_STEAM_RADIUS);
            }
        }

        if (this.infernal) {
            this.infernalElapsed++;
            this.infernalLeft--;
            if (this.infernalElapsed >= PartialShiftConfig.COLOSSAL_INFERNAL_WARMUP && this.tickCount % 2 == 0) {
                steamBurst(level, owner, true, PartialShiftConfig.COLOSSAL_INFERNAL_RADIUS);
            }
            if (this.infernalLeft <= 0) stopInfernal();
        }
        syncHud();
    }

    // ---- golpe -------------------------------------------------------------------------------------------------

    private void attackImpact(ServerLevel level, ServerPlayer owner) {
        double range = PartialShiftConfig.COLOSSAL_ATTACK_RANGE;
        double yaw = Math.toRadians(this.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        AABB area = new AABB(getX() - range, getY() - 4.0, getZ() - range,
                getX() + range, getY() + getBbHeight(), getZ() + range);
        float damage = (float) (daotDouble("colossalTitanAttackDamage", 30.0)
                * PartialShiftConfig.COLOSSAL_ATTACK_DAMAGE_SCALE);
        DamageSource source = level.damageSources().mobAttack(this);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (isIgnored(target, owner)) continue;
            Vec3 flat = new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ());
            double dist = flat.length();
            if (dist > range) continue;
            if (dist > 0.01 && flat.normalize().dot(forward) <= 0.1) continue; // só o arco à frente
            if (hitNape(target, source)) continue;

            target.hurt(source, damage);
            Vec3 away = dist > 0.01 ? flat.normalize() : forward;
            target.setDeltaMovement(target.getDeltaMovement().add(
                    away.x * PartialShiftConfig.COLOSSAL_ATTACK_KNOCKBACK,
                    PartialShiftConfig.COLOSSAL_ATTACK_KNOCKBACK_UP,
                    away.z * PartialShiftConfig.COLOSSAL_ATTACK_KNOCKBACK));
            target.hurtMarked = true;
        }

        double cx = getX() + forward.x * 10.0, cz = getZ() + forward.z * 10.0;
        BlockPos below = BlockPos.containing(cx, getY() - 0.5, cz);
        sendParticles(level, new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
                cx, getY() + 0.5, cz, 120, 8.0, 1.0, 8.0, 0.3);
        playSound(level, ModSounds.FLESH_IMPACT_4, 8.0f, 0.5f, 24.0);
        playSound(level, ModSounds.BOOM, 6.0f, 0.8f, 24.0);
        shake(level, cx, getY(), cz);
    }

    /** Acertar a nuca de um titã puro mata o titã inteiro (como o DAOT faz com o golpe do Colossal). */
    private static boolean hitNape(LivingEntity target, DamageSource source) {
        LivingEntity parent = null;
        if (target instanceof daot.TitanNapeEntity n) parent = n.getParentTitan();
        else if (target instanceof daot.SmallTitanNapeEntity n) parent = n.getParentTitan();
        else if (target instanceof daot.SmallTitan2NapeEntity n) parent = n.getParentTitan();
        else if (target instanceof daot.FritzTitanNapeEntity n) parent = n.getParentTitan();
        else return false;
        if (parent != null && parent.isAlive()) parent.hurt(source, Float.MAX_VALUE);
        return true;
    }

    /** Quem nunca é atingido pelas habilidades/golpe: o dono, o próprio titã, os hitboxes do DAOT e quem monta titã. */
    private boolean isIgnored(LivingEntity target, ServerPlayer owner) {
        if (target == this || target == owner || target.isPassengerOfSameVehicle(this)) return true;
        // (outros shifters podem ser atingidos pelo golpe; steam/queda os poupam mais abaixo)
        if (target instanceof Player pl && pl.getVehicle() instanceof PartialShifterTitanEntity) return true;
        String n = target.getClass().getSimpleName();
        boolean napeHit = target instanceof daot.TitanNapeEntity || target instanceof daot.SmallTitanNapeEntity
                || target instanceof daot.SmallTitan2NapeEntity || target instanceof daot.FritzTitanNapeEntity;
        if (napeHit) return false;
        return n.endsWith("NapeEntity") || n.endsWith("EyeEntity") || n.endsWith("HandEntity");
    }

    // ---- vapor / calor infernal ----------------------------------------------------------------------------------

    private void steamBurst(ServerLevel level, ServerPlayer owner, boolean fire, double radius) {
        ParticleOptions steam = DannysAot.COLOSSAL_STEAM_PARTICLE;
        for (int i = 0; i < 24; i++) {
            double ang = this.random.nextDouble() * Math.PI * 2.0;
            double dist = 2.0 + this.random.nextDouble() * 8.0;
            double h = 2.0 + this.random.nextDouble() * (getBbHeight() - 4.0);
            sendParticles(level, steam, getX() + Math.cos(ang) * dist, getY() + h, getZ() + Math.sin(ang) * dist,
                    0, Math.cos(ang) * 0.9, 0.15, Math.sin(ang) * 0.9, 1.0);
        }
        if (fire) {
            sendParticles(level, ParticleTypes.FLAME, getX(), getY() + 6.0, getZ(), 40, 9.0, 4.0, 9.0, 0.08);
            sendParticles(level, ParticleTypes.LAVA, getX(), getY() + 4.0, getZ(), 12, 8.0, 2.0, 8.0, 0.0);
            for (int i = 0; i < 3; i++) {
                double ang = this.random.nextDouble() * Math.PI * 2.0;
                double dist = 6.0 + this.random.nextDouble() * (radius - 6.0);
                placeFire(level, getX() + Math.cos(ang) * dist, getZ() + Math.sin(ang) * dist);
            }
        }
        if (this.tickCount % 20 == 0) playSound(level, ModSounds.STEAM_POOF, 5.0f, 0.6f, 20.0);

        AABB area = new AABB(getX() - radius, getY() - 4.0, getZ() - radius,
                getX() + radius, getY() + getBbHeight() + 10.0, getZ() + radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (isIgnored(target, owner) || target instanceof ShifterTitan) continue;
            Vec3 flat = new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ());
            double dist = flat.length();
            if (dist > radius) continue;
            double k = (1.0 - dist / radius);
            Vec3 away = dist > 0.01 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
            target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.5 * k, 0.05 * k, away.z * 0.5 * k));
            target.hurtMarked = true;
            if (fire) {
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
            }
        }
    }

    // ---- queda ---------------------------------------------------------------------------------------------------

    @Override
    protected void onExit() {
        this.entityData.set(DATA_ATTACK, 0);
        this.attackLeft = 0;
        this.steamLeft = 0;
        this.infernal = false;
        this.fallTicks = 0;
        syncHud();
    }

    @Override
    protected void tickFalling(ServerLevel level) {
        this.fallTicks++;
        if (this.fallTicks == 1) {
            playSound(level, ModSounds.ATTACK_TITAN_ROAR, 10.0f, 0.3f, 24.0);
        }
        int impact = PartialShiftConfig.COLOSSAL_FALL_IMPACT_TICK;
        if (this.fallTicks < impact && this.fallTicks % 10 == 0) {
            // estrondos e poeira enquanto desaba
            BlockPos below = this.blockPosition().below();
            sendParticles(level, new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
                    getX(), getY() + 0.5, getZ(), 40, getBbWidth() * 0.6, 0.5, getBbWidth() * 0.6, 0.2);
            if (this.fallTicks % 30 == 0) shake(level, getX(), getY(), getZ());
        }
        if (this.fallTicks == impact) fallImpact(level);
        if (this.fallTicks > impact && this.fallTicks % 4 == 0) {
            // brasas e fumaça no local do impacto
            sendParticles(level, ParticleTypes.FLAME, getX(), getY() + 1.0, getZ(), 30, 12.0, 1.5, 12.0, 0.03);
            sendParticles(level, ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.0, getZ(), 30, 12.0, 3.0, 12.0, 0.02);
        }
        if (this.fallTicks >= PartialShiftConfig.COLOSSAL_FALL_TOTAL_TICKS) finishFall();
    }

    /** Impacto do corpo no chão: dano em área (queda quadrática com a distância), fogo, explosões e tremor. */
    private void fallImpact(ServerLevel level) {
        double r = PartialShiftConfig.COLOSSAL_FALL_RADIUS;
        Vec3 c = this.position();
        ServerPlayer owner = ownerPlayer(level);
        AABB box = new AABB(c.x - r, c.y - 8.0, c.z - r, c.x + r, c.y + r, c.z + r);
        DamageSource source = level.damageSources().explosion(this, owner);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == this || target == owner || target instanceof ShifterTitan) continue;
            String n = target.getClass().getSimpleName();
            if (n.endsWith("NapeEntity") || n.endsWith("EyeEntity") || n.endsWith("HandEntity")) continue;
            Vec3 flat = new Vec3(target.getX() - c.x, 0.0, target.getZ() - c.z);
            double dist = flat.length();
            if (dist > r) continue;
            double intensity = 1.0 - dist / r;
            intensity *= intensity;
            float damage = (float) (PartialShiftConfig.COLOSSAL_FALL_DAMAGE * intensity);
            if (damage > 0.5f) target.hurt(source, damage);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 40 + (int) (160 * intensity)));
            Vec3 away = dist > 0.01 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
            double k = PartialShiftConfig.COLOSSAL_FALL_KNOCKBACK * intensity;
            target.setDeltaMovement(target.getDeltaMovement().add(away.x * k, 0.6 * k, away.z * k));
            target.hurtMarked = true;
        }

        for (int i = 0; i < 14; i++) {
            double ang = this.random.nextDouble() * Math.PI * 2.0;
            double dist = this.random.nextDouble() * r * 0.6;
            sendParticles(level, ParticleTypes.EXPLOSION_EMITTER, c.x + Math.cos(ang) * dist, c.y + 1.0,
                    c.z + Math.sin(ang) * dist, 1, 0.0, 0.0, 0.0, 0.0);
        }
        sendParticles(level, ParticleTypes.FLAME, c.x, c.y + 2.0, c.z, 300, r * 0.4, 3.0, r * 0.4, 0.15);
        sendParticles(level, ParticleTypes.LAVA, c.x, c.y + 2.0, c.z, 100, r * 0.35, 2.0, r * 0.35, 0.0);
        sendParticles(level, ParticleTypes.LARGE_SMOKE, c.x, c.y + 3.0, c.z, 250, r * 0.4, 4.0, r * 0.4, 0.05);
        for (int i = 0; i < 70; i++) {
            double ang = this.random.nextDouble() * Math.PI * 2.0;
            double dist = this.random.nextDouble() * r * 0.85;
            placeFire(level, c.x + Math.cos(ang) * dist, c.z + Math.sin(ang) * dist);
        }
        playSound(level, ModSounds.BOOM, 14.0f, 0.5f, 40.0);
        playSound(level, ModSounds.FLESH_IMPACT_4, 12.0f, 0.4f, 40.0);
        shake(level, c.x, c.y, c.z);
    }

    // ---- utilidades ---------------------------------------------------------------------------------------------

    private void placeFire(ServerLevel level, double x, double z) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunkAt(new BlockPos(bx, 64, bz))) return;
        int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        BlockPos pos = new BlockPos(bx, by, bz);
        BlockPos under = pos.below();
        if (!level.isEmptyBlock(pos) || !level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) return;
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (!fire.isAir()) level.setBlock(pos, fire, 11);
    }

    /** Partículas visíveis de longe para todo jogador que acompanha o titã. */
    private void sendParticles(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                               double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : PlayerLookup.tracking(this)) {
            level.sendParticles(sp, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private void playSound(ServerLevel level, SoundEvent sound, float volume, float pitch, double heightAboveBase) {
        level.playSound(null, getX(), getY() + Math.min(heightAboveBase, getBbHeight()), getZ(), sound,
                SoundSource.HOSTILE, volume, pitch);
    }

    private void shake(ServerLevel level, double x, double y, double z) {
        ShiftShakePayload payload = new ShiftShakePayload(x, y, z);
        for (ServerPlayer other : level.players()) ServerPlayNetworking.send(other, payload);
    }

    private static double daotDouble(String field, double fallback) {
        try {
            Object cfg = ModConfig.get();
            return ((Number) cfg.getClass().getField(field).get(cfg)).doubleValue();
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
