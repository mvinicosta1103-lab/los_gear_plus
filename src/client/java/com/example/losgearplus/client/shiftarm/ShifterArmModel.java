package com.example.losgearplus.client.shiftarm;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.shiftarm.ShifterArmEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Geo + animations of this addon; the texture is the Female Titan's own, read from the DAOT mod at runtime. */
public class ShifterArmModel extends GeoModel<ShifterArmEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "textures/entity/femaletitan.png");

    @Override
    public ResourceLocation getModelResource(ShifterArmEntity entity) {
        return LosGearPlus.id("geo/shifter_arm.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShifterArmEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShifterArmEntity entity) {
        return LosGearPlus.id("animations/shifter_arm.animation.json");
    }
}
