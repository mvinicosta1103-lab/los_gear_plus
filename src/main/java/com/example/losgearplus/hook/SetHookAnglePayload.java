package com.example.losgearplus.hook;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "quero este ângulo". O servidor limita (0 a 180) e decide se vale. */
public record SetHookAnglePayload(int angle) implements CustomPacketPayload {
	public static final Type<SetHookAnglePayload> TYPE = new Type<>(LosGearPlus.id("set_hook_angle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SetHookAnglePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, SetHookAnglePayload::angle, SetHookAnglePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
