package com.example.losgearplus.shifter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps the Mastery HUD up to date (the server is the single source of truth for the cooldown): sends the state on join and respawn, whenever something changes
 * (commands, transformations and level-ups call {@link #send}) and re-checks once per second, which also
 * catches the cooldown window expiring and the XP gained while transformed.
 * It only resends when level / used / limit / "is shifter" / XP change; the cooldown countdown runs on the client.
 * The Cart Titan is sent with an unlimited limit (max = -1) whatever its level.
 */
public final class ShifterMasterySync {
	private ShifterMasterySync() {}

	/** DAOT shifter tags (same list as Steam Heal). */
	public static final Set<String> SHIFTER_TAGS = ShifterTypes.TAGS;

	private static final Map<UUID, ShifterMasterySyncPayload> LAST = new HashMap<>();

	public static void init() {
		PayloadTypeRegistry.playS2C().register(ShifterMasterySyncPayload.TYPE, ShifterMasterySyncPayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.getPlayer().getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> send(newPlayer, true));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, false);
		});
	}

	/** Sends the current state to the player. {@code force} skips the comparison with the last send. */
	public static void send(ServerPlayer player, boolean force) {
		if (!ServerPlayNetworking.canSend(player, ShifterMasterySyncPayload.TYPE)) return;
		UUID id = player.getUUID();
		int level = ShifterMastery.getLevel(id);
		int maxT = ShifterMastery.maxTransforms(id); // already applies the Cart Titan override
		boolean shifter = false;
		for (String tag : player.getTags()) {
			if (SHIFTER_TAGS.contains(tag)) { shifter = true; break; }
		}
		ShifterMasterySyncPayload cur = new ShifterMasterySyncPayload(
				level,
				ShifterMastery.usedTransforms(id),
				maxT == ShifterMastery.UNLIMITED ? -1 : maxT,
				(int) Math.min(Integer.MAX_VALUE, ShifterMastery.ticksUntilReset(id)),
				shifter,
				ShifterMastery.xpIntoLevel(id),
				ShifterMastery.xpForNextLevel(level));

		ShifterMasterySyncPayload prev = LAST.get(id);
		// While a cooldown is running (limited levels) resend every second so the HUD countdown never drifts.
		boolean cooldownRunning = cur.used() > 0 && cur.max() >= 0;
		if (!force && !cooldownRunning && prev != null && prev.level() == cur.level() && prev.used() == cur.used()
				&& prev.max() == cur.max() && prev.shifter() == cur.shifter() && prev.xp() == cur.xp()) {
			return;
		}
		LAST.put(id, cur);
		ServerPlayNetworking.send(player, cur);
	}
}