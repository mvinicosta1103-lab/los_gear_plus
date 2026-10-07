package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbRules;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O golpe de lâmina do DAOT não passa pelos eventos do Fabric (chega por pacote), então é bloqueado aqui:
 * sem nenhum braço funcionando não há golpe. Alvo só pelo NOME ({@code startAttack} não tem sobrecarga).
 */
@Mixin(targets = "daot.BladeAttackTracker", remap = false)
public abstract class DaotBladeAttackLimbMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true, remap = false)
	private static void los_gear_plus$noArms(ServerPlayer player, int attackType, int chargeTimeTicks,
			float playerSpeed, CallbackInfo ci) {
		if (LimbRules.noArms(player)) ci.cancel();
	}
}
