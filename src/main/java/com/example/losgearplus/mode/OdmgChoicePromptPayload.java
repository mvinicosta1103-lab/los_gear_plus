package com.example.losgearplus.mode;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: "abra a tela de seleção" (New ODM Gear com blades ou New ODM Uniform com pistolas). Sem dados. */
public record OdmgChoicePromptPayload() implements CustomPacketPayload {
	public static final Type<OdmgChoicePromptPayload> TYPE = new Type<>(LosGearPlus.id("odmg_choice_prompt"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OdmgChoicePromptPayload> CODEC =
			StreamCodec.unit(new OdmgChoicePromptPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
