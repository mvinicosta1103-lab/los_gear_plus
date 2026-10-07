package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbData;
import com.example.losgearplus.limb.LimbLookup;
import com.example.losgearplus.limb.LimbRules;
import daot.ShifterTitan;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Perna inteira perdida (ou as duas canelas): não corre. Vale para o jogador e para o titã shifter dele (o
 * DAOT decide "correr" por {@code isSprinting()}). Nos dois lados, para a previsão do cliente bater.
 */
@Mixin(Entity.class)
public abstract class EntitySprintLimbMixin {
	@Inject(method = "isSprinting", at = @At("RETURN"), cancellable = true)
	private void los_gear_plus$noRun(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		Object self = this;
		if (self instanceof Player p) {
			if (LimbRules.cannotSprint(LimbData.of(p))) cir.setReturnValue(false);
		} else if (self instanceof ShifterTitan) {
			if (LimbRules.cannotSprint(LimbLookup.ofTitan((Entity) self))) cir.setReturnValue(false);
		}
	}
}
