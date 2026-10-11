package com.example.losgearplus.mixin;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O DAOT decide o tipo da shifter mark pela CLASSE do titã (instanceof). A entidade parcial não é de nenhuma delas,
 * então aqui devolvemos o tipo do titã completo equivalente ({@code PartialTitanVariant#markType}).
 */
@Mixin(targets = "daot.ShifterMarkTracker", remap = false)
public abstract class PartialMarkTypeMixin {
    @Inject(method = "getMarkType", at = @At("HEAD"), cancellable = true, remap = false)
    private static void losgearplus$partialMarkType(Entity entity, CallbackInfoReturnable<String> cir) {
        if (entity instanceof PartialShifterTitanEntity titan) {
            cir.setReturnValue(titan.getVariant().markType());
        }
    }
}
