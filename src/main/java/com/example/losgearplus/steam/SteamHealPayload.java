package com.example.losgearplus.steam;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "quero usar o Steam Heal". Sem dados; o servidor valida tudo. */
public record SteamHealPayload() implements CustomPacketPayload {
	public static final Type<SteamHealPayload> TYPE = new Type<>(LosGearPlus.id("steam_heal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SteamHealPayload> CODEC =
			StreamCodec.unit(new SteamHealPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
