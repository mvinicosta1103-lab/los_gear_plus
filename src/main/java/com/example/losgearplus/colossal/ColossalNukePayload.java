package com.example.losgearplus.colossal;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor. mode: -1 = só consultar, 0 = transformação normal (sem nuke), 1 = com nuke. */
public record ColossalNukePayload(int mode) implements CustomPacketPayload {
    public static final Type<ColossalNukePayload> TYPE = new Type<>(LosGearPlus.id("colossal_nuke"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColossalNukePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ColossalNukePayload::mode, ColossalNukePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
