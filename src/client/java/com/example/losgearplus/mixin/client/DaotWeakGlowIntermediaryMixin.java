package com.example.losgearplus.mixin.client;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.weaker.WeakArmoredTitanEntity;
import daot.ArmoredTitanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Versão "Intermediary" do mixin de glow do Weak Armored Titan.
 * Idêntica à DaotWeakGlowNamedMixin: o LosGearPlusMixinPlugin decide qual das duas
 * é aplicada (nome terminando em "IntermediaryMixin" quando o runtime é intermediary,
 * "NamedMixin" caso contrário).
 */
@Environment(EnvType.CLIENT)
@Mixin(targets = "daot/ArmoredTitanRenderer$1", remap = false)
public class DaotWeakGlowIntermediaryMixin {

    @Inject(
            method = "getTextureResource(Ldaot/ArmoredTitanEntity;)Lnet/minecraft/class_2960;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void losgearplus$weakGlowTex(ArmoredTitanEntity entity,
                                         CallbackInfoReturnable<ResourceLocation> cir) {
        if (entity instanceof WeakArmoredTitanEntity) {
            cir.setReturnValue(LosGearPlus.id("textures/entity/weak_armored_titan.png"));
        }
    }
}