package com.example.losgearplus.client.partial;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import com.example.losgearplus.partial.PartialTitanVariant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderer do titã parcial. A subida do chão / o afundar é só uma translação vertical do modelo inteiro. */
public class PartialShifterTitanRenderer extends GeoEntityRenderer<PartialShifterTitanEntity> {
    public PartialShifterTitanRenderer(EntityRendererProvider.Context context) {
        super(context, new PartialShifterTitanModel());
        this.shadowRadius = 2.5f;

        // Brilho do Colossal V2: o GeckoLib lê "<textura>_glowmask.png" ao lado da textura
        // (textures/entity/partial_titan_colossal_v2_glowmask.png). Só o V2 tem essa máscara, então a camada fica
        // desligada nas outras variantes; e apaga na evaporação para o corpo escurecer por inteiro.
        addRenderLayer(new AutoGlowingGeoLayer<PartialShifterTitanEntity>(this) {
            @Override
            public void render(PoseStack poseStack, PartialShifterTitanEntity animatable, BakedGeoModel bakedModel,
                               RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                               float partialTick, int packedLight, int packedOverlay) {
                if (animatable.getVariant() != PartialTitanVariant.COLOSSAL_V2
                        || animatable.phase() == PartialShifterTitanEntity.PHASE_EVAPORATING) {
                    return;
                }
                super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick,
                        packedLight, packedOverlay);
            }
        });
    }

    @Override
    public void preRender(PoseStack poseStack, PartialShifterTitanEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        float rise = animatable.getRise(partialTick);
        poseStack.translate(0.0, -(1.0f - rise) * animatable.riseDepth() + animatable.modelYOffset(partialTick), 0.0);
        float scale = animatable.modelScale(); // depois da translação: a profundidade da subida segue em blocos reais
        if (scale != 1.0f) poseStack.scale(scale, scale, scale);
    }
}
