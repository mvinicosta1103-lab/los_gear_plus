package com.example.losgearplus.mixin;

import com.example.losgearplus.weaker.WeakArmoredTitanEntity;
import daot.ArmoredTitanLegEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitbox da perna do Armored: no DAOT sempre devolve false. No Weaker o golpe vira "corte de tendão"
 * ({@link WeakArmoredTitanEntity#cutTendon}). Versão Intermediary (método "method_5643"). require = 0.
 */
@Mixin(targets = "daot.ArmoredTitanLegEntity", remap = false)
public abstract class DaotWeakLegIntermediaryMixin {
	@Inject(method = "method_5643", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void losgearplus$tendon(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (((ArmoredTitanLegEntity) (Object) this).getParentTitan() instanceof WeakArmoredTitanEntity weak) {
			cir.setReturnValue(weak.cutTendon(source, amount));
		}
	}
}
