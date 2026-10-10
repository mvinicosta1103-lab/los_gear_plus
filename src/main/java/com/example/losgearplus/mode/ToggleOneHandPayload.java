package com.example.losgearplus.mode;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: alterna entre One-Hand e Two-Hand. Sem dados. */
public record ToggleOneHandPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleOneHandPayload> TYPE =
            new CustomPacketPayload.Type<>(LosGearPlus.id("toggle_one_hand"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleOneHandPayload> CODEC =
            StreamCodec.unit(new ToggleOneHandPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}