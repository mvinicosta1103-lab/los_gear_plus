package com.example.losgearplus.client.shiftarm;

import com.example.losgearplus.shiftarm.ShifterArmConfig;
import com.example.losgearplus.shiftarm.ShifterArmEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShifterArmRenderer extends GeoEntityRenderer<ShifterArmEntity> {
    /** The giant arm would fill the screen from the owner's eyes, so it is hidden in first person. */
    private static final boolean HIDE_IN_FIRST_PERSON = true;

    public ShifterArmRenderer(EntityRendererProvider.Context context) {
        super(context, new ShifterArmModel());
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ShifterArmEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (HIDE_IN_FIRST_PERSON && mc.options.getCameraType().isFirstPerson() && entity.isOwnedBy(mc.player)
                && !isFarEvaporatingHusk(entity, mc)) {
            return;
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * After leaving the form the arm stays behind, evaporating. In first person it is still hidden while the owner
     * stands in it (it would fill the screen), but once the owner walks away it is drawn so the evaporation is seen.
     */
    private static boolean isFarEvaporatingHusk(ShifterArmEntity entity, Minecraft mc) {
        double d = ShifterArmConfig.EVAP_SHOW_IN_FIRST_PERSON_DISTANCE;
        return entity.isEvaporating() && mc.player != null && mc.player.distanceToSqr(entity) > d * d;
    }
}
