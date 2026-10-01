package com.example.losgearplus;

import com.example.losgearplus.gear.GearMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    private ModMenus() {}

    public static final MenuType<GearMenu> GEAR = Registry.register(
            BuiltInRegistries.MENU,
            LosGearPlus.id("gear"),
            new MenuType<>(GearMenu::new, FeatureFlags.VANILLA_SET));

    public static void init() {}
}