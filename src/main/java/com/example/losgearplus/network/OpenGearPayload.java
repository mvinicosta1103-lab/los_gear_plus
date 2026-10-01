package com.example.losgearplus.network;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "abra minha aba Gear". */
public record OpenGearPayload() implements CustomPacketPayload {
    public static final Type<OpenGearPayload> TYPE = new Type<>(LosGearPlus.id("open_gear"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGearPayload> CODEC = StreamCodec.unit(new OpenGearPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}