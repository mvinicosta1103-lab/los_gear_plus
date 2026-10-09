package com.example.losgearplus.partial;

import com.example.losgearplus.evap.TitanEvaporation;
import com.example.losgearplus.steam.SteamHealServer;
import daot.DannysAot;
import daot.DismountSmokeHelper;
import daot.ShifterMarkTracker;
import daot.ShifterTitan;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Titã parcial do shifter (Partial Shifting): o torso do titã emerge do chão com o shifter dentro, imóvel.
 *
 * <p>Integra com o resto do mod só por ser um {@link ShifterTitan} + {@link LivingEntity} que o jogador monta:
 * Steam Heal, regeneração de membros, maestria e o "esconder o jogador montado" já tratam qualquer
 * {@code ShifterTitan} montado. O que esta classe acrescenta:
 * <ul>
 *   <li>vida = fração da vida do titã completo (configurável) + barra de vida (boss bar) como a do DAOT;</li>
 *   <li>imóvel (sem movimento, empurrão ou controle), hitbox sólida: serve de muralha;</li>
 *   <li>o dano que o jogador montado receberia é absorvido por ela ({@code PartialRiderProtectionMixin});</li>
 *   <li>dano de contato em mobs hostis colados nela;</li>
 *   <li>sobe do chão ao surgir; K/O alterna entre dentro e emergido pela nuca (como o dismount do DAOT: o dono
 *       continua montado, porém visível, com vapor); emergido, o sneak sai de vez e o titã evapora, pelo
 *       {@link TitanEvaporation} do mod, como o titã normal;</li>
 *   <li>ao morrer só expulsa o dono (com a vida cheia) e evapora: o jogador não morre junto.</li>
 * </ul>
 */
public class PartialShifterTitanEntity extends PathfinderMob implements GeoEntity, ShifterTitan {
    /** Ativo: com o dono dentro OU fora (saída parcial). Evaporando: saída total, o TitanEvaporation do mod assume. */
    public static final int PHASE_ACTIVE = 0;
    public static final int PHASE_EVAPORATING = 1;

    private static final EntityDataAccessor<String> DATA_VARIANT =
            SynchedEntityData.defineId(PartialShifterTitanEntity.class, EntityDataSerializers.STRING);
    /** Emergido pela nuca (como o "dismounting" dos titãs do DAOT): jogador montado, porém visível e fora do corpo. */
    private static final EntityDataAccessor<Boolean> DATA_EMERGED =
            SynchedEntityData.defineId(PartialShifterTitanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(PartialShifterTitanEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.partial_titan.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent =
            new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private UUID ownerId;
    /** Só servidor: libera o sneak do dono (o DAOT chama isto de allowDismount). Ligado ao emergir e ao evaporar. */
    private boolean allowDismount;
    /** Só servidor: ticks até poder alternar de novo entre dentro e emergido (20 como no DAOT). */
    private int emergeCooldown;

    public PartialShifterTitanEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.noCulling = true;
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, PartialTitanVariant.ATTACK.id());
        builder.define(DATA_EMERGED, false);
        builder.define(DATA_PHASE, PHASE_ACTIVE);
    }

    // ---- dono / variante -------------------------------------------------------------------------------------

    /** Define o dono (único que pode montar) e ajusta vida e nome da barra. Chamar antes de adicionar ao mundo. */
    public void setOwner(ServerPlayer owner, PartialTitanVariant variant) {
        this.ownerId = owner.getUUID();
        this.entityData.set(DATA_VARIANT, variant.id());
        double max = variant.partialMaxHealth();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        this.setHealth((float) max);
        this.bossEvent.setName(Component.translatable("los_gear_plus.partial.bossbar",
                owner.getName(), Component.translatable(variant.nameKey())));
    }

    public boolean isOwnedBy(Entity entity) {
        return ownerId != null && entity != null && ownerId.equals(entity.getUUID());
    }

