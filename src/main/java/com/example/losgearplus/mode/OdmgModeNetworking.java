package com.example.losgearplus.mode;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registro comum (cliente + servidor) do ODMG Mode. Chame uma vez em LosGearPlus.onInitialize(). */
public final class OdmgModeNetworking {
	private OdmgModeNetworking() {}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(ToggleOdmgModePayload.TYPE, ToggleOdmgModePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OdmgModeSyncPayload.TYPE, OdmgModeSyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ToggleOdmgModePayload.TYPE,
				(payload, context) -> OdmgModeServer.toggle(context.player()));

		ServerTickEvents.END_SERVER_TICK.register(OdmgModeServer::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OdmgModeServer.forget(handler.getPlayer()));
		// Ao morrer/respawnar o modo cai (e o cliente é avisado).
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> OdmgModeServer.set(newPlayer, false));
	}
}
