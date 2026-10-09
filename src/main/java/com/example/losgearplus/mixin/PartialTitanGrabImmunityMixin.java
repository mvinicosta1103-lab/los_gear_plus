package com.example.losgearplus.mixin;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Os titãs puros do DAOT só deixam de agarrar/comer quem está imune ({@code TitanGrabImmunity.isImmune}) e só
 * agarram passageiros de veículos que não são do DAOT ({@code TitanReach.pluckable}, que olha o namespace). O titã
 * parcial é deste mod, então sem isto os titãs puros arrancariam o shifter de dentro dele. Aqui o jogador montado
 * no titã parcial passa a ser imune e não "arrancável".
 */
@Mixin(targets = "daot.TitanGrabImmunity", remap = false)
public abstract class PartialTitanGrabImmunityMixin {
    @Inject(method = "isImmune", at = @At("HEAD"), cancellable = true, remap = false)
    private static void losgearplus$riderIsImmune(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target != null && target.getVehicle() instanceof PartialShifterTitanEntity) {
            cir.setReturnValue(true);
        }
    }
}
