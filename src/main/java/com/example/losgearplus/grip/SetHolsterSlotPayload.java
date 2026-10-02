package com.example.losgearplus.grip;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Cliente -> servidor, só no inventário do MODO CRIATIVO: o criativo não manda o clique de slots que não são do
 * inventário, então o cliente avisa o que ficou em cada slot de arma ao lado do peitoral (0 = principal, 1 = secundária).
 * O servidor só aceita de jogador criativo, com New ODM Uniform + New ODM Gear vestidos e o ODMG Mode desligado.
 */
public record SetHolsterSlotPayload(int slot, ItemStack stack) implements CustomPacketPayload {
	public static final Type<SetHolsterSlotPayload> TYPE = new Type<>(LosGearPlus.id("set_holster_slot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SetHolsterSlotPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetHolsterSlotPayload::slot,
			ItemStack.OPTIONAL_STREAM_CODEC, SetHolsterSlotPayload::stack,
			SetHolsterSlotPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
