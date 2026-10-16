package com.example.losgearplus.codex.stacks;

import com.example.losgearplus.codex.skills.AbstractCustomSkill;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * Skill ganha ao absorver um stack. Mora no "titã" especial {@code stack} do Codex (3 slots, 3 teclas próprias),
 * então funciona com QUALQUER titã que o jogador monte, inclusive Cart, Jaw e Ogre, que não usam a barra 1-9.
 * Fica bloqueada até o jogador ter o stack de origem; o nível do stack (I a III) aumenta o poder.
 */
public abstract class StackSkill extends AbstractCustomSkill {
	private final StackSource source;

	protected StackSkill(StackSource source, String path, int cooldownTicks, float staminaFraction) {
		super(path, Set.of(StackSkills.TAB), cooldownTicks, staminaFraction);
		this.source = source;
	}

	public StackSource source() {
		return source;
	}

	@Override
	public boolean unlockedByDefault() {
		return false; // quem libera é o stack (CodexData.isUnlocked)
	}

	protected int level(ServerPlayer player) {
		return Math.max(1, StackData.level(player, source));
	}

	/** 1.0 no nível I, 1.25 no II, 1.5 no III. */
	protected double power(ServerPlayer player) {
		return 1.0 + 0.25 * (level(player) - 1);
	}
}
