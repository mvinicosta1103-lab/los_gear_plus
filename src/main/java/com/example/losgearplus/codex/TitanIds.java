package com.example.losgearplus.codex;

import com.example.losgearplus.partial.PartialShifterTitanEntity;
import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
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
 * <p>Fora da v1: Jaw, Cart, Ogre e Founding. Eles não passam por {@code ModNetworking.handleTitanAbility} (o
 * Jaw/Cart usam outro caminho no DAOT), então precisam de um gancho próprio; ver o README do Codex.
 */
public final class TitanIds {
	private TitanIds() {}

	public static final List<String> SUPPORTED = List.of("attack", "armored", "female", "colossal", "beast", "warhammer");

	/** Tag de jogador do DAOT -> id de titã do Codex. */
	private static final Map<String, String> TAGS = Map.of(
			"attack", "attack", "armored", "armored", "female", "female", "beast", "beast",
			"warhammer", "warhammer", "colossal", "colossal", "colossal_v2", "colossal", "colossalv2", "colossal");

	/** Id do titã que o jogador está montando, ou null se não for um dos suportados (ou for o titã parcial). */
	public static String of(Entity vehicle) {
		if (vehicle == null || vehicle instanceof PartialShifterTitanEntity) return null;
		if (vehicle instanceof WarhammerTitanEntity) return "warhammer";
		if (vehicle instanceof ColossalTitanEntity) return "colossal";
		if (vehicle instanceof BeastTitanEntity) return "beast";
		if (vehicle instanceof FemaleTitanEntity) return "female";
		if (vehicle instanceof ArmoredTitanEntity) return "armored"; // inclui o Armored Weaker
		if (vehicle.getClass() == AttackTitanEntity.class) return "attack"; // o Founding estende o Attack: fica de fora
		return null;
	}

	/** Quantos slots de habilidade o titã tem na barra do DAOT. */
	public static int slotCount(String titan) {
		return "colossal".equals(titan) ? 4 : 9;
	}

	/** Titãs que este jogador possui (pelas tags). Criativo vê todos. */
	public static List<String> owned(ServerPlayer player) {
		if (player.isCreative()) return SUPPORTED;
		Set<String> mine = new LinkedHashSet<>();
		for (String tag : player.getTags()) {
			String id = TAGS.get(tag);
			if (id != null) mine.add(id);
		}
		return SUPPORTED.stream().filter(mine::contains).toList();
	}

	public static boolean isSupported(String titan) {
		return SUPPORTED.contains(titan);
	}
}
