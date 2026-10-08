package com.example.losgearplus.client.limb;

import com.example.losgearplus.limb.LimbPart;
import com.example.losgearplus.limb.LimbState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Como braços e pernas do corpo HUMANO aparecem conforme o {@link LimbState} (o mesmo estado que o titã usa):
 *
 * <ul>
 *   <li><b>Perdido (braço/perna inteiro):</b> o membro some do corpo (modelo, manga/calça e armadura).</li>
 *   <li><b>Antebraço/canela perdido:</b> corte limpo no cotovelo/joelho. O membro vanilla é escondido e o
 *       {@link HumanLimbLayer} desenha só a metade de cima, com a mesma textura da skin.</li>
 *   <li><b>Crescendo (shifter, Steam Heal):</b> igual ao osso do titã: o braço/perna começa pequeno
 *       ({@link LimbState#STUMP_MIN}) e cresce de forma uniforme até 100%; depois o antebraço/canela vai saindo do
 *       cotovelo/joelho até ficar inteiro.</li>
 * </ul>
 *
 * Alturas em pixels do modelo do jogador (membro inteiro = 12, até o cotovelo/joelho = 6).
 */
public final class HumanLimbRender {
	private HumanLimbRender() {}

	public static final float FULL_PX = 12f;
	public static final float HALF_PX = 6f;
	/** Escala do membro que sumiu (não pode ser 0: a matriz de normais do render ficaria singular). */
	private static final float GONE_SCALE = 0.001f;

	/**
	 * @param hidden membro ausente (não desenha nada)
	 * @param height altura visível em pixels (12 = inteiro)
	 * @param scale  escala uniforme (1 = tamanho normal; menor que 1 só enquanto o braço/perna cresce)
	 */
	public record View(boolean hidden, float height, float scale) {
		/** O membro vanilla não serve: quem desenha é o {@link HumanLimbLayer} (corte ou crescendo). */
		public boolean partial() {
			return !hidden && height < FULL_PX - 0.01f;
		}
	}

	public static final View FULL = new View(false, FULL_PX, 1f);
	public static final View GONE = new View(true, 0f, 0f);

	public static View view(LimbState s, LimbPart.Kind kind, boolean left) {
		if (s.isPristine()) return FULL;
		LimbPart upper = LimbPart.of(kind, left, true);
		LimbPart lower = LimbPart.of(kind, left, false);
		switch (s.status(upper)) {
			case LOST:
				return GONE;
			case REGROWING:
				// braço/perna crescendo: a metade de cima, em escala uniforme de pequeno até 100%
				return new View(false, HALF_PX, s.growth(upper));
			default:
				break;
		}
		switch (s.status(lower)) {
			case LOST:
				return new View(false, HALF_PX, 1f);
			case REGROWING:
				// antebraço/canela saindo do cotovelo/joelho até completar o membro
				return new View(false, HALF_PX + (FULL_PX - HALF_PX) * s.progress(lower), 1f);
			default:
				return FULL;
		}
	}

	/**
	 * Parte vanilla do {@code PlayerModel} (e a manga/calça). O modelo é compartilhado entre todos os jogadores,
	 * então os valores são SEMPRE reescritos (inclusive 1.0). Só some quando o membro está ausente ou cortado
	 * (nesse caso o {@link HumanLimbLayer} desenha o que sobrou).
	 */
	public static void applyBody(ModelPart part, ModelPart overlay, View v) {
		float s = v.hidden() ? GONE_SCALE : v.scale();
		setScale(part, s);
		setScale(overlay, s);
		if (v.hidden() || v.partial()) {
			part.visible = false;
			overlay.visible = false;
		}
	}

	/**
	 * Parte do modelo da ARMADURA (ela copia posição, rotação e escala do modelo do jogador). Cortado: a armadura
	 * encolhe junto com o membro, e a bota (que fica no pé) some, porque o pé não existe mais.
	 */
	public static void applyArmor(ModelPart part, View v, boolean footPiece) {
		if (v.hidden() || (footPiece && v.partial())) {
			part.visible = false;
			setScale(part, GONE_SCALE);
			return;
		}
		if (v.partial()) {
			part.xScale = v.scale();
			part.zScale = v.scale();
			part.yScale = v.scale() * v.height() / FULL_PX;
		}
	}

	private static void setScale(ModelPart part, float s) {
		part.xScale = s;
		part.yScale = s;
		part.zScale = s;
	}

	// ---- peças cortadas (modelos próprios, com a textura da skin) -----------------------------------------------

	private static final Map<String, ModelPart> CUTS = new HashMap<>();

	/**
	 * Metade de cima (ou o membro até {@code height} px) do braço/perna: mesma caixa do {@code PlayerModel} só que
	 * mais baixa, com o mesmo trecho da skin. {@code overlay} = manga/calça (camada externa). Guardadas em cache por
	 * altura (em passos de 1/4 de pixel). Partilhadas: quem usa copia a pose para elas antes de desenhar.
	 */
	public static ModelPart cut(LimbPart.Kind kind, boolean left, boolean slim, boolean overlay, float height) {
		int q = Math.max(1, Math.round(height * 4f));
		String key = kind.ordinal() + (left ? "L" : "R") + (slim ? "s" : "w") + (overlay ? "o" : "b") + q;
		ModelPart part = CUTS.get(key);
		if (part == null) {
			part = build(kind, left, slim, overlay, q / 4f);
			CUTS.put(key, part);
		}
		return part;
	}

	private static ModelPart build(LimbPart.Kind kind, boolean left, boolean slim, boolean overlay, float height) {
		boolean arm = kind == LimbPart.Kind.ARM;
		// Posição de cada membro na skin 64x64 (a mesma do PlayerModel.createMesh do jogo).
		int u;
		int v;
		if (arm) {
			if (overlay) {
				u = left ? 48 : 40;
				v = left ? 48 : 32;
			} else {
				u = left ? 32 : 40;
				v = left ? 48 : 16;
			}
		} else if (overlay) {
			u = 0;
			v = left ? 48 : 32;
		} else {
			u = left ? 16 : 0;
			v = left ? 48 : 16;
		}
		float w = arm && slim ? 3f : 4f;
		float x = arm ? (left ? -1f : (slim ? -2f : -3f)) : -2f;
		float y = arm ? -2f : 0f; // o braço começa 2 px acima do ombro; a perna, no quadril
		CubeDeformation grow = overlay ? new CubeDeformation(0.25f) : CubeDeformation.NONE;
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("limb",
				CubeListBuilder.create().texOffs(u, v).addBox(x, y, -2f, w, height, 4f, grow), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("limb");
	}
}
