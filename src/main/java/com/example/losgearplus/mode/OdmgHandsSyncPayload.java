package com.example.losgearplus.mode;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: o jogador está em One-Hand (true) ou Two-Hand (false). */
public record OdmgHandsSyncPayload(boolean oneHand) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OdmgHandsSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(LosGearPlus.id("odmg_hands_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OdmgHandsSyncPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, OdmgHandsSyncPayload::oneHand, OdmgHandsSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}