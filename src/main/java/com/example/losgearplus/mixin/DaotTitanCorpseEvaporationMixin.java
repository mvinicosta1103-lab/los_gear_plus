package com.example.losgearplus.mixin;

import com.example.losgearplus.evap.TitanEvaporation;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Titã puro morto: o DAOT remove o corpo em {@code TitanBody.tickCorpse(Mob)} (todo tick de morte, nas 7 classes de
 * titã puro). Aqui trocamos isso pela evaporação (preto + fumaça + some), ignorando "corpo persiste" da config.
 * Alvo só pelo NOME do método (único em TitanBody), como nos outros mixins do DAOT.
 */
@Mixin(targets = "daot.TitanBody", remap = false)
public abstract class DaotTitanCorpseEvaporationMixin {
	@Inject(method = "tickCorpse", at = @At("HEAD"), cancellable = true, remap = false)
	private static void los_gear_plus$evaporate(Mob titan, CallbackInfo ci) {
		TitanEvaporation.tickPureCorpse(titan);
		ci.cancel();
	}
}
