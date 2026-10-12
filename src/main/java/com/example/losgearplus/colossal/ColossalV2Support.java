package com.example.losgearplus.colossal;

import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;

/** Bridges the custom V2 marker to DAOT's existing Colossal power set. */
public final class ColossalV2Support {
	public static final String V2_TAG = "colossalv2";
	public static final String V2_TAG_LEGACY = "colossal_v2";
	public static final String DAOT_TAG = "colossal";

	private ColossalV2Support() {}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(ColossalV2SyncPayload.TYPE, ColossalV2SyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer joining = handler.player;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (hasV2Tag(player)) ServerPlayNetworking.send(joining, new ColossalV2SyncPayload(player.getUUID(), true));
			}
		});
	}

	public static boolean hasV2Tag(ServerPlayer player) {
		return player.getTags().contains(V2_TAG) || player.getTags().contains(V2_TAG_LEGACY);
	}

	/** DAOT still owns all Colossal powers; add its base tag when a V2 player begins a shift. */
	public static void ensureDaotColossalTag(ServerPlayer player) {
		boolean v2 = hasV2Tag(player);
		if (v2) player.addTag(DAOT_TAG);
		ColossalV2SyncPayload payload = new ColossalV2SyncPayload(player.getUUID(), v2);
		for (ServerPlayer viewer : PlayerLookup.world((ServerLevel) player.level())) {
			if (ServerPlayNetworking.canSend(viewer, ColossalV2SyncPayload.TYPE)) ServerPlayNetworking.send(viewer, payload);
		}
	}
}
