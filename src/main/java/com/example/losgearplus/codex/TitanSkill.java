package com.example.losgearplus.codex;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma habilidade que pode ser colocada num slot da barra de habilidades de um titã shifter.
 *
 * <p>Existem dois tipos: as habilidades ORIGINAIS do DAOT ({@link DaotSkill}, só reencaminham para o número
 * original) e as SUAS ({@link CustomTitanSkill}, com código próprio). O jogador escolhe o que fica em cada slot
 * pelo Titan Ability Codex.
 *
 * <p>O {@link #id()} é sempre um {@code ResourceLocation} válido (ex.: {@code los_gear_plus:shockwave}); é ele
 * que fica gravado no jogador, então NÃO mude o id de uma skill já publicada.
 */
public interface TitanSkill {
	/** Titã "coringa": a skill serve para todos os titãs suportados. */
	String ALL = "*";

	String id();

	/** Dono da skill ({@code attack}, {@code colossal}...) ou {@link #ALL}. */
	String titanId();

	/** Esta skill pode ser equipada num titã deste tipo? */
	default boolean compatibleWith(String titan) {
		return titanId().equals(ALL) || titanId().equals(titan);
	}

	/** Textura 32x32 mostrada no Codex e na barra do DAOT. */
	ResourceLocation icon();

	/** Número original da habilidade no DAOT (1..9); 0 = habilidade própria. */
	default int daotAbilityNumber() {
		return 0;
	}

	default boolean isCustom() {
		return daotAbilityNumber() <= 0;
	}

	/** false = o jogador só pode equipar depois de {@code /codex unlock} (ou {@link CodexData#unlock}). */
	default boolean unlockedByDefault() {
		return true;
	}

	default String translationKey() {
		return "los_gear_plus.codex.skill." + id().replace(':', '.').replace('/', '.');
	}

	default Component name() {
		return Component.translatable(translationKey() + ".name");
	}

	default Component description() {
		return isCustom() ? Component.translatable(translationKey() + ".desc")
				: Component.translatable("los_gear_plus.codex.daot_desc", daotAbilityNumber());
	}
}
