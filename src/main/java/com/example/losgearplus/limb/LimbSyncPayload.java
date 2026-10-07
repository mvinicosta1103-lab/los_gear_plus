package com.example.losgearplus.limb;

import com.example.losgearplus.LosGearPlus;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: estado de membros de um jogador (para efeitos, HUD e renderização). 20 bytes. */
public record LimbSyncPayload(UUID id, byte[] data) implements CustomPacketPayload {
	public static final Type<LimbSyncPayload> TYPE = new Type<>(LosGearPlus.id("limb_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LimbSyncPayload> CODEC =
			StreamCodec.of(LimbSyncPayload::write, LimbSyncPayload::read);

	public static LimbSyncPayload of(UUID id, LimbState s) {
		byte[] d = new byte[LimbPart.COUNT * 2];
		for (LimbPart p : LimbPart.VALUES) {
			d[p.ordinal()] = (byte) s.status(p).ordinal();
			d[LimbPart.COUNT + p.ordinal()] = (byte) Math.round(s.progress(p) * 255f);
		}
		return new LimbSyncPayload(id, d);
	}

	public LimbState toState() {
		return LimbState.fromBytes(data);
	}

	private static void write(RegistryFriendlyByteBuf buf, LimbSyncPayload p) {
		buf.writeUUID(p.id);
		for (byte b : p.data) buf.writeByte(b);
	}

	private static LimbSyncPayload read(RegistryFriendlyByteBuf buf) {
		UUID id = buf.readUUID();
		byte[] d = new byte[LimbPart.COUNT * 2];
		for (int i = 0; i < d.length; i++) d[i] = buf.readByte();
		return new LimbSyncPayload(id, d);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
