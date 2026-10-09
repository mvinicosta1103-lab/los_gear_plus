package com.example.losgearplus.partial;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "alterna o Partial Shifting" (transforma, ou desfaz se já estiver nele). Sem dados. */
public record PartialShiftPayload() implements CustomPacketPayload {
    public static final Type<PartialShiftPayload> TYPE = new Type<>(LosGearPlus.id("partial_shift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartialShiftPayload> CODEC =
            StreamCodec.unit(new PartialShiftPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
