package com.example.losgearplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "cn.blockforge.royalattire.item.OdmGearItem", remap = false)
public abstract class NewOdmGearGasMixin {

    @Shadow(remap = false) @Mutable
    private int maxGas;

    // O New ODM Gear é criado com 500 (MAX_GAS_ODM_GEAR).
    // O Rusty (300) e o Uniform (1000) não são afetados.
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void losgearplus$newOdmGear1000(CallbackInfo ci) {
        if (this.maxGas == 500) {
            this.maxGas = 1000;
        }
    }
}