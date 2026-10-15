package com.example.losgearplus.codex;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Habilidade com código próprio. O Codex cuida de: slot escolhido pelo jogador, bloqueio (unlock), recarga,
 * custo de stamina (a maestria 9 não gasta, como no resto do mod), checagem de braços e sincronização.
 * Você só implementa {@link #activate}.
 */
public interface CustomTitanSkill extends TitanSkill {
	/** Recarga em ticks (20 = 1 s). */
	int cooldownTicks();

	/** Custo em fração da stamina MÁXIMA do shifter (0.10 = 10%). 0 = de graça. */
	float staminaFraction();

	/** true = precisa de pelo menos um braço funcionando (sistema de membros do mod). */
	default boolean needsArms() {
		return false;
	}

	/**
	 * Executa a habilidade (thread do servidor).
	 *
	 * @param player o shifter
	 * @param titan  o titã que ele está montando
	 * @return false para cancelar SEM cobrar stamina nem recarga (ex.: sem alvo)
	 */
	boolean activate(ServerPlayer player, LivingEntity titan);
}
