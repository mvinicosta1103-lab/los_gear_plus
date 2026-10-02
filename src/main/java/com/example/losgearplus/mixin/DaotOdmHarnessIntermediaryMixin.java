package com.example.losgearplus.mixin;

import com.example.losgearplus.compat.UniformOdm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gêmea de {@link DaotOdmHarnessNamedMixin} para o jogo "de verdade" (nomes INTERMEDIARY: class_1309 = LivingEntity,
 * class_1799 = ItemStack). As strings abaixo são literais de propósito: o Loom não remapeia o que está em
 * {@code method = "..."} quando {@code remap = false}.
 */
@Mixin(targets = "daot.OdmHarness", remap = false)
public abstract class DaotOdmHarnessIntermediaryMixin {

	@Inject(method = "getGear(Lnet/minecraft/class_1309;)Lnet/minecraft/class_1799;",
			at = @At("RETURN"), cancellable = true, require = 1, remap = false)
	private static void losgearplus$uniformAsGear(LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack real = cir.getReturnValue();
		ItemStack resolved = UniformOdm.resolve(entity, real);
		if (resolved != real) cir.setReturnValue(resolved);
	}
}
