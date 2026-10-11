package com.example.losgearplus.weaker;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: forma escolhida (índice de {@link ShifterForm}; só NORMAL/WEAKER são gravados). -1 = pedido de abrir o menu (o servidor responde com a sincronização). */
public record ShifterFormPayload(int form) implements CustomPacketPayload {
    public static final Type<ShifterFormPayload> TYPE = new Type<>(LosGearPlus.id("shifter_form"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShifterFormPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ShifterFormPayload::form, ShifterFormPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
