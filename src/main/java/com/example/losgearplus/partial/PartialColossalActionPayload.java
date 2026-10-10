package com.example.losgearplus.partial;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente -> servidor: ação do Colossal parcial montado. 0 = ataque; 1..4 = habilidades (mesma numeração do DAOT:
 * 1 vapor, 2 chute, 3 arrastar braço, 4 calor infernal). O servidor valida tudo (montado, dono, fase, recarga).
 */
public record PartialColossalActionPayload(int action) implements CustomPacketPayload {
	public static final Type<PartialColossalActionPayload> TYPE = new Type<>(LosGearPlus.id("partial_colossal_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PartialColossalActionPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, PartialColossalActionPayload::action, PartialColossalActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
