package com.example.losgearplus.grip;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Servidor -> clientes: os grips que o jogador (entityId) tem GUARDADOS no storage.
 * main = grip da mão principal, off = grip da mão secundária (EMPTY = nada guardado nesse lado).
 * Vão os stacks completos para o cliente desenhar o estado real da lâmina (fresca, lascada, quebrada, skin).
 * O cliente só desenha no torso o que o servidor mandou aqui.
 */
public record GripHolsterSyncPayload(int entityId, ItemStack main, ItemStack off) implements CustomPacketPayload {
	public static final Type<GripHolsterSyncPayload> TYPE = new Type<>(LosGearPlus.id("grip_holster_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GripHolsterSyncPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.entityId);
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.main);
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.off);
			},
			buf -> new GripHolsterSyncPayload(buf.readVarInt(),
					ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}