package com.example.losgearplus.client.partial;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renderer do titã parcial. A subida do chão / o afundar é só uma translação vertical do modelo inteiro. */
public class PartialShifterTitanRenderer extends GeoEntityRenderer<PartialShifterTitanEntity> {
    public PartialShifterTitanRenderer(EntityRendererProvider.Context context) {
        super(context, new PartialShifterTitanModel());
        this.shadowRadius = 2.5f;
    }

    @Override
    public void preRender(PoseStack poseStack, PartialShifterTitanEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        float rise = animatable.getRise(partialTick);
        poseStack.translate(0.0, -(1.0f - rise) * animatable.riseDepth() + animatable.modelYOffset(partialTick), 0.0);
    }
}
