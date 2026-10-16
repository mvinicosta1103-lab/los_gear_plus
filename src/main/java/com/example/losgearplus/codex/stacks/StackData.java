package com.example.losgearplus.codex.stacks;

import com.example.losgearplus.LosGearPlus;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Os stacks do jogador: titã de origem -> nível (1 a {@link #MAX_LEVEL}). Gravado no jogador, sobrevive à morte. */
public final class StackData {
	private StackData() {}

	public static final int MAX_LEVEL = 3;

	public static final AttachmentType<Map<String, Integer>> STACKS = AttachmentRegistry.<Map<String, Integer>>builder()
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
			.copyOnDeath()
			.buildAndRegister(LosGearPlus.id("codex_stacks"));

	public static Map<String, Integer> all(ServerPlayer player) {
		Map<String, Integer> m = player.getAttached(STACKS);
		return m == null ? Map.of() : m;
	}

	public static int level(ServerPlayer player, StackSource source) {
		return all(player).getOrDefault(source.id, 0);
	}

	public static boolean hasAny(ServerPlayer player) {
		return !all(player).isEmpty();
	}

	/** Define o nível (0 remove, e acima do máximo vira o máximo). */
	public static void set(ServerPlayer player, StackSource source, int level) {
		Map<String, Integer> next = new HashMap<>(all(player));
		int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
		if (clamped == 0) next.remove(source.id);
		else next.put(source.id, clamped);
		if (next.isEmpty()) player.removeAttached(STACKS);
		else player.setAttached(STACKS, Map.copyOf(next));
	}

	/** Soma níveis e devolve o nível final. */
	public static int add(ServerPlayer player, StackSource source, int amount) {
		int now = Math.min(MAX_LEVEL, level(player, source) + amount);
		set(player, source, now);
		return now;
	}

	public static void clear(ServerPlayer player) {
		player.removeAttached(STACKS);
	}
}
