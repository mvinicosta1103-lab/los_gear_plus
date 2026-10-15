package com.example.losgearplus.codex;

import net.minecraft.resources.ResourceLocation;

/**
 * Habilidade original do DAOT. Não tem código: quando equipada no slot N, apertar a tecla N chama o handler do
 * DAOT com o {@code number} original (ver CodexServer#route). Assim o jogador pode reordenar/trocar as originais.
 */
public record DaotSkill(String id, String titanId, int number, ResourceLocation icon) implements TitanSkill {
	@Override
	public int daotAbilityNumber() {
		return number;
	}
}
