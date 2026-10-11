package com.example.losgearplus;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** SoundEvents do mod (registrados para o servidor poder tocá-los para os jogadores próximos). */
public final class LosGearPlusSounds {

    /** Transformação do Colossal e variações. Ouvido até 128 blocos. */
    public static final SoundEvent TRANSFORMATION_COLOSSAL = register("transformation_colossal", 128.0F);

    /** Transformação dos demais titãs (15-17m). Ouvido até 64 blocos. */
    public static final SoundEvent TRANSFORMATION_STANDARD = register("transformation_standard", 64.0F);

    private LosGearPlusSounds() {}

    private static SoundEvent register(String name, float range) {
        ResourceLocation id = LosGearPlus.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    /** Só força o carregamento da classe (e portanto o registro). Chame no onInitialize. */
    public static void init() {}
}
