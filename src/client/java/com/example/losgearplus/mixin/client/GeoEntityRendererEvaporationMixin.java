package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.evap.EvaporationClient;
import com.example.losgearplus.evap.EvaporationRules;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Escurece (multiplica o RGB) o corpo do titã que está evaporando. Qualquer renderer GeckoLib de entidade passa por aqui. */
@Mixin(value = GeoEntityRenderer.class, remap = false)
public abstract class GeoEntityRendererEvaporationMixin {
	@Inject(method = "actuallyRender", at = @At("HEAD"), remap = false, require = 0)
	private void los_gear_plus$evaporate(PoseStack poseStack, Entity animatable, BakedGeoModel model, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour, CallbackInfo ci, @Local(argsOnly = true, ordinal = 2) LocalIntRef colourRef) {
		float d = EvaporationClient.darkness(animatable, partialTick);
		if (d > 0f) colourRef.set(EvaporationRules.shade(colourRef.get(), d));
	}
}
