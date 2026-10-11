package com.example.losgearplus.colossal;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente. colossal: o jogador é shifter Colossal; nuke: modo atual (true = com nuke). */
public record ColossalNukeSyncPayload(boolean colossal, boolean nuke) implements CustomPacketPayload {
    public static final Type<ColossalNukeSyncPayload> TYPE = new Type<>(LosGearPlus.id("colossal_nuke_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColossalNukeSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ColossalNukeSyncPayload::colossal,
                    ByteBufCodecs.BOOL, ColossalNukeSyncPayload::nuke,
                    ColossalNukeSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
