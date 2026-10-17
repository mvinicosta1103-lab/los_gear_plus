package com.example.losgearplus.client.codex;

import com.example.losgearplus.codex.CodexPayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Quais titãs (por id de entidade) estão com cristal agora, e de que tipo. Lido pela {@link CrystalLayer}. */
public final class CrystalClient {
	private CrystalClient() {}

	public record Fx(int mode, long start, long end) {}

	private static final Map<Integer, Fx> FX = new HashMap<>();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CodexPayloads.CrystalFx.TYPE, (payload, context) -> context.client().execute(() -> {
			if (payload.mode() == CodexPayloads.CrystalFx.CLEAR) FX.remove(payload.entityId());
			else FX.put(payload.entityId(), new Fx(payload.mode(), payload.start(), payload.end()));
		}));
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> FX.remove(entity.getId()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FX.clear());
	}

	public static Fx get(int entityId) {
		return FX.get(entityId);
	}
}
