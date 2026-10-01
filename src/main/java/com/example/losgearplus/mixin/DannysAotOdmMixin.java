package com.example.losgearplus.mixin;

import com.example.losgearplus.ModTags;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mesmo padrão do los_gear (OdmGearCompatMixin): o DAOT decide o que é "ODM gear"
 * em daot.DannysAot.isODMGear(Item); aqui acrescentamos os itens da tag
 * los_gear_plus:odm_gear.
 *
 * remap = false porque o alvo é código do DAOT (já vem com nomes próprios, não de Minecraft).
 *
 * ATENÇÃO: só "reconhecer" não basta para o item funcionar. Faltam os ganchos de gás
 * (getMaxGasForGear, getGasFromGear, setGasOnGear, consumeGasFromGear, gearHasGas),
 * listados no README.
 */
@Mixin(targets = "daot.DannysAot", remap = false)
public abstract class DannysAotOdmMixin {

	@SuppressWarnings("deprecation")
	@Inject(method = "isODMGear", at = @At("RETURN"), cancellable = true)
	private static void losgearplus$recognizeOdmGear(Item item, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && item.builtInRegistryHolder().is(ModTags.ODM_GEAR)) {
			cir.setReturnValue(true);
		}
	}
}
