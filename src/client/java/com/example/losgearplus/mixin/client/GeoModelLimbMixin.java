package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.limb.TitanLimbBones;
import daot.ShifterTitan;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Gancho único para os models dos titãs shifters (todos estendem {@code GeoModel} e quase nenhum sobrescreve
 * {@code setCustomAnimations}). Roda no fim, depois das animações; sai logo para tudo que não for titã shifter.
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class GeoModelLimbMixin {
	@Inject(method = "setCustomAnimations", at = @At("TAIL"), remap = false)
	private void los_gear_plus$titanLimbs(GeoAnimatable animatable, long instanceId, AnimationState animationState,
			CallbackInfo ci) {
		if (animatable instanceof ShifterTitan && animatable instanceof Entity) {
			TitanLimbBones.apply((GeoModel<?>) (Object) this, (Entity) animatable, animationState.getPartialTick());
		}
	}
}
