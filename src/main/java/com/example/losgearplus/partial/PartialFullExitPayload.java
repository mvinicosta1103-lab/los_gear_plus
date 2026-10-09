package com.example.losgearplus.partial;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: saída TOTAL do titã parcial (tecla O com sneak). Sem dados. */
public record PartialFullExitPayload() implements CustomPacketPayload {
    public static final Type<PartialFullExitPayload> TYPE = new Type<>(LosGearPlus.id("partial_full_exit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartialFullExitPayload> CODEC =
            StreamCodec.unit(new PartialFullExitPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
