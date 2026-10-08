package com.example.losgearplus.limb;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/** Todos os números e regras derivadas do conjunto de partes perdidas. Ajuste aqui para balancear. */
public final class LimbRules {
	private LimbRules() {}

	// ---- gatilho de dano (chance por golpe que acerta) ----------------------------------------------------
	// Humanos SÓ perdem membro para titãs (puros ou shifters). Titãs de shifter perdem para lâmina, titã,
	// explosão (lança do trovão, canhão) e qualquer outro dano forte, e o ponto atingido decide a parte.
	/** Chance base (por golpe que acerta um membro) de um titã de shifter perder a parte, conforme a origem. */
	public static final float BLADE_VS_TITAN = 0.20f;
	public static final float BLAST_VS_TITAN = 0.35f;
	/** Outros danos (soco, flecha, mob...): só entram pelo bônus de dano abaixo. */
	public static final float OTHER_VS_TITAN = 0.0f;
	/** Titã (puro ou shifter) acertando alguém: vale para humano e para titã de shifter. */
	public static final float TITAN_HIT = 0.15f;
	/** Bônus de chance = (dano / vida máxima) x escala, limitado ao máximo. Só vale contra titã de shifter. */
	public static final float DAMAGE_CHANCE_SCALE = 2.0f;
	public static final float DAMAGE_CHANCE_MAX_BONUS = 0.35f;
	/** Dano "OTHER" abaixo desta fração da vida máxima do titã nunca decepa. */
	public static final float OTHER_MIN_DAMAGE_FRACTION = 0.03f;
	/** Dano EXTRA (antes da armadura) que o humano leva ao perder um membro (braço/perna inteiro; metade+ no antebraço/canela/olho). 0 = sem dano extra. */
	public static final float HUMAN_LOSS_EXTRA_DAMAGE = 6.0f;
	/** Ticks mínimos entre duas perdas da mesma vítima (evita arrancar tudo num golpe em área). */
	public static final int LOSS_COOLDOWN_TICKS = 40;

	// ---- onde o titã foi atingido (altura relativa ao corpo: 0 = pés, 1 = topo da cabeça) -------------------
	/** Acima disto é cabeça: só o olho (golpe de lâmina pela frente) pode ser decepado. */
	public static final float HIT_HEAD_MIN = 0.88f;
	/** Faixa dos braços: de HIT_ARM_MIN até a cabeça. Acima de HIT_ARM_UPPER_MIN é o braço inteiro (ombro), abaixo é o antebraço. */
	public static final float HIT_ARM_MIN = 0.45f;
	public static final float HIT_ARM_UPPER_MIN = 0.65f;
	/** Na faixa dos braços, o golpe precisa estar a pelo menos esta fração da meia-largura para o lado (|lado| 0..1); no meio é tronco. */
	public static final float HIT_ARM_SIDE_MIN = 0.40f;
	/** Abaixo da faixa dos braços são as pernas. Acima disto é a coxa (perna inteira); abaixo, a canela. */
	public static final float HIT_LEG_UPPER_MIN = 0.22f;
	/** Se o esquerdo/direito sair invertido nos testes, troque para true. */
	public static final boolean HIT_SWAP_SIDES = false;

	/** Chance final de perda contra titã de shifter: base da origem + bônus pelo dano relativo à vida máxima. */
	public static float titanVictimChance(float base, float amount, float maxHealth) {
		float frac = maxHealth > 0f ? amount / maxHealth : 0f;
		return Math.min(1f, base + Math.min(DAMAGE_CHANCE_MAX_BONUS, frac * DAMAGE_CHANCE_SCALE));
	}

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

	/**
	 * Passiva: fração da velocidade com que um shifter regenera os membros SEM Steam Heal, mas só na forma de titã
	 * (montado no titã dele). Solta fumaça enquanto cresce. O Steam Heal ligado regenera na velocidade cheia (1.0).
	 * Só vale com a passiva liberada pelas gamerules ({@link LimbGameRules}: ligada e maestria >= mínima); senão o
	 * membro só volta com o Steam Heal (J), que funciona na forma humana e na de titã. Use 0 para desligar a passiva.
	 */
	public static final float PASSIVE_REGROW = 0.15f;
	/**
	 * Aviso de progresso: a cada quantos % de regeneração o jogador recebe o banner "Regenerando Braço esquerdo: 50%".
	 * Também avisa quando o membro começa a crescer (0%) e quando fica recuperado. Menor = mais avisos (1..100).
	 */
	public static final int REGROW_ALERT_STEP_PERCENT = 25;
	/** Ticks entre os avisos "use o Steam Heal" enquanto o membro está travado (sem passiva liberada). */
	public static final int PASSIVE_HINT_INTERVAL_TICKS = 200;
	/** Ticks entre as baforadas de fumaça da regeneração passiva. */
	public static final int PASSIVE_SMOKE_INTERVAL_TICKS = 6;

