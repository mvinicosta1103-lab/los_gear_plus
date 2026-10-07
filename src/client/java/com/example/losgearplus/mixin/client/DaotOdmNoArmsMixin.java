package com.example.losgearplus.mixin.client;

import com.example.losgearplus.limb.LimbRules;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O hook do ODM é simulado no CLIENTE (o servidor só valida dano e sincroniza). O DAOT já recolhe os hooks e
 * avisa o servidor quando {@code isWearingODMGear} é falso, então usamos esse ponto: com pelo menos UM braço o
 * hook funciona normalmente; sem nenhum braço o ODM "não está vestido". Alvo só pelo NOME.
 */
@Mixin(targets = "daot.ODMTickHandler", remap = false)
public abstract class DaotOdmNoArmsMixin {
	@Inject(method = "isWearingODMGear", at = @At("RETURN"), cancellable = true, remap = false)
	private static void los_gear_plus$needAnArm(LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && LimbRules.noArms(player)) cir.setReturnValue(false);
	}
}
