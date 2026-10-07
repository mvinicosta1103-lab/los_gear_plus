package com.example.losgearplus.limb;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/** Todos os números e regras derivadas do conjunto de partes perdidas. Ajuste aqui para balancear. */
public final class LimbRules {
	private LimbRules() {}

	// ---- gatilho de dano (chance por golpe que acerta) ----------------------------------------------------
	public static final float BLADE_VS_HUMAN = 0.25f;
	public static final float BLADE_VS_TITAN = 0.20f;
	public static final float TITAN_HIT = 0.15f;
	/** Ticks mínimos entre duas perdas da mesma vítima (evita arrancar tudo num golpe em área). */
	public static final int LOSS_COOLDOWN_TICKS = 40;

	/** Peso de cada parte no sorteio (só entram partes ainda inteiras). */
	public static int dropWeight(LimbPart p) {
		return p.isEye() ? 1 : (p.isUpper() ? 1 : 2);
	}

	// ---- regeneração --------------------------------------------------------------------------------------
	/** Ticks para uma parte crescer de 0 a 1 com multiplicador 1.0 (indexado por LimbPart.ordinal()). */
	private static final int[] REGROW_TICKS = {
			1200, 1200,   // braço (upper) E/D
			800, 800,     // antebraço (lower) E/D
			1600, 1600,   // perna (upper) E/D
			1000, 1000,   // canela (lower) E/D
			1400, 1400    // olhos
	};

	/** Multiplicador por nível de maestria (0..9): quanto mais maestria, mais rápido. */
	private static final float[] MASTERY_REGROW = { 0.5f, 0.6f, 0.75f, 0.9f, 1.1f, 1.3f, 1.6f, 2.0f, 2.5f, 3.5f };

	/** Fração da velocidade que um shifter regenera SEM Steam Heal (0 = só com Steam Heal ligado). */
	public static final float PASSIVE_REGROW = 0f;

	public static int regrowTicks(LimbPart p) {
		return REGROW_TICKS[p.ordinal()];
	}

	public static float regrowSpeed(int masteryLevel) {
		return MASTERY_REGROW[Math.max(0, Math.min(MASTERY_REGROW.length - 1, masteryLevel))];
	}

	// ---- stamina da regeneração ---------------------------------------------------------------------------
	/** Fração da stamina MÁXIMA gasta para uma parte crescer de 0 a 1 (sem desconto de maestria). */
	private static final float[] STAMINA_FRACTION = {
			0.20f, 0.20f,   // braço
			0.12f, 0.12f,   // antebraço
			0.25f, 0.25f,   // perna
			0.15f, 0.15f,   // canela
			0.15f, 0.15f    // olhos
	};

	/** Desconto por maestria: 1 = custo cheio, 0 = de graça (nível 9 não gasta stamina). */
	private static final float[] STAMINA_MASTERY_FACTOR = { 1.0f, 0.85f, 0.70f, 0.58f, 0.46f, 0.35f, 0.25f, 0.16f, 0.08f, 0f };

	public static float staminaFraction(LimbPart p) {
		return STAMINA_FRACTION[p.ordinal()];
	}

	public static float staminaFactor(int ruleLevel) {
		return STAMINA_MASTERY_FACTOR[Math.max(0, Math.min(STAMINA_MASTERY_FACTOR.length - 1, ruleLevel))];
	}

	// ---- mãos ---------------------------------------------------------------------------------------------

	/** A mão {@code hand} funciona? (depende de qual braço é o principal nas opções do jogador). */
	public static boolean handUsable(Player p, InteractionHand hand) {
		boolean right = (hand == InteractionHand.MAIN_HAND) == (p.getMainArm() == HumanoidArm.RIGHT);
		return LimbData.of(p).armUsable(!right);
	}

	/**
	 * Se SÓ UM braço funciona, ele vira o principal por obrigação (a outra mão fica inutilizável). Com os dois
	 * funcionando (ou nenhum) vale a escolha normal do jogador. Volta ao normal sozinho quando o braço cresce.
	 */
	public static HumanoidArm forcedMainArm(LimbState s) {
		boolean left = s.armUsable(true);
		boolean right = s.armUsable(false);
		if (left == right) return null;
		return left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
	}

	/** Sem nenhum braço funcionando. */
	public static boolean noArms(Player p) {
		LimbState s = LimbData.of(p);
		return !s.armUsable(true) && !s.armUsable(false);
	}

	// ---- pernas -------------------------------------------------------------------------------------------

	/** Média das duas pernas: 1 = normal, 0 = sem pernas. */
	public static float mobility(LimbState s) {
		return (s.legValue(true) + s.legValue(false)) / 2f;
	}

	/** Titã que ajoelha até recuperar as pernas: uma perna inteira perdida, ou as duas canelas. */
	public static boolean mustKneel(LimbState s) {
		return mobility(s) <= 0.5f;
	}

	/** Velocidade do titã: ajoelhado quase não sai do lugar. */
	public static float titanSpeedMultiplier(LimbState s) {
		return mustKneel(s) ? 0.10f : speedMultiplier(s);
	}

	// ---- braços do titã -----------------------------------------------------------------------------------
	/** true = qualquer braço perdido já impede socar/defender/habilidades de braço; false = basta UM braço. */
	public static final boolean TITAN_ARM_ACTIONS_NEED_BOTH_ARMS = true;

	public static boolean titanArmsOk(LimbState s) {
		boolean l = s.armUsable(true);
		boolean r = s.armUsable(false);
		return TITAN_ARM_ACTIONS_NEED_BOTH_ARMS ? (l && r) : (l || r);
	}

	/**
	 * A habilidade {@code n} deste titã usa os braços? Padrão: sim. Exceções (não usam braço): Colossal 1 (vapor),
	 * 2 (chute) e 4 (calor infernal); Beast 4 (rugido).
	 */
	public static boolean titanAbilityNeedsArms(String titanClass, int n) {
		switch (titanClass) {
			case "ColossalTitanEntity": return n == 3;
			case "BeastTitanEntity": return n != 4;
			default: return true;
		}
	}

	/** Habilidades que usam as pernas (não funcionam ajoelhado): Colossal 2 (chute). */
	public static boolean titanAbilityNeedsLegs(String titanClass, int n) {
		return titanClass.equals("ColossalTitanEntity") && n == 2;
	}

	/** Só rasteja: as duas pernas com pelo menos metade perdida. */
	public static boolean mustCrawl(LimbState s) {
		return s.legValue(true) <= 0.5f && s.legValue(false) <= 0.5f;
	}

	public static boolean cannotSprint(LimbState s) {
		return mobility(s) <= 0.5f;
	}

	public static float speedMultiplier(LimbState s) {
		float m = mobility(s);
		if (m >= 1f) return 1f;
		if (mustCrawl(s)) return 0.15f + 0.2f * m;
		return 0.15f + 0.85f * (float) Math.pow(m, 1.5);
	}

	/** 1 = pula normal; meia perna perdida pula pouco; perna inteira perdida não pula. */
	public static float jumpMultiplier(LimbState s) {
		return Math.max(0f, Math.min(1f, (mobility(s) - 0.5f) * 2f));
	}
}
