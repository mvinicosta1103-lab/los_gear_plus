package com.example.losgearplus.weaker;

import com.example.losgearplus.LosGearPlus;
import daot.ArmoredTitanEntity;
import daot.ShifterTitan;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Armored Titan "Weaker": é um {@link ArmoredTitanEntity} completo (mesmas animações, habilidades, grab, climb...)
 * mas com as pernas sem couraça (corre desde o início), a nuca sempre exposta (mixin da nuca), tendões das pernas
 * atacáveis ({@link #cutTendon}), menos vida e menos custo de stamina.
 */
public class WeakArmoredTitanEntity extends ArmoredTitanEntity {
    private static final ResourceLocation CRIPPLE_ID = LosGearPlus.id("tendon_cut");

    private boolean healthApplied;
    private int crippleTicks;
    private int lastTendonTick = -1000;

    public WeakArmoredTitanEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    /** Sem couraça nas pernas: libera correr (o DAOT só deixa o Armored correr com as pernas "quebradas"). */
    @Override
    public boolean isLegsShattered() {
        return true;
    }

    @Override
    public void setLegsShattered(boolean v) {
        // sempre expostas
    }

    @Override
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting && crippleTicks <= 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!healthApplied) {
            healthApplied = true; // o DAOT aplica a vida do Armored no 1º tick; aqui reduzimos logo depois
            double max = Math.max(20.0, getMaxHealth() * WeakerArmoredConfig.HEALTH_FRACTION);
            AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(max);
            setHealth((float) max);
        }

        UUID owner = getShifterUUID();
        if (owner != null) {
            if (isPlayerControlled()) WeakerArmoredManager.ACTIVE.add(owner);
            else WeakerArmoredManager.ACTIVE.remove(owner);
        }

        if (crippleTicks > 0 && --crippleTicks == 0) {
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(CRIPPLE_ID);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        UUID owner = getShifterUUID();
        if (owner != null) WeakerArmoredManager.ACTIVE.remove(owner);
    }

    /**
     * Golpe no hitbox da perna (chamado pelo mixin de {@code ArmoredTitanLegEntity}): corta o tendão. Tira uma
     * fração da vida (nunca mata), faz o titã mancar (sem correr, mais lento) e repassa o golpe ao sistema de
     * membros do addon (chance de decepar).
     */
    public boolean cutTendon(DamageSource source, float amount) {
        if (level().isClientSide || isDefeated() || isConsciousnessTransferActive() || isTransforming() || isDismounting()) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity) || attacker == this || attacker instanceof ShifterTitan) return false;
        if (attacker.getUUID().equals(getShifterUUID()) || attacker.getVehicle() == this) return false;
        if (tickCount - lastTendonTick < WeakerArmoredConfig.TENDON_COOLDOWN_TICKS) return false;
        lastTendonTick = tickCount;

        float dmg = Math.min(getMaxHealth() * WeakerArmoredConfig.TENDON_DAMAGE_FRACTION, getHealth() - 1.0f);
        if (dmg > 0.0f) setHealth(getHealth() - dmg);

        crippleTicks = WeakerArmoredConfig.CRIPPLE_TICKS;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CRIPPLE_ID);
            speed.addTransientModifier(new AttributeModifier(CRIPPLE_ID, WeakerArmoredConfig.CRIPPLE_SPEED,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        setSprinting(false);
        triggerHitReaction(attacker, false, false);
        hurt(source, amount); // passa pelo gancho de decepar membros (LimbDamage); o dano em si o DAOT ignora
        return true;
    }
}
