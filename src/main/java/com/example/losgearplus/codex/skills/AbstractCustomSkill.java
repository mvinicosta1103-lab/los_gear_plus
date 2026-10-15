package com.example.losgearplus.codex.skills;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.codex.CustomTitanSkill;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Base para as suas skills. O id vira {@code los_gear_plus:<path>} e o ícone é lido de
 * {@code assets/los_gear_plus/textures/gui/codex/<path>.png} (32x32). Nome e descrição vêm do lang:
 * {@code los_gear_plus.codex.skill.los_gear_plus.<path>.name / .desc}.
 */
public abstract class AbstractCustomSkill implements CustomTitanSkill {
	private final ResourceLocation id;
	private final ResourceLocation icon;
	private final Set<String> titans;
	private final int cooldownTicks;
	private final float staminaFraction;

	/**
	 * @param path            parte do id e do nome do arquivo do ícone
	 * @param titans          titãs que podem equipar (vazio = todos os suportados)
	 * @param cooldownTicks   recarga (20 = 1 s)
	 * @param staminaFraction custo em fração da stamina máxima (0.10 = 10%)
	 */
	protected AbstractCustomSkill(String path, Set<String> titans, int cooldownTicks, float staminaFraction) {
		this.id = LosGearPlus.id(path);
		this.icon = LosGearPlus.id("textures/gui/codex/" + path + ".png");
		this.titans = Set.copyOf(titans);
		this.cooldownTicks = cooldownTicks;
		this.staminaFraction = staminaFraction;
	}

	@Override
	public String id() {
		return id.toString();
	}

	@Override
	public String titanId() {
		return titans.size() == 1 ? titans.iterator().next() : ALL;
	}

	@Override
	public boolean compatibleWith(String titan) {
		return titans.isEmpty() || titans.contains(titan);
	}

	/** Titãs aceitos (vazio = todos). Usado pelo Codex para mostrar a compatibilidade. */
	public Set<String> titans() {
		return titans;
	}

	@Override
	public ResourceLocation icon() {
		return icon;
	}

	@Override
	public int cooldownTicks() {
		return cooldownTicks;
	}

	@Override
	public float staminaFraction() {
		return staminaFraction;
	}
}
