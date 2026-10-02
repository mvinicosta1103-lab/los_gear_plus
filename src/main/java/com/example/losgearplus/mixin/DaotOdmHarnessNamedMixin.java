package com.example.losgearplus.mixin;

import com.example.losgearplus.compat.UniformOdm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * New ODM Uniform como Anti-Personnel ODM, versão para nomes MOJANG (ambiente de desenvolvimento: runClient).
 * A lógica está em {@link UniformOdm}. Em produção (nomes intermediary) vale a gêmea
 * {@link DaotOdmHarnessIntermediaryMixin}; {@link LosGearPlusMixinPlugin} liga só uma das duas.
 *
 * {@code OdmHarness.getGear} tem duas sobrecargas (LivingEntity e ItemStack), por isso o descritor completo.
 * Todo o DAOT consulta o ODM por este método (HUD, hooks, gás, boost, sons, rede...).
 */
@Mixin(targets = "daot.OdmHarness", remap = false)
public abstract class DaotOdmHarnessNamedMixin {

	@Inject(method = "getGear(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("RETURN"), cancellable = true, require = 1, remap = false)
	private static void losgearplus$uniformAsGear(LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack real = cir.getReturnValue();
		ItemStack resolved = UniformOdm.resolve(entity, real);
		if (resolved != real) cir.setReturnValue(resolved);
	}
}
