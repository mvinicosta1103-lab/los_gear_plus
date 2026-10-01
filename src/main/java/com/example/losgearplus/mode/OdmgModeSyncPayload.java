package com.example.losgearplus.mode;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: estado oficial do ODMG Mode do jogador. */
public record OdmgModeSyncPayload(boolean active) implements CustomPacketPayload {
	public static final Type<OdmgModeSyncPayload> TYPE = new Type<>(LosGearPlus.id("odmg_mode_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OdmgModeSyncPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, OdmgModeSyncPayload::active, OdmgModeSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
