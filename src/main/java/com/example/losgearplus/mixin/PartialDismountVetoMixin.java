package com.example.losgearplus.mixin;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trava o dono dentro do titã parcial, como o {@code EntityDismountMixin} do DAOT faz com os titãs dele, só que mais
 * rígido: o shifter só sai se ELE quiser (agachar já emergido) ou se a barra de vida do titã acabar.
 * <ul>
 *   <li>{@code stopRiding}: vetado, a não ser que o titã permita (ver {@link PartialShifterTitanEntity#permitsDismount});</li>
 *   <li>{@code startRiding}: o dono não pode ser colocado em OUTRO veículo (a mão de um titã puro, a boca, etc.)
 *       enquanto estiver no titã parcial.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class PartialDismountVetoMixin {
    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void los_gear_plus$vetoPartialDismount(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer p
                && p.getVehicle() instanceof PartialShifterTitanEntity titan
                && !titan.permitsDismount(p)) {
            ci.cancel();
        }
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    private void los_gear_plus$noSwapVehicle(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer p
                && p.getVehicle() instanceof PartialShifterTitanEntity titan
                && titan.phase() == PartialShifterTitanEntity.PHASE_ACTIVE
                && vehicle != titan) {
            cir.setReturnValue(false);
        }
    }
}
