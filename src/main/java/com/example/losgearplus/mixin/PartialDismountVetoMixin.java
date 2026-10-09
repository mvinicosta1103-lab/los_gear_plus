package com.example.losgearplus.mixin;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Igual ao {@code EntityDismountMixin} do DAOT para os titãs deles: o dono do titã parcial não consegue descer
 * (sneak) enquanto não estiver emergido pela nuca. Emergido, o sneak passa e vira a saída total.
 */
@Mixin(Entity.class)
public abstract class PartialDismountVetoMixin {
    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void los_gear_plus$vetoPartialDismount(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer p
                && p.getVehicle() instanceof PartialShifterTitanEntity titan
                && !titan.isDismountAllowed()) {
            ci.cancel();
        }
    }
}
