package com.example.losgearplus.mixin;

import com.example.losgearplus.shifter.ShifterForceShift;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Low priority number = runs before DAOT's own force-shift hook, so this toggle/level system decides first. */
@Mixin(value = LivingEntity.class, priority = 500)
public abstract class ForceShiftMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void los_gear_plus$forceShift(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer p && !p.level().isClientSide) {
            if (ShifterForceShift.onHurt(p, source, amount)) cir.setReturnValue(false);
        }
    }
}