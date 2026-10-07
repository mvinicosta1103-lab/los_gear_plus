package com.example.losgearplus.client.evap;

import com.example.losgearplus.evap.EvaporateSyncPayload;
import com.example.losgearplus.evap.EvaporationRules;
import daot.HeavyTitan;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Estado de evaporação no cliente: quanto cada corpo de titã está escuro (0..1). */
public final class EvaporationClient {
	private EvaporationClient() {}

	/** id da entidade -> tick de jogo (cliente) em que ela some. */
	private static final Int2LongOpenHashMap ENDS = new Int2LongOpenHashMap();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(EvaporateSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			var level = context.client().level;
			if (level == null) return;
			if (payload.remainingTicks() <= 0) ENDS.remove(payload.entityId());
			else ENDS.put(payload.entityId(), level.getGameTime() + payload.remainingTicks());
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ENDS.clear());
	}

	/** 0 = normal; 1 = totalmente preto. */
	public static float darkness(Entity e, float partialTick) {
		if (e instanceof HeavyTitan && e instanceof Mob m && m.isDeadOrDying()) {
			float elapsed = m.deathTime + partialTick - EvaporationRules.PURE_START_DEATH_TICKS;
			return elapsed <= 0f ? 0f : EvaporationRules.darkness(elapsed / EvaporationRules.DURATION_TICKS);
		}
		if (ENDS.isEmpty() || !ENDS.containsKey(e.getId())) return 0f;
		var level = Minecraft.getInstance().level;
		if (level == null) return 0f;
		float left = ENDS.get(e.getId()) - (level.getGameTime() + partialTick);
		if (left <= 0f) return 1f;
		return EvaporationRules.darkness(1f - left / EvaporationRules.DURATION_TICKS);
	}
}