	public static int regrowTicks(LimbPart p) {
		return REGROW_TICKS[p.ordinal()];
	}

	public static float regrowSpeed(int masteryLevel) {
		return MASTERY_REGROW[Math.max(0, Math.min(MASTERY_REGROW.length - 1, masteryLevel))];
	}

	// ---- membro decepado no chão (peça solta do titã) -----------------------------------------------------
	/** Ticks que a peça solta fica no chão antes de sumir sozinha (20 ticks = 1 s). Use 0 ou menos para nunca sumir. */
	public static final int DEBRIS_LIFETIME_TICKS = 20 * 30;
	/** Ticks finais em que a peça encolhe e solta vapor até desaparecer (precisa ser menor que o tempo de vida). */
	public static final int DEBRIS_FADE_TICKS = 20 * 6;
	/** Intervalo (ticks) entre vapores no começo e no fim da evaporação: vai ficando mais denso. */
	public static final int DEBRIS_STEAM_INTERVAL_START = 8;
	public static final int DEBRIS_STEAM_INTERVAL_END = 2;

	// ---- membro decepado no chão (corpo humano) -----------------------------------------------------------
	/**
	 * Humano que perde braço/perna solta a peça decepada no chão (só visual, no cliente). Ela some com vapor depois de
	 * {@link #DEBRIS_LIFETIME_TICKS} (mesmos tempos do titã) ou na hora em que o membro começa a crescer de volta.
	 * Use false para só remover o membro do corpo, sem a peça no chão.
	 */
	public static final boolean HUMAN_DEBRIS_ENABLED = true;
	/** Máximo de peças soltas ao mesmo tempo no cliente (as mais antigas somem primeiro). */
	public static final int HUMAN_DEBRIS_MAX = 48;

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
	/** true = qualquer braço perdido já impede socar/defender; false = basta UM braço. (Soco e guarda, não habilidades.) */
	public static final boolean TITAN_ARM_ACTIONS_NEED_BOTH_ARMS = true;
	/**
	 * true = qualquer braço perdido já impede as habilidades de braço; false (padrão) = só ficam impossíveis quando
	 * os DOIS braços foram removidos (com um braço o titã ainda usa as habilidades).
	 */
	public static final boolean TITAN_ABILITIES_NEED_BOTH_ARMS = false;

	/** Soco / guarda: respeita {@link #TITAN_ARM_ACTIONS_NEED_BOTH_ARMS}. */
	public static boolean titanArmsOk(LimbState s) {
		boolean l = s.armUsable(true);
		boolean r = s.armUsable(false);
		return TITAN_ARM_ACTIONS_NEED_BOTH_ARMS ? (l && r) : (l || r);
	}

	/** Habilidades: respeita {@link #TITAN_ABILITIES_NEED_BOTH_ARMS}. Sem nenhum braço funcionando nunca passa. */
	public static boolean titanAbilitiesOk(LimbState s) {
		boolean l = s.armUsable(true);
		boolean r = s.armUsable(false);
		return TITAN_ABILITIES_NEED_BOTH_ARMS ? (l && r) : (l || r);
	}

	/**
	 * A habilidade {@code n} deste titã usa os braços? Padrão: sim. Exceções (funcionam até SEM braços):
	 * gritos e berserk; Colossal 1 (vapor), 2 (chute) e 4 (calor infernal); Beast 4 (rugido); Female 3 (grito
	 * convocador); Attack 9 (berserk). O grito do Attack/Female/Royal e o impulso (dash) do titã não passam por
	 * aqui (têm payload próprio) e nunca são bloqueados por braços.
	 */
	public static boolean titanAbilityNeedsArms(String titanClass, int n) {
		switch (titanClass) {
			case "ColossalTitanEntity": return n == 3;
			case "BeastTitanEntity": return n != 4;
			case "FemaleTitanEntity": return n != 3;
			case "AttackTitanEntity": return n != 9;
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