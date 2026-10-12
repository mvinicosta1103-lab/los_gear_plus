package com.example.losgearplus.colossal;

import com.example.losgearplus.LosGearPlus;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> clients: identifies which Colossal player UUID should use the V2 appearance. */
public record ColossalV2SyncPayload(UUID playerId, boolean v2) implements CustomPacketPayload {
	public static final Type<ColossalV2SyncPayload> TYPE = new Type<>(LosGearPlus.id("colossal_v2_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ColossalV2SyncPayload> CODEC = StreamCodec.of(
			(buf, payload) -> { buf.writeUUID(payload.playerId); buf.writeBoolean(payload.v2); },
			buf -> new ColossalV2SyncPayload(buf.readUUID(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
