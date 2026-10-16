package com.example.losgearplus.codex.skills;

import com.example.losgearplus.codex.TitanCodex;

/**
 * AQUI você registra as suas skills. Para criar uma nova:
 * <ol>
 *   <li>copie {@link ShockwaveSkill}, mude o {@code path}, os titãs, a recarga, o custo e o {@code activate};</li>
 *   <li>coloque o ícone 32x32 em {@code assets/los_gear_plus/textures/gui/codex/<path>.png};</li>
 *   <li>acrescente {@code los_gear_plus.codex.skill.los_gear_plus.<path>.name} e {@code .desc} nos arquivos de lang;</li>
 *   <li>registre aqui com {@code TitanCodex.register(new MinhaSkill())}.</li>
 * </ol>
 */
public final class CodexSkills {
	private CodexSkills() {}

	public static void registerAll() {
		TitanCodex.register(new ShockwaveSkill());
		TitanCodex.register(new SteamSurgeSkill());
		com.example.losgearplus.codex.stacks.StackSkills.registerAll(); // skills ganhas por stack
	}
}
