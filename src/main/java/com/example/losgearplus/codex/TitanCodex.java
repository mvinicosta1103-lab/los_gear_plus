package com.example.losgearplus.codex;

import com.example.losgearplus.codex.skills.CodexSkills;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registro de todas as skills (rodam nos DOIS lados: o cliente lê o mesmo catálogo para desenhar o Codex; o
 * servidor só manda ids). Para adicionar uma skill nova: crie a classe em {@code codex/skills} e registre-a em
 * {@link CodexSkills}. Outros addons também podem chamar {@link #register} no onInitialize deles.
 */
public final class TitanCodex {
	private TitanCodex() {}

	private static final Map<String, TitanSkill> BY_ID = new LinkedHashMap<>();
	private static boolean initialized;

	public static synchronized void init() {
		if (initialized) return;
		initialized = true;
		DaotSkills.registerAll();
		CodexSkills.registerAll();
	}

	/** API pública: registra uma skill (id repetido substitui a anterior). */
	public static synchronized void register(TitanSkill skill) {
		BY_ID.put(skill.id(), skill);
	}

	public static synchronized Optional<TitanSkill> find(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static synchronized Collection<TitanSkill> all() {
		return List.copyOf(BY_ID.values());
	}

	/** Skills que podem ser equipadas neste titã, na ordem de registro (originais primeiro). */
	public static synchronized List<TitanSkill> skillsFor(String titan) {
		List<TitanSkill> out = new ArrayList<>();
		for (TitanSkill s : BY_ID.values()) if (s.compatibleWith(titan)) out.add(s);
		return out;
	}

	/** A habilidade original do DAOT que mora neste slot (0-based) quando o jogador não mexeu nele; null se vazio. */
	public static synchronized TitanSkill defaultSkill(String titan, int slot) {
		for (TitanSkill s : BY_ID.values()) {
			if (!s.isCustom() && s.titanId().equals(titan) && s.daotAbilityNumber() == slot + 1) return s;
		}
		return null;
	}
}
