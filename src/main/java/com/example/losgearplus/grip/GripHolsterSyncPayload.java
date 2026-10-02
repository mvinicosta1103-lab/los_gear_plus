package com.example.losgearplus.grip;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> clientes: quais grips o jogador (entityId) tem GUARDADOS no storage.
 * mask: bit 0 = grip da mão principal (lado direito), bit 1 = grip da mão secundária (lado esquerdo).
 * O cliente só desenha no torso o que está marcado aqui.
 */
public record GripHolsterSyncPayload(int entityId, int mask) implements CustomPacketPayload {
	public static final Type<GripHolsterSyncPayload> TYPE = new Type<>(LosGearPlus.id("grip_holster_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GripHolsterSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GripHolsterSyncPayload::entityId,
			ByteBufCodecs.VAR_INT, GripHolsterSyncPayload::mask,
			GripHolsterSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
