package com.example.losgearplus.weaker;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: titã do portador atual (id da PartialTitanVariant; vazio = não é shifter), se a opção Weaker
 * existe para ele (só o Armored) e a forma atual. É a resposta ao pedido de abrir o menu.
 */
public record ShifterFormSyncPayload(String titanId, boolean canWeaker, int form) implements CustomPacketPayload {
    public static final Type<ShifterFormSyncPayload> TYPE = new Type<>(LosGearPlus.id("shifter_form_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShifterFormSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShifterFormSyncPayload::titanId,
            ByteBufCodecs.BOOL, ShifterFormSyncPayload::canWeaker,
            ByteBufCodecs.VAR_INT, ShifterFormSyncPayload::form,
            ShifterFormSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
