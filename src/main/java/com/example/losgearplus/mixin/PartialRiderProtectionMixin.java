package com.example.losgearplus.mixin;

import com.example.losgearplus.partial.PartialShiftManager;
import com.example.losgearplus.partial.PartialShifterTitanEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Jogador montado no titã parcial: o golpe vai para o titã (que é a muralha dele), não para o jogador.
 * Prioridade menor que a do ForceShiftMixin (500) para rodar antes dele.
 */
@Mixin(value = LivingEntity.class, priority = 400)
public abstract class PartialRiderProtectionMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void los_gear_plus$partialShield(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer p && p.getVehicle() instanceof PartialShifterTitanEntity titan) {
            if (PartialShiftManager.absorb(p, titan, source, amount)) cir.setReturnValue(false);
        }
    }
}
