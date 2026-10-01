package com.example.losgearplus.hook;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: o ângulo oficial do jogador (resposta a cada SetHookAnglePayload). */
public record HookAngleSyncPayload(int angle) implements CustomPacketPayload {
	public static final Type<HookAngleSyncPayload> TYPE = new Type<>(LosGearPlus.id("hook_angle_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HookAngleSyncPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, HookAngleSyncPayload::angle, HookAngleSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
