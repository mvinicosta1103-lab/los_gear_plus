package com.example.losgearplus.codex;

import com.example.losgearplus.LosGearPlus;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * O que o jogador escolheu no Codex. Fica gravado no JOGADOR (Data Attachment persistente) e sobrevive à morte.
 *
 * <ul>
 *   <li>Loadout: por titã, uma lista de 9 ids (um por slot). {@link #DEFAULT} ("") = o slot continua com a
 *       habilidade original do DAOT, então quem nunca abre o Codex joga igual a antes. {@link #EMPTY} ("-") =
 *       slot desligado (apertar a tecla não faz nada).</li>
 *   <li>Desbloqueios: ids de skills com {@code unlockedByDefault() == false} que o jogador já liberou.</li>
 * </ul>
 */
public final class CodexData {
	private CodexData() {}

	public static final String DEFAULT = "";
	public static final String EMPTY = "-";
	public static final int MAX_SLOTS = 9;

	public static final AttachmentType<Map<String, List<String>>> LOADOUT = AttachmentRegistry.<Map<String, List<String>>>builder()
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()))
			.copyOnDeath()
			.buildAndRegister(LosGearPlus.id("codex_loadout"));

	public static final AttachmentType<List<String>> UNLOCKED = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf())
			.copyOnDeath()
			.buildAndRegister(LosGearPlus.id("codex_unlocked"));

	/** Os 9 slots do titã (0-based); sempre devolve 9 entradas ("" = padrão). */
	public static String[] slots(ServerPlayer player, String titan) {
		String[] out = new String[MAX_SLOTS];
		Arrays.fill(out, DEFAULT);
		Map<String, List<String>> all = player.getAttached(LOADOUT);
		List<String> saved = all == null ? null : all.get(titan);
		if (saved != null) {
			for (int i = 0; i < MAX_SLOTS && i < saved.size(); i++) out[i] = saved.get(i);
		}
		return out;
	}

	public static String getSlot(ServerPlayer player, String titan, int slot) {
		return slot < 0 || slot >= MAX_SLOTS ? DEFAULT : slots(player, titan)[slot];
	}

	public static void setSlot(ServerPlayer player, String titan, int slot, String skillId) {
		if (slot < 0 || slot >= MAX_SLOTS) return;
		String[] slots = slots(player, titan);
		slots[slot] = skillId;
		store(player, titan, slots);
	}

	/** Volta o titã inteiro às habilidades originais. */
	public static void resetTitan(ServerPlayer player, String titan) {
		store(player, titan, null);
	}

	public static void resetAll(ServerPlayer player) {
		player.removeAttached(LOADOUT);
	}

	private static void store(ServerPlayer player, String titan, String[] slots) {
		Map<String, List<String>> next = new HashMap<>();
		Map<String, List<String>> old = player.getAttached(LOADOUT);
		if (old != null) next.putAll(old);
		if (slots == null || Arrays.stream(slots).allMatch(String::isEmpty)) {
			next.remove(titan); // tudo padrão: não precisa guardar nada
		} else {
			next.put(titan, new ArrayList<>(Arrays.asList(slots)));
		}
		if (next.isEmpty()) player.removeAttached(LOADOUT);
		else player.setAttached(LOADOUT, Map.copyOf(next));
	}

	// ---- desbloqueios -------------------------------------------------------------------------------------

	public static List<String> unlockedList(ServerPlayer player) {
		List<String> l = player.getAttached(UNLOCKED);
		return l == null ? List.of() : l;
	}

	public static boolean isUnlocked(ServerPlayer player, TitanSkill skill) {
		return skill.unlockedByDefault() || unlockedList(player).contains(skill.id());
	}

	public static boolean unlock(ServerPlayer player, String skillId) {
		List<String> cur = unlockedList(player);
		if (cur.contains(skillId)) return false;
		List<String> next = new ArrayList<>(cur);
		next.add(skillId);
		player.setAttached(UNLOCKED, List.copyOf(next));
		return true;
	}

	public static boolean lock(ServerPlayer player, String skillId) {
		List<String> cur = unlockedList(player);
		if (!cur.contains(skillId)) return false;
		List<String> next = new ArrayList<>(cur);
		next.remove(skillId);
		if (next.isEmpty()) player.removeAttached(UNLOCKED);
		else player.setAttached(UNLOCKED, List.copyOf(next));
		return true;
	}
}
