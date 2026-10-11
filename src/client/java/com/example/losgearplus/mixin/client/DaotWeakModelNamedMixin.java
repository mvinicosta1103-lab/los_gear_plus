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
 * Geo e textura do Armored Weaker no GeoModel do Armored do DAOT (as animações continuam as do DAOT).
 * Descritor completo por causa do método-ponte do GeckoLib. Versão Named; o plugin liga só a do ambiente.
 * require = 0: se falhar, o Weaker só aparece com o visual do Armored normal.
 */
@Mixin(targets = "daot.ArmoredTitanModel", remap = false)
public abstract class DaotWeakModelNamedMixin {
	@Inject(method = "getModelResource(Ldaot/ArmoredTitanEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true,
			remap = false, require = 0)
	private void losgearplus$weakGeo(ArmoredTitanEntity e, CallbackInfoReturnable<ResourceLocation> cir) {
		if (e instanceof WeakArmoredTitanEntity) cir.setReturnValue(LosGearPlus.id("geo/weak_armored_titan.geo.json"));
	}

	@Inject(method = "getTextureResource(Ldaot/ArmoredTitanEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true,
			remap = false, require = 0)
	private void losgearplus$weakTex(ArmoredTitanEntity e, CallbackInfoReturnable<ResourceLocation> cir) {
		if (e instanceof WeakArmoredTitanEntity) cir.setReturnValue(LosGearPlus.id("textures/entity/weak_armored_titan.png"));
	}
}
