package com.example.losgearplus.client.partial;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.partial.PartialShifterTitanEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Aponta o GeckoLib para o geo/textura/animação do titã parcial (por variante, via {@code modelName()}). */
public class PartialShifterTitanModel extends GeoModel<PartialShifterTitanEntity> {
    @Override
    public ResourceLocation getModelResource(PartialShifterTitanEntity entity) {
        return LosGearPlus.id("geo/" + entity.getVariant().modelName() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PartialShifterTitanEntity entity) {
        return LosGearPlus.id("textures/entity/" + entity.getVariant().modelName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(PartialShifterTitanEntity entity) {
        return LosGearPlus.id("animations/" + entity.getVariant().animationFile());
    }
}
