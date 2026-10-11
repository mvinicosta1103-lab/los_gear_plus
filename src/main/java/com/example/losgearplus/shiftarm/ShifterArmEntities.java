package com.example.losgearplus.shiftarm;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity type of the Shifter Arm (a non-saved follower of the owner, drawn with the female titan arm). */
public final class ShifterArmEntities {
    private ShifterArmEntities() {}

    public static final EntityType<ShifterArmEntity> SHIFTER_ARM = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            LosGearPlus.id("shifter_arm"),
            EntityType.Builder.<ShifterArmEntity>of(ShifterArmEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("shifter_arm"));

    /** Forces the class to load (registers the type). */
    public static void init() {}
}
