package com.example.losgearplus.mode;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "troque o funcionamento" (New ODM Gear com blades <-> New ODM Uniform com pistolas). Sem dados. */
public record SwapOdmgKindPayload() implements CustomPacketPayload {
	public static final Type<SwapOdmgKindPayload> TYPE = new Type<>(LosGearPlus.id("swap_odmg_kind"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwapOdmgKindPayload> CODEC =
			StreamCodec.unit(new SwapOdmgKindPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
