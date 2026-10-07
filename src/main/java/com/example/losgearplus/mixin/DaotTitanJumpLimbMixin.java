package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbLookup;
import com.example.losgearplus.limb.LimbRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Titã ajoelhado (pernas cortadas) não pula. Cada shifter tem o seu {@code triggerJump(Player)}. */
@Mixin(targets = {
		"daot.AttackTitanEntity", "daot.ArmoredTitanEntity", "daot.WarhammerTitanEntity",
		"daot.FemaleTitanEntity", "daot.BeastTitanEntity" }, remap = false)
public abstract class DaotTitanJumpLimbMixin {
	@Inject(method = "triggerJump", at = @At("HEAD"), cancellable = true, remap = false)
	private void los_gear_plus$noJump(Player player, CallbackInfo ci) {
		if (LimbRules.mustKneel(LimbLookup.ofTitan((Entity) (Object) this))) ci.cancel();
	}
}
