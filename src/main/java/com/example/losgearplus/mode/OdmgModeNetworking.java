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
		PayloadTypeRegistry.playC2S().register(SetHookAnglePayload.TYPE, SetHookAnglePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HookAngleSyncPayload.TYPE, HookAngleSyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ToggleOdmgModePayload.TYPE,
				(payload, context) -> OdmgModeServer.toggle(context.player()));

		// Angulação dos hooks: só aceita com o ODMG Mode ligado (o valor é limitado a 0..180).
		ServerPlayNetworking.registerGlobalReceiver(SetHookAnglePayload.TYPE, (payload, context) -> {
			if (OdmgModeServer.isActive(context.player())) {
				HookAngles.set(context.player(), payload.angle());
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(OdmgModeServer::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			OdmgModeServer.forget(handler.getPlayer());
			HookAngles.forget(handler.getPlayer().getUUID());
		});
		// Ao morrer/respawnar o modo cai (e o cliente é avisado).
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			OdmgModeServer.set(newPlayer, false);
			HookAngles.forget(newPlayer.getUUID());
			ServerPlayNetworking.send(newPlayer, new HookAngleSyncPayload(0));
		});
	}
}
