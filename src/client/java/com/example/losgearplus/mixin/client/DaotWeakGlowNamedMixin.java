package com.example.losgearplus.mixin.client;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.weaker.WeakArmoredTitanEntity;
import daot.ArmoredTitanEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camada de brilho (olhos/vapor) do renderer do Armored: o DAOT aponta para a textura dele; no Weaker apontamos
 * para a nossa (o GeckoLib procura "weak_armored_titan_glowmask.png" ao lado). Alvo: classe anônima $1 do
 * ArmoredTitanRenderer. Versão Named. require = 0.
 */
@Mixin(targets = "daot.ArmoredTitanRenderer$1", remap = false)
public abstract class DaotWeakGlowNamedMixin {
	@Inject(method = "getTextureResource(Ldaot/ArmoredTitanEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true,
			remap = false, require = 0)
	private void losgearplus$weakGlowTex(ArmoredTitanEntity e, CallbackInfoReturnable<ResourceLocation> cir) {
		if (e instanceof WeakArmoredTitanEntity) cir.setReturnValue(LosGearPlus.id("textures/entity/weak_armored_titan.png"));
	}
}
