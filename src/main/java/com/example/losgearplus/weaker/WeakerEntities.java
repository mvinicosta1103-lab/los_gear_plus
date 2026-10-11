package com.example.losgearplus.weaker;

import com.example.losgearplus.LosGearPlus;
import daot.ArmoredTitanEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registro do tipo de entidade do Armored Weaker (mesmas dimensões do Armored do DAOT). */
public final class WeakerEntities {
    private WeakerEntities() {}

    public static final EntityType<WeakArmoredTitanEntity> WEAK_ARMORED_TITAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            LosGearPlus.id("weak_armored_titan"),
            EntityType.Builder.<WeakArmoredTitanEntity>of(WeakArmoredTitanEntity::new, MobCategory.CREATURE)
                    .sized(2.3f, 15.0f)
                    .eyeHeight(14.0f)
                    .clientTrackingRange(32)
                    .build("weak_armored_titan"));

    public static void init() {
        FabricDefaultAttributeRegistry.register(WEAK_ARMORED_TITAN, ArmoredTitanEntity.createAttributes());
    }
}
