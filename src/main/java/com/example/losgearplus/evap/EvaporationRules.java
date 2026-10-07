package com.example.losgearplus.evap;

/** Números da "evaporação" dos corpos de titã (ficam pretos, soltam fumaça e somem). Ajuste aqui para balancear. */
public final class EvaporationRules {
	private EvaporationRules() {}

	/** Titã de shifter: ticks entre o portador sair do corpo e o corpo começar a evaporar (20 ticks = 1 s). */
	public static final int SHIFTER_DELAY_TICKS = 20;
	/** Duração da evaporação (do primeiro escurecimento até sumir de vez). 140 ticks = 7 s. */
	public static final int DURATION_TICKS = 140;
	/** Titã puro: a evaporação começa quando o tempo de morte (deathTime) chega a este valor (a queda dura ~46 ticks). */
	public static final int PURE_START_DEATH_TICKS = 60;
	/** Fração da duração em que o corpo já está totalmente preto (o resto é só fumaça até sumir). */
	public static final float FULL_BLACK_AT = 0.65f;
	/** Brilho mínimo do corpo preto (0 = preto absoluto, 1 = sem escurecer). */
	public static final float MIN_BRIGHTNESS = 0.04f;

	/** Quanto o corpo está escuro (0 = normal, 1 = totalmente preto) para um progresso 0..1 da evaporação. */
	public static float darkness(float progress) {
		float t = Math.max(0f, Math.min(1f, progress / FULL_BLACK_AT));
		return t * t * (3f - 2f * t); // smoothstep: começa devagar e fecha rápido
	}

	/** Multiplica o RGB de uma cor ARGB pelo brilho que sobra com esta escuridão (a transparência não muda). */
	public static int shade(int argb, float darkness) {
		float f = 1f - darkness * (1f - MIN_BRIGHTNESS);
		int a = argb >>> 24;
		int r = Math.round(((argb >> 16) & 0xFF) * f);
		int g = Math.round(((argb >> 8) & 0xFF) * f);
		int b = Math.round((argb & 0xFF) * f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
