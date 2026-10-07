package com.example.losgearplus.limb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Envia o estado de membros ao próprio jogador e a quem o está vendo (para renderizar). */
public final class LimbSync {
	private LimbSync() {}

	private static final Map<UUID, byte[]> LAST = new HashMap<>();
	/** Ticks desde o último envio, contados por nós (nada de comparar relógios diferentes). */
	private static final Map<UUID, Integer> SINCE = new HashMap<>();
	/** Enquanto algo cresce, manda no máximo a cada N ticks. */
	private static final int THROTTLE = 4;

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST.remove(handler.getPlayer().getUUID());
			SINCE.remove(handler.getPlayer().getUUID());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> send(newPlayer, true));
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof ServerPlayer tp) {
				push(viewer, LimbSyncPayload.of(tp.getUUID(), LimbData.of(tp)));
			}
		});
	}

	/** {@code force} ignora a comparação com o último envio. */
	public static void send(ServerPlayer owner, boolean force) {
		LimbState state = LimbData.of(owner);
		LimbSyncPayload pl = LimbSyncPayload.of(owner.getUUID(), state);
		byte[] prev = LAST.get(owner.getUUID());
		if (!force) {
			if (prev != null && Arrays.equals(prev, pl.data())) return;
			if (prev == null && state.isPristine()) return;
		}
		LAST.put(owner.getUUID(), pl.data());
		SINCE.put(owner.getUUID(), 0);
		push(owner, pl);
		for (ServerPlayer viewer : PlayerLookup.tracking(owner)) push(viewer, pl);
	}

	/** Chamado todo tick para quem tem perdas: reenvia só se mudou e respeitando o intervalo. */
	public static void tick(ServerPlayer owner) {
		UUID id = owner.getUUID();
		int since = SINCE.merge(id, 1, Integer::sum);
		byte[] prev = LAST.get(id);
		byte[] cur = LimbSyncPayload.of(id, LimbData.of(owner)).data();
		// mudou quem está perdido/crescendo/inteiro: manda já; só o progresso (barra de crescimento): a cada THROTTLE ticks
		boolean statusChanged = prev == null || !Arrays.equals(
				Arrays.copyOf(cur, LimbPart.COUNT), Arrays.copyOf(prev, LimbPart.COUNT));
		if (!statusChanged && since < THROTTLE) return;
		send(owner, false);
	}

	private static void push(ServerPlayer target, LimbSyncPayload pl) {
		if (ServerPlayNetworking.canSend(target, LimbSyncPayload.TYPE)) ServerPlayNetworking.send(target, pl);
	}
}
