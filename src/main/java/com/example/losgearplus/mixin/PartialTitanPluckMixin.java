package com.example.losgearplus.mixin;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quem está montado no titã parcial nunca é "arrancável" por um titã puro (ver {@link PartialTitanGrabImmunityMixin}). */
@Mixin(targets = "daot.TitanReach", remap = false)
public abstract class PartialTitanPluckMixin {
    @Inject(method = "pluckable", at = @At("HEAD"), cancellable = true, remap = false)
    private static void losgearplus$notPluckable(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target != null && target.getVehicle() instanceof PartialShifterTitanEntity) {
            cir.setReturnValue(false);
        }
    }
}
