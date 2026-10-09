package com.example.losgearplus.partial;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: a tecla única do Partial Shifting (transformar / emergir / voltar para dentro). Sem dados. */
public record PartialShiftPayload() implements CustomPacketPayload {
    public static final Type<PartialShiftPayload> TYPE = new Type<>(LosGearPlus.id("partial_shift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartialShiftPayload> CODEC =
            StreamCodec.unit(new PartialShiftPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
