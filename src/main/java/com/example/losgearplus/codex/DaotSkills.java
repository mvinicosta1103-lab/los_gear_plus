package com.example.losgearplus.codex;

import net.minecraft.resources.ResourceLocation;

/**
 * Catálogo das habilidades ORIGINAIS do DAOT (2.5.x), uma por slot da barra de cada titã. Os números e os ícones
 * vêm do {@code ShifterAbilityHUD} e do {@code ModNetworking.handleTitanAbility} do DAOT. Gerado a partir de uma
 * tabela única, que também gera os nomes em {@code lang/*.json} (chave {@code los_gear_plus.codex.skill.daot.<titã>.<chave>.name}).
 * Se o DAOT ganhar uma habilidade nova, é só acrescentar uma linha aqui e o nome no lang.
 */
final class DaotSkills {
	private DaotSkills() {}

	static void registerAll() {
		add("attack", 1, "ground_stomp", "ability_titan_ground_stomp");
		add("attack", 4, "heavy_punch", "ability_titan_heavy_punch");
		add("attack", 6, "harden", "ability_titan_hardened_block");
		add("attack", 7, "grab", "ability_titan_grab_female");
		add("attack", 9, "berserk", "ability_titan_berserk");
		add("armored", 1, "hardened_stomp", "ability_titan_hardened_stomp");
		add("armored", 2, "kick", "ability_armored_kick");
		add("armored", 3, "heavy", "ability_titan_armored_heavy");
		add("armored", 4, "breach", "ability_titan_breach");
		add("armored", 5, "shatter", "ability_titan_armor_shatter");
		add("armored", 6, "toss", "ability_titan_toss");
		add("armored", 7, "grab", "ability_titan_titan_hardened_grab");
		add("armored", 8, "climb", "ability_titan_hardened_fists_tier_1");
		add("armored", 9, "consciousness_transfer", "ability_titan_consciousness_transfer");
		add("female", 1, "ground_stomp", "female_ability_titan_ground_stomp");
		add("female", 2, "head_kick", "female_ability_titan_head_kick");
		add("female", 3, "summoning_roar", "female_ability_titan_summoning_roar");
		add("female", 4, "wire_swing", "female_ability_titan_wire_swing");
		add("female", 5, "nape_covering", "female_abilitiy_titan_nape_covering");
		add("female", 6, "nape_hardening", "female_abilitiy_titan_nape_hardening");
		add("female", 7, "grab", "ability_titan_grab_female");
		add("female", 8, "wire_detach", "female_ability_titan_wire_detach");
		add("female", 9, "crystal_cocoon", "ability_titan_crystal_cocoon");
		add("colossal", 1, "steam", "ability_titan_colossal_steam");
		add("colossal", 2, "kick", "ability_titan_colossal_kick");
		add("colossal", 3, "ground_tear", "ability_titan_ground_tear");
		add("colossal", 4, "infernal_heat", "ability_titan_infernal_heat");
		add("beast", 1, "scatter_shot", "ability_titan_scatter_shot");
		add("beast", 2, "fire_scatter_shot", "ability_titan_scatter_shot_fire");
		add("beast", 3, "big_throw", "ability_titan_throw_big");
		add("beast", 4, "roar", "ability_titan_beast_roar");
		add("beast", 5, "awaken", "ability_titan_awaken");
		add("beast", 6, "stop", "ability_titan_stop");
		add("beast", 7, "continue", "ability_titan_continue");
		add("beast", 8, "regroup", "ability_titan_regroup");
		add("beast", 9, "target", "ability_titan_target");
		add("warhammer", 1, "hammer_attack", "ability_titan_hammer_attack");
		add("warhammer", 2, "ground_stomp", "ability_titan_ground_stomp_warhammer");
		add("warhammer", 3, "ground_smash", "ability_titan_ground_smash_warhammer");
		add("warhammer", 4, "piercing_thorns", "ability_titan_piercing_thorns");
		add("warhammer", 5, "spike_field", "ability_titan_spike_field");
		add("warhammer", 6, "hardened_block", "ability_titan_hardened_block");
		add("warhammer", 7, "control_center", "ability_titan_control_center");
		add("warhammer", 8, "giant_spike", "abilities_titan_giant_spike");
		add("warhammer", 9, "summon_hammer", "abilities_titan_summon_hammer");
	}

	private static void add(String titan, int number, String key, String texture) {
		TitanCodex.register(new DaotSkill("daot:" + titan + "/" + key, titan, number,
				ResourceLocation.fromNamespaceAndPath("dannys-aot", "textures/gui/" + texture + ".png")));
	}
}
