package com.example.losgearplus.mixin;

import com.example.losgearplus.codex.stacks.CrystalTitans;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Titã cristalizado (Iron Skin) não solta a fumaça nem o chiado que o DAOT faz no corpo do titã que ficou sem
 * portador ({@code DismountSmokeHelper.spawnShifterEffect}: fumaça preta + som de fogo apagando). Só mexe em quem
 * está cristalizado; os outros titãs continuam iguais. {@code require = 0}: se o DAOT mudar o método, nada quebra.
 */
@Mixin(targets = "daot.DismountSmokeHelper", remap = false)
public abstract class DaotCrystalDismountSmokeMixin {
	@Inject(method = "spawnShifterEffect", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void los_gear_plus$noSmokeOnCrystal(Entity titan, int remaining, float range, CallbackInfo ci) {
		if (titan != null && CrystalTitans.isCrystal(titan)) ci.cancel();
	}
}
