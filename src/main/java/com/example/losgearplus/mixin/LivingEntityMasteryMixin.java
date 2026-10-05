package com.example.losgearplus.mixin;

import com.example.losgearplus.shifter.ShifterMasteryStats;
import com.example.losgearplus.shifter.ShifterTypes;
import daot.ShifterTitan;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMasteryMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float los_gear_plus$mastery(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return amount;

        // Strength: damage dealt by a human shifter or by his titan
        Entity attacker = source.getEntity();
        UUID owner = null;
        if (attacker instanceof ServerPlayer sp && ShifterTypes.isShifter(sp)) owner = sp.getUUID();
        else if (attacker instanceof ShifterTitan st) owner = st.getShifterUUID();
        if (owner != null) amount *= ShifterMasteryStats.strengthMultiplier(owner);

        // Damage reduction: shifter in human form
        if (self instanceof ServerPlayer p) amount *= 1f - ShifterMasteryStats.damageReduction(p);
        return amount;
    }
}