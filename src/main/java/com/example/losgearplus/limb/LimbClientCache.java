package com.example.losgearplus.limb;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Estado de membros dos jogadores visíveis, como o cliente recebeu do servidor (sem classes de cliente). */
public final class LimbClientCache {
	private LimbClientCache() {}

	private static final Map<UUID, LimbState> MAP = new ConcurrentHashMap<>();

	public static void put(UUID id, LimbState state) { MAP.put(id, state); }

	public static LimbState get(UUID id) {
		LimbState s = MAP.get(id);
		return s == null ? LimbState.PRISTINE : s;
	}

	public static void clear() { MAP.clear(); }
}
