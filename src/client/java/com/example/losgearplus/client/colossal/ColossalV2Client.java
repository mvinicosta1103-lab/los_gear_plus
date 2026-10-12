package com.example.losgearplus.client.colossal;

import com.example.losgearplus.colossal.ColossalV2SyncPayload;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side cache of Colossal V2 owner UUIDs, since entity scoreboard tags are not a render sync channel. */
public final class ColossalV2Client {
	private static final Set<UUID> V2_PLAYERS = ConcurrentHashMap.newKeySet();

	private ColossalV2Client() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ColossalV2SyncPayload.TYPE, (payload, context) -> {
			if (payload.v2()) V2_PLAYERS.add(payload.playerId());
			else V2_PLAYERS.remove(payload.playerId());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> V2_PLAYERS.clear());
	}

	public static boolean isV2(UUID playerId) { return V2_PLAYERS.contains(playerId); }
}
