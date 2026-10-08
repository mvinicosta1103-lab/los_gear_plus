package com.example.losgearplus.client.limb;

import com.example.losgearplus.limb.LimbClientCache;
import com.example.losgearplus.limb.LimbState;
import com.example.losgearplus.limb.LimbSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Recebe o estado de membros dos jogadores visíveis. */
public final class LimbClient {
	private LimbClient() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LimbSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					LimbState next = payload.toState();
					LimbState previous = LimbClientCache.put(payload.id(), next);
					HumanLimbDebris.onState(payload.id(), previous, next); // membro decepado do corpo humano
				}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LimbClientCache.clear();
			TitanLimbBones.clear();
			HumanLimbDebris.clear();
		});
		HumanLimbDebris.init();
		LimbVisionHud.init();
	}
}
