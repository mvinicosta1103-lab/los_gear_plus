package com.example.losgearplus.partial;

import com.example.losgearplus.LosGearPlus;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registro do tipo de entidade do titã parcial. */
public final class PartialEntities {
    private PartialEntities() {}

    public static final EntityType<PartialShifterTitanEntity> PARTIAL_SHIFTER_TITAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            LosGearPlus.id("partial_shifter_titan"),
            EntityType.Builder.<PartialShifterTitanEntity>of(PartialShifterTitanEntity::new, MobCategory.MISC)
                    .sized(PartialShiftConfig.WIDTH, PartialShiftConfig.HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("partial_shifter_titan"));

    public static final EntityType<PartialColossalTitanEntity> PARTIAL_COLOSSAL_TITAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            LosGearPlus.id("partial_colossal_titan"),
            EntityType.Builder.<PartialColossalTitanEntity>of(PartialColossalTitanEntity::new, MobCategory.MISC)
                    .sized(PartialShiftConfig.COLOSSAL_WIDTH, PartialShiftConfig.COLOSSAL_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("partial_colossal_titan"));

    public static void init() {
        FabricDefaultAttributeRegistry.register(PARTIAL_COLOSSAL_TITAN, PartialShifterTitanEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PARTIAL_SHIFTER_TITAN, PartialShifterTitanEntity.createAttributes());
    }
}
