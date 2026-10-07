package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbData;
import com.example.losgearplus.limb.LimbRules;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Esquiva do shifter (humano ou titã) exige pernas: sem elas (ajoelhado/rastejando) não esquiva. */
@Mixin(targets = "daot.ShifterDodgeManager", remap = false)
public abstract class DaotDodgeLimbMixin {
	@Inject(method = "tryStart", at = @At("HEAD"), cancellable = true, remap = false)
	private static void los_gear_plus$noDodge(ServerPlayer player, int direction, CallbackInfo ci) {
		if (LimbRules.mustKneel(LimbData.of(player))) ci.cancel();
	}
}
