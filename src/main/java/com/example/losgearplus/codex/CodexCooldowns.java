package com.example.losgearplus.codex;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Recargas das skills PRÓPRIAS (só em memória do servidor; as originais usam a recarga do próprio DAOT). */
public final class CodexCooldowns {
	private CodexCooldowns() {}

	private static final Map<UUID, Map<String, Long>> END = new HashMap<>();

	/** Ticks que faltam para a skill ficar pronta (0 = pronta). */
	public static int remaining(ServerPlayer player, String skillId) {
		Map<String, Long> m = END.get(player.getUUID());
		Long end = m == null ? null : m.get(skillId);
		if (end == null) return 0;
		long left = end - player.serverLevel().getGameTime();
		if (left <= 0L) {
			m.remove(skillId);
			return 0;
		}
		return (int) left;
	}

	public static void start(ServerPlayer player, String skillId, int ticks) {
		if (ticks <= 0) return;
		END.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(skillId, player.serverLevel().getGameTime() + ticks);
		if (ServerPlayNetworking.canSend(player, CodexPayloads.Cooldown.TYPE)) {
			ServerPlayNetworking.send(player, new CodexPayloads.Cooldown(skillId, ticks));
		}
	}

	public static void clear(UUID id) {
		END.remove(id);
	}
}
