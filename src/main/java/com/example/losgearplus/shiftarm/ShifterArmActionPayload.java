package com.example.losgearplus.shiftarm;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server. action: 0 = turn the Shifter Arm on/off, 1 = primary (punch; throws if holding),
 * 2 = grab (releases if holding), 3 = exit (leave the arm transformation: it turns black and evaporates; sent by the
 * L key; the B key is DAOT's own transform key and is handled on the server). The server validates everything.
 */
public record ShifterArmActionPayload(int action) implements CustomPacketPayload {
    public static final int TOGGLE = 0;
    public static final int PRIMARY = 1;
    public static final int GRAB = 2;
    public static final int EXIT = 3;

    public static final Type<ShifterArmActionPayload> TYPE = new Type<>(LosGearPlus.id("shifter_arm_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShifterArmActionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ShifterArmActionPayload::action, ShifterArmActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
