package com.example.losgearplus.hook;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registro comum (cliente + servidor) do ângulo dos ganchos. Chame uma vez em LosGearPlus.onInitialize(). */
public final class HookAngleNetworking {
	private HookAngleNetworking() {}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(SetHookAnglePayload.TYPE, SetHookAnglePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HookAngleSyncPayload.TYPE, HookAngleSyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SetHookAnglePayload.TYPE,
				(payload, context) -> HookAngleServer.set(context.player(), payload.angle()));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HookAngleServer.forget(handler.getPlayer()));
	}
}
