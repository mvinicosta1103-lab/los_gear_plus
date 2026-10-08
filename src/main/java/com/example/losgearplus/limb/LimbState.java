package com.example.losgearplus.limb;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Estado de membros de UM jogador: um {@link LimbStatus} e um progresso (0..1) por {@link LimbPart}.
 * As condições se somam (é só um conjunto de partes), não existem estados "combinados" pré-definidos.
 * O mesmo estado vale para a forma humana e para o titã (o titã lê/escreve no jogador).
 */
public final class LimbState {
	/** Instância compartilhada "tudo inteiro". Somente leitura (nunca altere). */
	public static final LimbState PRISTINE = new LimbState();

	public static final Codec<LimbState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.listOf().fieldOf("status").forGetter(LimbState::statusIds),
			Codec.FLOAT.listOf().fieldOf("progress").forGetter(LimbState::progressList)
	).apply(i, LimbState::fromLists));

	/** Menor tamanho visual de um toco que acabou de começar a crescer. */
	public static final float STUMP_MIN = 0.3f;

	private final LimbStatus[] status = new LimbStatus[LimbPart.COUNT];
	private final float[] progress = new float[LimbPart.COUNT];

	public LimbState() {
		Arrays.fill(status, LimbStatus.INTACT);
	}

	// ---- leitura ------------------------------------------------------------------------------------------

	public LimbStatus status(LimbPart p) { return status[p.ordinal()]; }
	public float progress(LimbPart p) { return progress[p.ordinal()]; }
	public boolean isIntact(LimbPart p) { return status[p.ordinal()] == LimbStatus.INTACT; }
	/** Perdida OU ainda crescendo: a parte não funciona. */
	public boolean isMissing(LimbPart p) { return !isIntact(p); }

	public boolean isPristine() {
		for (LimbStatus s : status) if (s != LimbStatus.INTACT) return false;
		return true;
	}

	/** O braço serve para segurar/usar item? Exige braço E antebraço inteiros. */
	public boolean armUsable(boolean left) {
		return isIntact(LimbPart.of(LimbPart.Kind.ARM, left, true)) && isIntact(LimbPart.of(LimbPart.Kind.ARM, left, false));
	}

	/** 1 = perna inteira, 0.5 = só falta a canela, 0 = perna toda perdida. */
	public float legValue(boolean left) {
		if (isMissing(LimbPart.of(LimbPart.Kind.LEG, left, true))) return 0f;
		if (isMissing(LimbPart.of(LimbPart.Kind.LEG, left, false))) return 0.5f;
		return 1f;
	}

	/** Quanto da visão deste olho foi perdida: 0 = vê, 1 = cego; crescendo volta gradualmente. */
	public float eyeLoss(boolean left) {
		LimbPart eye = left ? LimbPart.EYE_LEFT : LimbPart.EYE_RIGHT;
		switch (status(eye)) {
			case LOST: return 1f;
			case REGROWING: return 1f - progress(eye);
			default: return 0f;
		}
	}

	public boolean blind() { return isMissing(LimbPart.EYE_LEFT) && isMissing(LimbPart.EYE_RIGHT); }

	/** Muda só quando muda o STATUS de alguma parte (ignora o progresso). */
	public int statusSignature() {
		int h = 0;
		for (LimbStatus s : status) h = h * 3 + s.ordinal();
		return h;
	}

	/**
	 * Espessura visual do braço: enquanto o braço (upper) cresce ele é um mini-braço inteiro, que engrossa junto
	 * com o comprimento. Nos outros estados é 1 (o antebraço não dá para engrossar separado do braço).
	 */
	public float armThickness(boolean left) {
		LimbPart upper = LimbPart.of(LimbPart.Kind.ARM, left, true);
		return status(upper) == LimbStatus.REGROWING ? fraction(upper) : 1f;
	}

	public float legThickness(boolean left) {
		LimbPart upper = LimbPart.of(LimbPart.Kind.LEG, left, true);
		return status(upper) == LimbStatus.REGROWING ? fraction(upper) : 1f;
	}

	/** Comprimento visual do braço (0..1 do braço inteiro; o cotovelo fica em 0.5). */
	public float armLength(boolean left) {
		return length(LimbPart.of(LimbPart.Kind.ARM, left, true), LimbPart.of(LimbPart.Kind.ARM, left, false));
	}

	/** Comprimento visual da perna (0..1; o joelho fica em 0.5). */
	public float legLength(boolean left) {
		return length(LimbPart.of(LimbPart.Kind.LEG, left, true), LimbPart.of(LimbPart.Kind.LEG, left, false));
	}

	private float length(LimbPart upper, LimbPart lower) {
		float u = fraction(upper);
		if (!isIntact(upper)) return 0.5f * u;
		return 0.5f + 0.5f * fraction(lower);
	}

	/**
	 * Tamanho visual (escala uniforme) de uma parte: 1 inteira, 0 perdida e, enquanto cresce, de {@link #STUMP_MIN}
	 * até 1 (o mesmo crescimento que o osso do titã faz no steam heal). Usado pela renderização do corpo humano.
	 */
	public float growth(LimbPart p) {
		return fraction(p);
	}

	private float fraction(LimbPart p) {
		switch (status(p)) {
			case INTACT: return 1f;
			case REGROWING: return STUMP_MIN + (1f - STUMP_MIN) * progress(p);
			default: return 0f;
		}
	}

	// ---- escrita ------------------------------------------------------------------------------------------

	/** Perde a parte (e, se for um UPPER, o LOWER junto). True se algo mudou. */
	public boolean lose(LimbPart p) {
		boolean changed = set(p, LimbStatus.LOST);
		LimbPart child = p.child();
		if (child != null) changed |= set(child, LimbStatus.LOST);
		return changed;
	}

	/** Cura a parte na hora (comando/admin). Restaurar um LOWER exige o UPPER inteiro, então sobe junto. */
	public boolean restore(LimbPart p) {
		boolean changed = set(p, LimbStatus.INTACT);
		LimbPart parent = p.parent();
		if (parent != null) changed |= set(parent, LimbStatus.INTACT);
		return changed;
	}

	public boolean restoreAll() {
		boolean changed = false;
		for (LimbPart p : LimbPart.VALUES) changed |= set(p, LimbStatus.INTACT);
		return changed;
	}

	private boolean set(LimbPart p, LimbStatus s) {
		int i = p.ordinal();
		if (status[i] == s && progress[i] == 0f) return false;
		status[i] = s;
		progress[i] = 0f;
		return true;
	}

	/**
	 * Um tick de regeneração. {@code speed} = multiplicador (maestria x Steam Heal).
	 * PERDIDA vira toco (REGROWING, progresso 0); o toco cresce até 1 e vira INTACT. Um LOWER só começa
	 * quando o UPPER do mesmo lado está inteiro; membros diferentes e olhos crescem em paralelo.
	 * True se algo mudou.
	 */
	public boolean regrow(float speed) {
		if (speed <= 0f) return false;
		boolean changed = false;
		for (LimbPart p : LimbPart.VALUES) {
			int i = p.ordinal();
			if (status[i] == LimbStatus.INTACT) continue;
			LimbPart parent = p.parent();
			if (parent != null && status[parent.ordinal()] != LimbStatus.INTACT) continue;
			changed = true;
			if (status[i] == LimbStatus.LOST) {
				status[i] = LimbStatus.REGROWING;
				progress[i] = 0f;
				continue;
			}
			progress[i] += speed / LimbRules.regrowTicks(p);
			if (progress[i] >= 1f) {
				status[i] = LimbStatus.INTACT;
				progress[i] = 0f;
			}
		}
		return changed;
	}

	/**
	 * Custo em stamina (fração da máxima) do próximo tick de {@link #regrow(float)} com este {@code speed}:
	 * só partes que estão realmente crescendo (toco) e liberadas pelo UPPER, sem o desconto de maestria.
	 */
	public float regrowCost(float speed) {
		float cost = 0f;
		for (LimbPart p : LimbPart.VALUES) {
			int i = p.ordinal();
			if (status[i] != LimbStatus.REGROWING) continue;
			LimbPart parent = p.parent();
			if (parent != null && status[parent.ordinal()] != LimbStatus.INTACT) continue;
			cost += LimbRules.staminaFraction(p) * speed / LimbRules.regrowTicks(p);
		}
		return cost;
	}

	public void copyFrom(LimbState o) {
		System.arraycopy(o.status, 0, status, 0, status.length);
		System.arraycopy(o.progress, 0, progress, 0, progress.length);
	}

	// ---- (de)serialização ---------------------------------------------------------------------------------

	private List<Integer> statusIds() {
		List<Integer> l = new ArrayList<>(status.length);
		for (LimbStatus s : status) l.add(s.ordinal());
		return l;
	}

	private List<Float> progressList() {
		List<Float> l = new ArrayList<>(progress.length);
		for (float f : progress) l.add(f);
		return l;
	}

	/** Inverso de {@link LimbSyncPayload#of}: 10 bytes de status + 10 bytes de progresso (0..255). */
	public static LimbState fromBytes(byte[] d) {
		LimbState s = new LimbState();
		if (d.length < LimbPart.COUNT * 2) return s;
		for (int i = 0; i < LimbPart.COUNT; i++) {
			s.status[i] = LimbStatus.byId(d[i]);
			s.progress[i] = (d[LimbPart.COUNT + i] & 0xFF) / 255f;
		}
		return s;
	}

	private static LimbState fromLists(List<Integer> st, List<Float> pr) {
		LimbState s = new LimbState();
		for (int i = 0; i < LimbPart.COUNT; i++) {
			if (i < st.size()) s.status[i] = LimbStatus.byId(st.get(i));
			if (i < pr.size()) s.progress[i] = Math.max(0f, Math.min(1f, pr.get(i)));
		}
		return s;
	}
}
