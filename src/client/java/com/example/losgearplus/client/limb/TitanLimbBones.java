package com.example.losgearplus.client.limb;

import com.example.losgearplus.limb.LimbLookup;
import com.example.losgearplus.limb.LimbPart;
import com.example.losgearplus.limb.LimbRules;
import com.example.losgearplus.limb.LimbState;
import com.example.losgearplus.limb.LimbStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Membros do titã shifter, no cliente. Por quadro, depois das animações do GeckoLib:
 * <ol>
 *   <li><b>Decepar:</b> quando uma parte passa de inteira a perdida, solta o osso como peça física do
 *       {@code TitanDebris} do DAOT (ver {@link TitanDebrisBridge}); ela cai, fica no chão e some com vapor quando o
 *       membro começa a crescer.</li>
 *   <li><b>Esconder/crescer:</b> osso perdido fica escondido (leva os filhos); osso crescendo é escalado em volta do
 *       pivô (igual ao {@code showLimb} dos pure titans).</li>
 *   <li><b>Ajoelhar:</b> pernas cortadas (ver {@link LimbRules#mustKneel}): a raiz desce até o joelho tocar o chão e
 *       canela e pé dobram para trás. O jogador e os hitboxes de nuca/olho seguem os bones, então descem junto.</li>
 * </ol>
 * Os nomes de bones e a "frente" de cada modelo ({@code fz}: -1 normal, +1 espelhado) vêm dos {@code geo.json}.
 */
public final class TitanLimbBones {
	private TitanLimbBones() {}

	private static final Logger LOG = LoggerFactory.getLogger("los_gear_plus");

	/** {@code end} = osso da ponta (mão/pé), usado só para o eixo da peça solta. */
	private record Limb(String upper, String lower, String end) {}

	private record Rig(Limb armLeft, Limb armRight, Limb legLeft, Limb legRight, String root, float fz) {}

	private static final Map<String, Rig> RIGS = new HashMap<>();
	private static final Map<ResourceLocation, Rig> BY_MODEL = new HashMap<>();
	/** Titãs (id) que tiveram ossos alterados: precisam ser restaurados quando tudo voltar. */
	private static final Set<Integer> TOUCHED = new HashSet<>();
	/** Máscara de partes ausentes no quadro anterior, por titã (detecta o instante do corte). */
	private static final Map<Integer, Integer> MASKS = new HashMap<>();
	private static final Map<Integer, Kneel> KNEEL = new HashMap<>();
	/** Vida de cada peça solta (por titã): quando nasceu, último vapor e escala-base. Chave por identidade. */
	private static final Map<Integer, Map<Object, Life>> LIVES = new HashMap<>();

	private static final class Life {
		final float born;
		float lastSteam;
		float[] baseScale;

		Life(float born) {
			this.born = born;
			this.lastSteam = born;
		}
	}

	static {
		Limb[] std = {
				new Limb("arm_l", "forearm_l", "hand_l"), new Limb("arm_r", "forearm_r", "hand_r"),
				new Limb("leg_l", "leg2_l", "heel_l"), new Limb("leg_r", "leg2_r", "heel_r") };
		Rig attackLike = new Rig(std[0], std[1], std[2], std[3], "main", -1f);
		for (String n : new String[] { "attacktitan2", "armoredtitan", "femaletitan", "ogre", "beast_titan", "oattack", "tung" }) {
			RIGS.put(n, attackLike);
		}
		RIGS.put("colossal", new Rig(
				new Limb("lefthand", "lowerlefthand", "leftwrist"), new Limb("righthand", "lowerrighthand", "rightwrist"),
				new Limb("leftleg", "lowerleftleg", "leftfoot"), new Limb("rightleg", "lowerrightleg", "rightfoot"),
				"body", -1f));
		RIGS.put("warhammertitan", new Rig(
				new Limb("lefthand", "lefthand_2", "leftfist"), new Limb("righthand", "righthand_2", "rightfist"),
				new Limb("leftleg", "leftleg_2", "leftfeet"), new Limb("rightleg", "rightleg_2", "rightfeet"),
				"body", -1f));
		Limb[] num = {
				new Limb("lefthand", "lefthand2", "lefthand3"), new Limb("righthand", "righthand2", "righthand3"),
				new Limb("leftleg", "leftleg2", "leftleg3"), new Limb("rightleg", "rightleg2", "rightleg3") };
		RIGS.put("jawtitan", new Rig(num[0], num[1], num[2], num[3], "body", -1f));
		// A família Cart tem o modelo espelhado (frente em +Z), igual ao "flippedRoot" dos rigs do DAOT.
		Rig cart = new Rig(num[0], num[1], num[2], num[3], "body", +1f);
		for (String n : new String[] { "cart", "cart_cannon", "cart_cargo", "cart_turret" }) RIGS.put(n, cart);
	}

	public static void clear() {
		TOUCHED.clear();
		MASKS.clear();
		KNEEL.clear();
		LIVES.clear();
	}

	/** Chamado depois das animações do GeckoLib, uma vez por quadro, para cada titã shifter. */
	@SuppressWarnings("unchecked")
	public static void apply(GeoModel<?> model, Entity titan, float partialTick) {
		LimbState st = LimbLookup.ofTitan(titan);
		int id = titan.getId();
		int mask = missingMask(st);
		Integer prev = MASKS.put(id, mask);
		boolean idle = mask == 0 && (prev == null || prev == 0) && !TOUCHED.contains(id) && !KNEEL.containsKey(id);
		if (idle) return;

		ResourceLocation res = ((GeoModel<GeoAnimatable>) model).getModelResource((GeoAnimatable) titan);
		Rig rig = BY_MODEL.computeIfAbsent(res, r -> {
			String file = r.getPath().substring(r.getPath().lastIndexOf('/') + 1);
			int dot = file.indexOf('.');
			return RIGS.get(dot < 0 ? file : file.substring(0, dot));
		});
		if (rig == null) return;

		// 1) o instante do corte vira peça solta (antes de esconder, com os ossos ainda na pose da animação)
		if (prev != null && (mask & ~prev) != 0 && TitanDebrisBridge.ready()) {
			spawnDebris(model, (LivingEntity) titan, rig, mask & ~prev);
		}

		// 2) esconder / crescer
		if (mask != 0) TOUCHED.add(id); else TOUCHED.remove(id);
		limb(model, rig.armLeft, st, LimbPart.ARM_UPPER_LEFT, LimbPart.ARM_LOWER_LEFT);
		limb(model, rig.armRight, st, LimbPart.ARM_UPPER_RIGHT, LimbPart.ARM_LOWER_RIGHT);
		limb(model, rig.legLeft, st, LimbPart.LEG_UPPER_LEFT, LimbPart.LEG_LOWER_LEFT);
		limb(model, rig.legRight, st, LimbPart.LEG_UPPER_RIGHT, LimbPart.LEG_LOWER_RIGHT);

		// 3) ajoelhar
		Kneel kn = KNEEL.computeIfAbsent(id, k -> new Kneel());
		kn.advance(LimbRules.mustKneel(st));
		if (kn.k <= 0f && !LimbRules.mustKneel(st)) {
			KNEEL.remove(id);
		} else {
			kneel(model, rig, st, kn.k);
		}

		// 4) peças soltas (depois de esconder: o pose() reexibe o osso solto)
		if (TitanDebrisBridge.ready()) debrisFrame(model, (LivingEntity) titan, rig, st, partialTick);
	}

	private static int missingMask(LimbState st) {
		int m = 0;
		for (LimbPart p : LimbPart.VALUES) {
			if (!p.isEye() && st.isMissing(p)) m |= 1 << p.ordinal();
		}
		return m;
	}

	// ---- esconder / crescer -------------------------------------------------------------------------------

	private static void limb(GeoModel<?> model, Limb limb, LimbState st, LimbPart upper, LimbPart lower) {
		show(model, limb.upper, st, upper);
		show(model, limb.lower, st, lower); // depois do upper: o lower perdido/crescendo prevalece sobre o pai
	}

	private static void show(GeoModel<?> model, String boneName, LimbState st, LimbPart part) {
		GeoBone bone = model.getBone(boneName).orElse(null);
		if (bone == null) return;
		BoneSnapshot bind = bone.getInitialSnapshot();
		float scale;
		switch (st.status(part)) {
			case LOST:
				bone.setHidden(true);
				return;
			case REGROWING:
				scale = LimbState.STUMP_MIN + (1f - LimbState.STUMP_MIN) * st.progress(part);
				break;
			default:
				scale = 1f;
		}
		bone.setHidden(false);
		bone.setScaleX(bind.getScaleX() * scale);
		bone.setScaleY(bind.getScaleY() * scale);
		bone.setScaleZ(bind.getScaleZ() * scale);
	}

	// ---- decepar de verdade (TitanDebris do DAOT) ---------------------------------------------------------

	private static Limb limbOf(Rig rig, LimbPart p) {
		if (p.isArm()) return p.isLeft() ? rig.armLeft : rig.armRight;
		return p.isLeft() ? rig.legLeft : rig.legRight;
	}

	private static void spawnDebris(GeoModel<?> model, LivingEntity titan, Rig rig, int fresh) {
		try {
			Object frames = TitanDebrisBridge.frames(model.getAnimationProcessor().getRegisteredBones());
			for (LimbPart p : LimbPart.VALUES) {
				if (p.isEye() || (fresh & (1 << p.ordinal())) == 0) continue;
				LimbPart parent = p.parent();
				// antebraço/canela perdido junto com o braço/perna: a peça do upper já leva o osso inteiro
				if (parent != null && (fresh & (1 << parent.ordinal())) != 0) continue;
				if (p.isUpper()) dropPiecesOf(titan.getId(), p.child());
				Limb limb = limbOf(rig, p);
				GeoBone bone = model.getBone(p.isUpper() ? limb.upper : limb.lower).orElse(null);
				GeoBone end = model.getBone(limb.end).orElse(null);
				TitanDebrisBridge.loose(titan, p.ordinal(), bone, end, frames);
			}
		} catch (Throwable t) {
			LOG.warn("Falha ao soltar o membro do titan: {}", t.toString());
		}
	}

	/** Remove (sem vapor) a peça antiga de um membro que acabou de ser engolido por um corte maior. */
	private static void dropPiecesOf(int id, LimbPart part) {
		if (part == null) return;
		TitanDebrisBridge.piecesOf(id).removeIf(piece -> {
			try {
				return TitanDebrisBridge.partOf(piece) == part.ordinal();
			} catch (Throwable t) {
				return false;
			}
		});
	}

	private static void debrisFrame(GeoModel<?> model, LivingEntity titan, Rig rig, LimbState st, float partialTick) {
		List<Object> pieces = TitanDebrisBridge.piecesOf(titan.getId());
		if (pieces.isEmpty()) {
			LIVES.remove(titan.getId());
			return;
		}
		Map<Object, Life> lives = LIVES.computeIfAbsent(titan.getId(), k -> new IdentityHashMap<>());
		float now = titan.level().getGameTime() + partialTick;
		int lifetime = LimbRules.DEBRIS_LIFETIME_TICKS;
		int fade = Math.min(LimbRules.DEBRIS_FADE_TICKS, Math.max(1, lifetime));
		try {
			pieces.removeIf(piece -> {
				try {
					Life life = lives.computeIfAbsent(piece, k -> new Life(now));
					LimbPart part = LimbPart.VALUES[TitanDebrisBridge.partOf(piece)];
					// 1) o membro começou a crescer (toco): a peça solta evapora de uma vez
					boolean regrowing = st.status(part) != LimbStatus.LOST;
					// 2) passou o tempo de vida: evaporou por completo
					boolean expired = lifetime > 0 && now - life.born >= lifetime;
					if (regrowing || expired) {
						TitanDebrisBridge.steam(titan, piece);
						lives.remove(piece);
						return true;
					}
					return false;
				} catch (Throwable t) {
					lives.remove(piece);
					return true;
				}
			});
			if (pieces.isEmpty()) {
				LIVES.remove(titan.getId());
				return;
			}
			Object frames = TitanDebrisBridge.frames(model.getAnimationProcessor().getRegisteredBones());
			for (Object piece : pieces) {
				LimbPart part = LimbPart.VALUES[TitanDebrisBridge.partOf(piece)];
				GeoBone bone = model.getBone(TitanDebrisBridge.boneOf(piece)).orElse(null);
				TitanDebrisBridge.pose(titan, piece, bone, frames, partialTick);
				if (part.isUpper() && part.child() != null) {
					// a peça do braço/perna inteiro leva o antebraço/canela junto: reexibe o filho
					GeoBone child = model.getBone(limbOf(rig, part).lower).orElse(null);
					if (child != null) {
						child.setHidden(false);
						BoneSnapshot bind = child.getInitialSnapshot();
						child.setScaleX(bind.getScaleX());
						child.setScaleY(bind.getScaleY());
						child.setScaleZ(bind.getScaleZ());
					}
				}
				Life life = lives.get(piece);
				if (life != null && bone != null) evaporate(titan, piece, bone, life, now, lifetime, fade);
			}
		} catch (Throwable t) {
			LOG.warn("Falha ao animar a peca solta do titan: {}", t.toString());
		}
	}

	/**
	 * Fase final da peça solta: nos últimos {@code fade} ticks ela encolhe (em torno do pivô do osso) e solta vapor
	 * cada vez mais seguido. A escala é sempre "base x fator", com a base guardada no primeiro quadro (logo após o
	 * {@code pose()}), para não acumular caso o DAOT não reponha a escala a cada quadro.
	 */
	private static void evaporate(LivingEntity titan, Object piece, GeoBone bone, Life life, float now, int lifetime, int fade)
			throws ReflectiveOperationException {
		if (life.baseScale == null) {
			life.baseScale = new float[] { bone.getScaleX(), bone.getScaleY(), bone.getScaleZ() };
		}
		if (lifetime <= 0) return;
		float t = (now - life.born - (lifetime - fade)) / fade; // 0 antes do fade, 1 no fim
		if (t <= 0f) return;
		t = Math.min(1f, t);
		float k = Math.max(0.02f, 1f - t * t * (3f - 2f * t)); // 1 -> ~0, suave
		bone.setScaleX(life.baseScale[0] * k);
		bone.setScaleY(life.baseScale[1] * k);
		bone.setScaleZ(life.baseScale[2] * k);
		float interval = LimbRules.DEBRIS_STEAM_INTERVAL_START
				+ (LimbRules.DEBRIS_STEAM_INTERVAL_END - LimbRules.DEBRIS_STEAM_INTERVAL_START) * t;
		if (now - life.lastSteam >= interval) {
			life.lastSteam = now;
			TitanDebrisBridge.steam(titan, piece);
		}
	}

	// ---- ajoelhar -----------------------------------------------------------------------------------------

	/** Suaviza o ajoelhar: 12 ticks para descer e 34 para levantar (mesmos tempos do TitanBody do DAOT). */
	private static final class Kneel {
		float k;
		long last = System.nanoTime();

		void advance(boolean down) {
			long now = System.nanoTime();
			float ticks = Math.min(5f, (now - last) / 50_000_000f);
			last = now;
			k = down ? Math.min(1f, k + ticks / 12f) : Math.max(0f, k - ticks / 34f);
		}
	}

	private static void kneel(GeoModel<?> model, Rig rig, LimbState st, float k) {
		float e = k * k * (3f - 2f * k);
		GeoBone root = model.getBone(rig.root).orElse(null);
		if (root != null) root.setPosY(root.getPosY() - kneelDepthPx(model, rig, st) * e);
		// joelho e pé dobram para trás: o sinal vem da frente do modelo (igual ao applyStomp do DAOT)
		float angle = rig.fz * ((float) Math.PI / 2f) * e;
		for (Limb leg : new Limb[] { rig.legLeft, rig.legRight }) {
			swing(model.getBone(leg.lower).orElse(null), angle);
			swing(model.getBone(leg.end).orElse(null), angle);
		}
	}

	/** Quanto o corpo desce (px do modelo): até o joelho tocar o chão; sem nenhuma coxa, até o quadril. */
	private static float kneelDepthPx(GeoModel<?> model, Rig rig, LimbState st) {
		boolean thighLeft = st.isIntact(LimbPart.LEG_UPPER_LEFT);
		boolean thighRight = st.isIntact(LimbPart.LEG_UPPER_RIGHT);
		if (thighLeft || thighRight) {
			Limb leg = thighLeft ? rig.legLeft : rig.legRight;
			GeoBone shin = model.getBone(leg.lower).orElse(null);
			if (shin != null) return shin.getPivotY();
		}
		GeoBone thigh = model.getBone(rig.legLeft.upper).orElse(null);
		return thigh == null ? 0f : thigh.getPivotY() * 0.9f;
	}

	private static Quaternionf quat(GeoBone b) {
		return new Quaternionf().rotationZYX(b.getRotZ(), b.getRotY(), b.getRotX());
	}

	/** Gira o osso {@code angle} rad em torno do eixo X do MODELO (compensando a rotação dos pais). */
	private static void swing(GeoBone bone, float angle) {
		if (bone == null) return;
		Quaternionf above = new Quaternionf();
		for (GeoBone p = bone.getParent(); p != null; p = p.getParent()) above.premul(quat(p));
		Vector3f axis = above.conjugate(new Quaternionf()).transform(new Vector3f(1f, 0f, 0f));
		Quaternionf turned = new Quaternionf().rotationAxis(angle, axis).mul(quat(bone));
		Vector3f e = turned.getEulerAnglesZYX(new Vector3f());
		bone.setRotX(e.x);
		bone.setRotY(e.y);
		bone.setRotZ(e.z);
	}
}