    public PartialTitanVariant getVariant() {
        return PartialTitanVariant.byId(this.entityData.get(DATA_VARIANT));
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    // ---- emergir / voltar / sair --------------------------------------------------------------------------

    public boolean isEmerged() {
        return this.entityData.get(DATA_EMERGED);
    }

    /** Usado pelo {@code PartialDismountVetoMixin}: o sneak do dono só funciona com ele emergido (igual ao DAOT). */
    public boolean isDismountAllowed() {
        return this.allowDismount || isEmerged();
    }

    /**
     * Tecla K/O: alterna entre dentro do titã e emergido pela nuca (jogador montado, visível, com vapor).
     * Emergido, o sneak sai de vez e o titã evapora.
     */
    public void toggleEmerge(ServerPlayer owner) {
        if (phase() != PHASE_ACTIVE || owner.getVehicle() != this) return;
        if (this.tickCount < PartialShiftConfig.RISE_TICKS) return; // ainda subindo do chão
        if (this.emergeCooldown > 0) return;
        this.emergeCooldown = PartialShiftConfig.EMERGE_COOLDOWN_TICKS;

        boolean emerge = !isEmerged();
        this.entityData.set(DATA_EMERGED, emerge);
        if (emerge) {
            if (!owner.hasEffect(MobEffects.INVISIBILITY)) owner.setInvisible(false);
            owner.displayClientMessage(Component.translatable("los_gear_plus.partial.emerged"), true);
        } else {
            owner.setInvisible(true);
            owner.displayClientMessage(Component.translatable("los_gear_plus.partial.reentered"), true);
        }
    }

    /** Vapor saindo da nuca enquanto o dono está emergido (o mesmo efeito de partícula do DAOT ao desmontar). */
    private void napeSteam(ServerLevel level) {
        if (this.tickCount % 2 != 0) return;
        double yaw = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw); // frente do titã
        double x = this.getX() + fx * PartialShiftConfig.EMERGE_FORWARD;
        double z = this.getZ() + fz * PartialShiftConfig.EMERGE_FORWARD;
        double y = this.getY() + PartialShiftConfig.EMERGE_Y + 0.8;
        level.sendParticles(DannysAot.PLAYER_DISMOUNT_PARTICLE, x, y, z, 3, 0.25, 0.25, 0.25, 0.02);
        if (this.tickCount % 10 == 0) {
            level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.15f, 1.0f);
        }
    }

    // ---- ShifterTitan (interface do DAOT) ------------------------------------------------------------------

    @Override
    public boolean isDismounting() {
        // Emergido ou evaporando: o jogador aparece (os mixins de esconder o rider checam isso), como no DAOT.
        return isEmerged() || phase() == PHASE_EVAPORATING;
    }

    /**
     * Enquanto ativo (dono dentro ou fora) devolve o dono; depois da saída total devolve null, exatamente como os
     * titãs do DAOT, e é isso que faz o {@link TitanEvaporation} começar a evaporar o corpo.
     */
    @Override
    public UUID getShifterUUID() {
        if (phase() != PHASE_ACTIVE) return null;
        return ownerId != null ? ownerId : new UUID(0L, 0L);
    }

    @Override
    public boolean isIncapacitated() {
        return false;
    }

    // ---- imóvel / sólido -----------------------------------------------------------------------------------

    @Override
    public void travel(Vec3 travelVector) {
        // imóvel: não anda, não cai, não desliza
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // sem empurrão
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // sem knockback
    }

    @Override
    public boolean canBeCollidedWith() {
        // sólida (muralha) enquanto ativa; evaporando deixa de colidir
        return phase() == PHASE_ACTIVE;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // vive só enquanto o dono está transformado
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL) || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.DROWN) || super.isInvulnerableTo(source);
    }

    // ---- montaria ------------------------------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && isOwnedBy(passenger) && phase() == PHASE_ACTIVE;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // Referencial local da entidade: +Z é a frente (a cabeça do modelo fica à frente).
        if (isEmerged()) {
            return new Vec3(0.0, PartialShiftConfig.EMERGE_Y, PartialShiftConfig.EMERGE_FORWARD); // pela nuca
        }
        return new Vec3(0.0, PartialShiftConfig.SEAT_Y, PartialShiftConfig.SEAT_FORWARD);
    }

    // ---- dano / morte --------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (phase() != PHASE_ACTIVE || this.tickCount < PartialShiftConfig.RISE_TICKS) return false; // subindo/evaporando
        if (isOwnedBy(source.getEntity())) return false; // o dono não fere o próprio titã
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || phase() != PHASE_ACTIVE) return;
        // Não morre de verdade (sem animação de morte do vanilla): vida mínima, expulsa o dono e evapora.
        this.setHealth(1.0f);
        beginEvaporate(owner((ServerLevel) this.level()));
    }

    // ---- ciclo de vida -------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            serverTick((ServerLevel) this.level());
        }
    }

    private ServerPlayer owner(ServerLevel level) {
        return ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
    }

    private void serverTick(ServerLevel level) {
        if (phase() != PHASE_ACTIVE) {
            this.bossEvent.removeAllPlayers();
            return; // evaporando: o TitanEvaporation do mod cuida da fumaça, do escurecimento e do discard
        }
        ServerPlayer owner = owner(level);
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            beginEvaporate(owner);
            return;
        }

        if (this.emergeCooldown > 0) this.emergeCooldown--;

        // O dono sempre fica montado. Se deixou de estar (sneak já emergido = saída total, ou teleporte/outro
        // motivo) depois do começo, é saída total: o titã evapora.
        if (owner.getVehicle() != this && this.tickCount > 10) {
            beginEvaporate(owner);
            return;
        }
        if (isEmerged()) napeSteam(level);

        if (this.tickCount < PartialShiftConfig.RISE_TICKS) {
            riseEffects(level);
        } else {
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(PartialShiftConfig.PASSIVE_HEAL_PER_TICK);
            }
            if (this.tickCount % PartialShiftConfig.CONTACT_INTERVAL_TICKS == 0) {
                contactDamage(level, owner);
            }
        }
        updateBossBar(level);
    }

    /** Terra voando e vapor na base enquanto sobe. */
    private void riseEffects(ServerLevel level) {
        if (this.tickCount % 2 != 0) return;
        BlockPos below = this.blockPosition().below();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
                this.getX(), this.getY() + 0.2, this.getZ(), 24, 1.6, 0.3, 1.6, 0.15);
        SteamHealServer.passiveSmoke(level, this);
    }

    private void contactDamage(ServerLevel level, ServerPlayer owner) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(PartialShiftConfig.CONTACT_REACH),
                e -> e != this && e != owner && e instanceof Enemy && !(e instanceof ShifterTitan));
        for (LivingEntity target : near) {
            target.hurt(level.damageSources().mobAttack(this), PartialShiftConfig.CONTACT_DAMAGE);
        }
    }

    private void updateBossBar(ServerLevel level) {
        this.bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));
        if (this.tickCount % 10 != 0) return;
        double rangeSqr = PartialShiftConfig.BOSSBAR_RANGE * PartialShiftConfig.BOSSBAR_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) <= rangeSqr) this.bossEvent.addPlayer(p);
            else this.bossEvent.removePlayer(p);
        }
    }

    /**
     * Saída total: devolve o dono ao chão (vida cheia: enquanto montado ele não foi ferido), registra a shifter mark
     * como "desmontou por completo" e deixa o corpo evaporar pelo {@link TitanEvaporation} (como o titã normal).
     */
    private void beginEvaporate(ServerPlayer owner) {
        if (this.level().isClientSide || phase() != PHASE_ACTIVE) return;
        this.entityData.set(DATA_PHASE, PHASE_EVAPORATING);
        this.addTag(TitanEvaporation.HAD_RIDER_TAG); // garante que o corpo conte como "já teve portador"
        this.entityData.set(DATA_EMERGED, false);
        this.allowDismount = true; // libera o stopRiding (o veto do mixin só vale enquanto não permitido)
        this.ejectPassengers();
        this.bossEvent.removeAllPlayers();
        PartialShiftManager.unbind(ownerId, this);

        if (owner != null && owner.isAlive() && owner.level() == this.level()) {
            if (!owner.hasEffect(MobEffects.INVISIBILITY)) owner.setInvisible(false);
            double yaw = Math.toRadians(this.getYRot());
            owner.teleportTo(this.getX() + Math.sin(yaw) * PartialShiftConfig.GROUND_EXIT_BACK, this.getY(),
                    this.getZ() - Math.cos(yaw) * PartialShiftConfig.GROUND_EXIT_BACK);
            owner.fallDistance = 0.0f;
            ShifterMarkTracker.markFullyDismounted(owner, this);
            DismountSmokeHelper.armFullDismount(owner.getUUID(), owner.getX(), owner.getY(), owner.getZ(),
                    owner.getServer().getTickCount());
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.allowDismount = true;
        this.bossEvent.removeAllPlayers();
        PartialShiftManager.unbind(ownerId, this);
        super.remove(reason);
    }

    // ---- visual (cliente) ----------------------------------------------------------------------------------

    /** 0 = totalmente enterrado, 1 = totalmente emergido. Só a subida do chão; o fim é a evaporação. */
    public float getRise(float partialTick) {
        float k = Mth.clamp((this.tickCount + partialTick) / PartialShiftConfig.RISE_TICKS, 0.0f, 1.0f);
        return k * k * (3.0f - 2.0f * k); // smoothstep
    }

    // ---- GeckoLib ------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::predicate));
    }

    private PlayState predicate(AnimationState<PartialShifterTitanEntity> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
