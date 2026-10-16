package com.example.losgearplus.codex.stacks;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.CartShifterTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
import daot.FoundingTitanEntity;
import daot.OgreShifterTitanEntity;
import daot.TestShifterTitanEntity;
import daot.WarhammerTitanEntity;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * De qual titã um stack vem. Matar um shifter deste titã dá ao assassino as habilidades dele (ver {@link StackSkills}).
 * {@code tags} são as tags de jogador do DAOT que dizem "este jogador É um shifter deste titã".
 */
public enum StackSource {
	ARMORED("armored", "armored"),
	ATTACK("attack", "attack"),
	FEMALE("female", "female"),
	COLOSSAL("colossal", "colossal", "colossal_v2", "colossalv2"),
	BEAST("beast", "beast"),
	WARHAMMER("warhammer", "warhammer"),
	JAW("jaw", "jaw"),
	CART("cart", "cart_shifter"),
	OGRE("ogre", "ogre_shifter");

	public final String id;
	private final Set<String> tags;

	StackSource(String id, String... tags) {
		this.id = id;
		this.tags = Set.of(tags);
	}

	public static StackSource byId(String id) {
		for (StackSource s : values()) if (s.id.equals(id)) return s;
		return null;
	}

	/**
	 * Qual titã é esta entidade? null = não gera stack (Founding, titãs puros, o titã parcial...). A ordem importa:
	 * o Ogre e o Founding ESTENDEM o Attack no DAOT, então precisam ser vistos antes dele.
	 */
	public static StackSource ofTitan(Entity e) {
		if (e instanceof CartShifterTitanEntity) return CART;
		if (e instanceof TestShifterTitanEntity) return JAW;
		if (e instanceof OgreShifterTitanEntity) return OGRE;
		if (e instanceof FoundingTitanEntity) return null;
		if (e instanceof WarhammerTitanEntity) return WARHAMMER;
		if (e instanceof ColossalTitanEntity) return COLOSSAL;
		if (e instanceof BeastTitanEntity) return BEAST;
		if (e instanceof FemaleTitanEntity) return FEMALE;
		if (e instanceof ArmoredTitanEntity) return ARMORED; // inclui o Armored Weaker
		if (e instanceof AttackTitanEntity) return ATTACK;
		return null;
	}

	/** Os titãs que o jogador possui NATURALMENTE (pelas tags). Stack do próprio titã não existe. */
	public static Set<StackSource> owned(Player player) {
		Set<StackSource> out = EnumSet.noneOf(StackSource.class);
		for (String tag : player.getTags()) {
			for (StackSource s : values()) if (s.tags.contains(tag)) out.add(s);
		}
		return out;
	}

	/** É um shifter? (um dos titãs acima, ou Founder / Triple T). */
	public static boolean isShifter(Player player) {
		return !owned(player).isEmpty() || player.getTags().contains("founder") || player.getTags().contains("triple_t");
	}
}
