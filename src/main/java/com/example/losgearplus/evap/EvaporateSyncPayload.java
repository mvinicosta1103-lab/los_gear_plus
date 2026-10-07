package com.example.losgearplus.evap;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: o corpo de um titã de shifter está evaporando. {@code remainingTicks} é quanto falta para
 * sumir; 0 (ou menos) cancela (o portador voltou para o corpo). É reenviado a cada segundo para quem chegar depois.
 */
public record EvaporateSyncPayload(int entityId, int remainingTicks) implements CustomPacketPayload {
	public static final Type<EvaporateSyncPayload> TYPE = new Type<>(LosGearPlus.id("evaporate_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EvaporateSyncPayload> CODEC =
			StreamCodec.of(EvaporateSyncPayload::write, EvaporateSyncPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, EvaporateSyncPayload p) {
		buf.writeVarInt(p.entityId);
		buf.writeVarInt(Math.max(0, p.remainingTicks));
	}

	private static EvaporateSyncPayload read(RegistryFriendlyByteBuf buf) {
		return new EvaporateSyncPayload(buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
