package com.example.losgearplus.mixin;

import com.example.losgearplus.codex.stacks.CrystalTitans;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Titã cristalizado (Iron Skin) não solta o vapor da nuca nem o som de gás que o DAOT faz depois que o portador sai
 * ({@code NapeSmokeHelper.tick}). Só mexe em quem está cristalizado. {@code require = 0}: se o DAOT mudar o método,
 * nada quebra.
 */
@Mixin(targets = "daot.NapeSmokeHelper", remap = false)
public abstract class DaotCrystalNapeSmokeMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void los_gear_plus$noSmokeOnCrystal(LivingEntity shifter, Mob titan, boolean active, CallbackInfo ci) {
		if (shifter != null && CrystalTitans.isCrystal(shifter)) ci.cancel();
	}
}
