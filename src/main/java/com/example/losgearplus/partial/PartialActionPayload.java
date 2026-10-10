package com.example.losgearplus.partial;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente -> servidor: ação de combate do titã parcial (só o Colossal usa por enquanto).
 * {@code action}: 0 = ataque (botão de ataque do mouse); 1..4 = número da habilidade, igual ao DAOT
 * (1 = vapor, 4 = calor infernal; 2 chute e 3 arrastar braço não existem no torso).
 */
public record PartialActionPayload(int action) implements CustomPacketPayload {
    public static final int ATTACK = 0;

    public static final Type<PartialActionPayload> TYPE = new Type<>(LosGearPlus.id("partial_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartialActionPayload> CODEC =
            StreamCodec.of(PartialActionPayload::write, PartialActionPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PartialActionPayload p) {
        buf.writeVarInt(p.action);
    }

    private static PartialActionPayload read(RegistryFriendlyByteBuf buf) {
        return new PartialActionPayload(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
