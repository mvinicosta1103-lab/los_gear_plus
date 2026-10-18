package com.example.losgearplus.codex;

import com.example.losgearplus.codex.stacks.StackData;
import com.example.losgearplus.partial.PartialShifterTitanEntity;
import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.CartShifterTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
import daot.FoundingTitanEntity;
import daot.TestShifterTitanEntity;
import daot.WarhammerTitanEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Quais titãs o Codex conhece e como reconhecê-los (pela entidade que o jogador monta ou pelas tags de shifter).
 *
 * <p>Jaw, Cart e Founding também têm aba (eles ESTENDEM o Attack no DAOT e passam pelo mesmo ramo do
 * {@code handleTitanAbility}). Só o Ogre ainda fica de fora. As skills de stack funcionam em todos (teclas próprias).
 */
public final class TitanIds {
	private TitanIds() {}

	/** "Titã" especial do Codex: a barra de 3 slots das skills de stack (funciona com qualquer titã). */
	public static final String STACK = "stack";

	public static final List<String> SUPPORTED = List.of("attack", "armored", "female", "colossal", "beast", "warhammer", "jaw", "cart", "founding");

	/** Tag de jogador do DAOT -> id de titã do Codex. */
	private static final Map<String, String> TAGS = Map.ofEntries(
			Map.entry("attack", "attack"), Map.entry("armored", "armored"), Map.entry("female", "female"),
			Map.entry("beast", "beast"), Map.entry("warhammer", "warhammer"), Map.entry("colossal", "colossal"),
			Map.entry("colossal_v2", "colossal"), Map.entry("colossalv2", "colossal"),
			Map.entry("jaw", "jaw"), Map.entry("cart_shifter", "cart"), Map.entry("founder", "founding"));

	/** Id do titã que o jogador está montando, ou null se não for um dos suportados (ou for o titã parcial). */
	public static String of(Entity vehicle) {
		if (vehicle == null || vehicle instanceof PartialShifterTitanEntity) return null;
		// Jaw, Cart (que estende o Jaw) e Founding estendem o Attack: precisam ser testados ANTES dele.
		if (vehicle instanceof CartShifterTitanEntity) return "cart"; // o Cart estende o Jaw: vem primeiro
		if (vehicle instanceof TestShifterTitanEntity) return "jaw";
		if (vehicle instanceof FoundingTitanEntity) return "founding";
		if (vehicle instanceof WarhammerTitanEntity) return "warhammer";
		if (vehicle instanceof ColossalTitanEntity) return "colossal";
		if (vehicle instanceof BeastTitanEntity) return "beast";
		if (vehicle instanceof FemaleTitanEntity) return "female";
		if (vehicle instanceof ArmoredTitanEntity) return "armored"; // inclui o Armored Weaker
		if (vehicle.getClass() == AttackTitanEntity.class) return "attack"; // o Ogre também estende o Attack: fica de fora
		return null;
	}

	/** Quantos slots de habilidade o titã tem na barra do DAOT. */
	public static int slotCount(String titan) {
		if (STACK.equals(titan)) return 3;
		if ("jaw".equals(titan)) return 5;
		return "colossal".equals(titan) ? 4 : 9;
	}

	/** Titãs que este jogador possui (pelas tags). Criativo vê todos. */
	public static List<String> owned(ServerPlayer player) {
		boolean stacks = player.isCreative() || StackData.hasAny(player);
		if (player.isCreative()) return withStack(SUPPORTED, stacks);
		Set<String> mine = new LinkedHashSet<>();
		for (String tag : player.getTags()) {
			String id = TAGS.get(tag);
			if (id != null) mine.add(id);
		}
		return withStack(SUPPORTED.stream().filter(mine::contains).toList(), stacks);
	}

	private static List<String> withStack(List<String> titans, boolean stacks) {
		if (!stacks) return titans;
		List<String> out = new java.util.ArrayList<>(titans);
		out.add(STACK);
		return out;
	}

	/** Titã da barra 1-9 que o Codex roteia (não inclui a aba de stacks). */
	public static boolean isSupported(String titan) {
		return SUPPORTED.contains(titan);
	}

	/** Qualquer aba do Codex: os titãs suportados ou a de stacks. */
	public static boolean isTab(String id) {
		return isSupported(id) || STACK.equals(id);
	}
}
