package com.example.losgearplus.mode;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: a escolha da tela de seleção. guns = false: New ODM Gear com blades; true: New ODM Uniform com pistolas. */
public record OdmgChoicePayload(boolean guns) implements CustomPacketPayload {
	public static final Type<OdmgChoicePayload> TYPE = new Type<>(LosGearPlus.id("odmg_choice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OdmgChoicePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, OdmgChoicePayload::guns, OdmgChoicePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
