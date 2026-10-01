package com.example.losgearplus.mode;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "quero alternar o ODMG Mode". Sem dados; o servidor decide. */
public record ToggleOdmgModePayload() implements CustomPacketPayload {
	public static final Type<ToggleOdmgModePayload> TYPE = new Type<>(LosGearPlus.id("toggle_odmg_mode"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleOdmgModePayload> CODEC =
			StreamCodec.unit(new ToggleOdmgModePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
