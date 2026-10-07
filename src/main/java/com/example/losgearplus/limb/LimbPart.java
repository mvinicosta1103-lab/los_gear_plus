package com.example.losgearplus.limb;

import java.util.Locale;

/**
 * As 10 partes que o jogador (ou o titã dele) pode perder.
 *
 * <p>Hierarquia dos membros: o "upper" é o membro inteiro (braço / perna) e o "lower" é só a parte de baixo
 * (antebraço / canela). Perder o UPPER leva o LOWER junto; perder só o LOWER mantém o UPPER. A regra mora em
 * {@link LimbState#lose(LimbPart)}.
 *
 * <p>A ordem das constantes importa: todo UPPER vem antes do seu LOWER (a regeneração depende disso).
 */
public enum LimbPart {
	ARM_UPPER_LEFT(Kind.ARM, true, true),
	ARM_UPPER_RIGHT(Kind.ARM, false, true),
	ARM_LOWER_LEFT(Kind.ARM, true, false),
	ARM_LOWER_RIGHT(Kind.ARM, false, false),
	LEG_UPPER_LEFT(Kind.LEG, true, true),
	LEG_UPPER_RIGHT(Kind.LEG, false, true),
	LEG_LOWER_LEFT(Kind.LEG, true, false),
	LEG_LOWER_RIGHT(Kind.LEG, false, false),
	EYE_LEFT(Kind.EYE, true, true),
	EYE_RIGHT(Kind.EYE, false, true);

	public enum Kind { ARM, LEG, EYE }

	public static final LimbPart[] VALUES = values();
	public static final int COUNT = VALUES.length;

	private final Kind kind;
	private final boolean left;
	private final boolean upper;
	private final String id;

	LimbPart(Kind kind, boolean left, boolean upper) {
		this.kind = kind;
		this.left = left;
		this.upper = upper;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public Kind kind() { return kind; }
	public boolean isArm() { return kind == Kind.ARM; }
	public boolean isLeg() { return kind == Kind.LEG; }
	public boolean isEye() { return kind == Kind.EYE; }
	public boolean isLeft() { return left; }
	/** Só faz sentido para braços e pernas (olhos contam como "upper"). */
	public boolean isUpper() { return upper; }

	/** Ex.: {@code arm_upper_left}. Usado no comando e nas chaves de tradução. */
	public String id() { return id; }

	/** Para um UPPER de braço/perna: o LOWER do mesmo lado. Senão null. */
	public LimbPart child() {
		return (kind == Kind.EYE || !upper) ? null : of(kind, left, false);
	}

	/** Para um LOWER de braço/perna: o UPPER do mesmo lado. Senão null. */
	public LimbPart parent() {
		return (kind == Kind.EYE || upper) ? null : of(kind, left, true);
	}

	public static LimbPart of(Kind kind, boolean left, boolean upper) {
		for (LimbPart p : VALUES) {
			if (p.kind == kind && p.left == left && p.upper == upper) return p;
		}
		throw new IllegalArgumentException(kind + " " + left + " " + upper);
	}

	public static LimbPart byId(String id) {
		for (LimbPart p : VALUES) {
			if (p.id.equals(id)) return p;
		}
		return null;
	}
}
