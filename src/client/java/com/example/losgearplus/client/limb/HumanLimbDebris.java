package com.example.losgearplus.client.limb;

import com.example.losgearplus.limb.LimbPart;
import com.example.losgearplus.limb.LimbRules;
import com.example.losgearplus.limb.LimbState;
import com.example.losgearplus.limb.LimbStatus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import daot.ShifterTitan;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Braço/perna decepado de um HUMANO: no instante do corte solta uma peça física (só visual, no cliente) que voa, cai
 * com gravidade, fica no chão e some com vapor, igual ao que acontece com os membros dos titãs:
 * <ul>
 *   <li>passados {@link LimbRules#DEBRIS_LIFETIME_TICKS} ticks ela encolhe soltando vapor e some;</li>
 *   <li>se o dono (shifter) começa a regenerar o membro, a peça evapora na hora.</li>
 * </ul>
 * O corte é detectado pela mudança de estado recebida do servidor ({@link #onState}); o titã tem a própria peça (DAOT),
 * então nada é solto enquanto o jogador está montado num titã. Desligue em {@link LimbRules#HUMAN_DEBRIS_ENABLED}.
 */
public final class HumanLimbDebris {
	private HumanLimbDebris() {}

	private static final List<Piece> PIECES = new ArrayList<>();
	private static final RandomSource RNG = RandomSource.create();
	/** Meia aresta da caixa de colisão da peça (a espessura do membro é 4 px = 0,25 bloco). */
	private static final double RADIUS = 0.125;

	private static boolean steamLooked;
	private static ParticleOptions daotSteam;

	private static final class Piece {
		final UUID owner;
		final LimbPart part;
		final ResourceLocation skin;
		final ModelPart model;
		double x, y, z, px, py, pz;
		double vx, vy, vz;
		float yaw, pitch, roll, pYaw, pPitch, pRoll;
		float dYaw, dPitch, dRoll;
		int age;
		float lastSteam;
		boolean onGround;

		Piece(UUID owner, LimbPart part, ResourceLocation skin, ModelPart model) {
			this.owner = owner;
			this.part = part;
			this.skin = skin;
			this.model = model;
		}
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HumanLimbDebris::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(HumanLimbDebris::render);
	}

	public static void clear() {
		PIECES.clear();
	}

	// ---- detectar o corte / o crescimento ----------------------------------------------------------------------

	/** Chamado quando chega do servidor um estado novo de {@code id}; {@code before} é null na primeira vez que ele é visto. */
	public static void onState(UUID id, LimbState before, LimbState after) {
		if (before == null) return; // primeira vez que o jogador é visto: só memoriza, sem soltar nada
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;

		// membro voltou (cresceu, restaurado, transformação, morte): a peça solta evapora
		for (LimbPart p : LimbPart.VALUES) {
			if (p.isEye()) continue;
			if (before.status(p) == LimbStatus.LOST && after.status(p) != LimbStatus.LOST) evaporate(level, id, p);
		}

		if (!LimbRules.HUMAN_DEBRIS_ENABLED) return;
		if (!(level.getPlayerByUUID(id) instanceof AbstractClientPlayer player)) return;
		if (player.getVehicle() instanceof ShifterTitan) return; // dentro do titã quem solta a peça é o titã
		for (LimbPart.Kind kind : new LimbPart.Kind[] { LimbPart.Kind.ARM, LimbPart.Kind.LEG }) {
			for (boolean left : new boolean[] { true, false }) {
				LimbPart upper = LimbPart.of(kind, left, true);
				LimbPart lower = LimbPart.of(kind, left, false);
				if (before.isIntact(upper) && after.status(upper) == LimbStatus.LOST) {
					spawn(player, upper, before);
				} else if (before.isIntact(upper) && after.isIntact(upper)
						&& before.isIntact(lower) && after.status(lower) == LimbStatus.LOST) {
					spawn(player, lower, before);
				}
			}
		}
	}

	private static void evaporate(ClientLevel level, UUID owner, LimbPart part) {
		Iterator<Piece> it = PIECES.iterator();
		while (it.hasNext()) {
			Piece p = it.next();
			if (p.part == part && p.owner.equals(owner)) {
				burst(level, p);
				it.remove();
			}
		}
	}

	private static void spawn(AbstractClientPlayer player, LimbPart part, LimbState before) {
		boolean arm = part.isArm();
		boolean left = part.isLeft();
		float len;
		int vOff = 0;
		if (part.isUpper()) {
			// membro inteiro; se o antebraço/canela já faltava (ou ainda crescia), a peça é só o que existia
			LimbPart lower = part.child();
			switch (before.status(lower)) {
				case INTACT:
					len = HumanLimbRender.FULL_PX;
					break;
				case REGROWING:
					len = HumanLimbRender.HALF_PX + (HumanLimbRender.FULL_PX - HumanLimbRender.HALF_PX) * before.progress(lower);
					break;
				default:
					len = HumanLimbRender.HALF_PX;
			}
		} else {
			len = HumanLimbRender.HALF_PX; // antebraço/canela: textura da metade de baixo
			vOff = (int) HumanLimbRender.HALF_PX;
		}

		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		float w = arm && slim ? 3f : 4f;
		int u = arm ? (left ? 32 : 40) : (left ? 16 : 0);
		int v = left ? 48 : 16;
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("limb",
				CubeListBuilder.create().texOffs(u, v + vOff).addBox(-w / 2f, -len / 2f, -2f, w, len, 4f), PartPose.ZERO);
		ModelPart model = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("limb");

		Piece piece = new Piece(player.getUUID(), part, player.getSkin().texture(), model);
		double rad = Math.toRadians(player.yBodyRot);
		double lx = Math.cos(rad); // vetor "esquerda" do jogador
		double lz = Math.sin(rad);
		double side = left ? 1.0 : -1.0;
		double off = arm ? 0.34 : 0.12;
		piece.x = player.getX() + lx * side * off;
		piece.y = player.getY() + player.getBbHeight() * (arm ? 0.68 : 0.30);
		piece.z = player.getZ() + lz * side * off;
		piece.px = piece.x;
		piece.py = piece.y;
		piece.pz = piece.z;
		Vec3 motion = player.getDeltaMovement();
		piece.vx = motion.x * 0.6 + lx * side * 0.07 + (RNG.nextDouble() - 0.5) * 0.08;
		piece.vy = 0.12 + RNG.nextDouble() * 0.12;
		piece.vz = motion.z * 0.6 + lz * side * 0.07 + (RNG.nextDouble() - 0.5) * 0.08;
		piece.yaw = player.yBodyRot;
		piece.pYaw = piece.yaw;
		piece.dYaw = (RNG.nextFloat() - 0.5f) * 30f;
		piece.dPitch = (RNG.nextFloat() - 0.5f) * 60f;
		piece.dRoll = (RNG.nextFloat() - 0.5f) * 30f;

		while (PIECES.size() >= LimbRules.HUMAN_DEBRIS_MAX) PIECES.remove(0);
		PIECES.add(piece);
	}

	// ---- física ---------------------------------------------------------------------------------------------------

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			PIECES.clear();
			return;
		}
		if (PIECES.isEmpty() || mc.isPaused()) return;
		int lifetime = LimbRules.DEBRIS_LIFETIME_TICKS;
		Iterator<Piece> it = PIECES.iterator();
		while (it.hasNext()) {
			Piece p = it.next();
			p.age++;
			if (lifetime > 0 && p.age >= lifetime) {
				burst(level, p);
				it.remove();
				continue;
			}
			step(level, p);
			fadeSteam(level, p, lifetime);
		}
	}

	private static void step(ClientLevel level, Piece p) {
		p.px = p.x;
		p.py = p.y;
		p.pz = p.z;
		p.pYaw = p.yaw;
		p.pPitch = p.pitch;
		p.pRoll = p.roll;

		p.vy -= 0.08;
		Vec3 want = new Vec3(p.vx, p.vy, p.vz);
		AABB box = new AABB(p.x - RADIUS, p.y - RADIUS, p.z - RADIUS, p.x + RADIUS, p.y + RADIUS, p.z + RADIUS);
		Vec3 got = Entity.collideBoundingBox(null, want, box, level, List.of());
		boolean hitX = Math.abs(got.x - want.x) > 1.0E-7;
		boolean hitY = Math.abs(got.y - want.y) > 1.0E-7;
		boolean hitZ = Math.abs(got.z - want.z) > 1.0E-7;
		p.x += got.x;
		p.y += got.y;
		p.z += got.z;
		p.onGround = hitY && want.y < 0;
		if (hitX) p.vx *= -0.3;
		if (hitZ) p.vz *= -0.3;
		if (hitY) p.vy = p.onGround && -want.y > 0.25 ? -want.y * 0.25 : 0.0; // quica um pouco ao cair de alto
		p.vx *= 0.98;
		p.vy *= 0.98;
		p.vz *= 0.98;
		if (p.onGround) {
			p.vx *= 0.6;
			p.vz *= 0.6;
		}

		if (!p.onGround) {
			p.yaw += p.dYaw;
			p.pitch += p.dPitch;
			p.roll += p.dRoll;
		} else {
			// no chão: para de girar e deita (comprimento na horizontal)
			p.dYaw *= 0.5f;
			p.dPitch = 0f;
			p.dRoll = 0f;
			p.yaw += p.dYaw;
			float flat = 90f + 180f * Math.round((p.pitch - 90f) / 180f);
			p.pitch += (flat - p.pitch) * 0.35f;
			float rollFlat = 90f * Math.round(p.roll / 90f);
			p.roll += (rollFlat - p.roll) * 0.35f;
		}
	}

	/** Fração (0..1) já percorrida da fase final em que a peça encolhe e solta vapor. */
	private static float fadeProgress(float age, int lifetime) {
		if (lifetime <= 0) return 0f;
		int fade = Math.min(LimbRules.DEBRIS_FADE_TICKS, Math.max(1, lifetime));
		return Math.max(0f, Math.min(1f, (age - (lifetime - fade)) / fade));
	}

	private static void fadeSteam(ClientLevel level, Piece p, int lifetime) {
		float t = fadeProgress(p.age, lifetime);
		if (t <= 0f) return;
		float interval = LimbRules.DEBRIS_STEAM_INTERVAL_START
				+ (LimbRules.DEBRIS_STEAM_INTERVAL_END - LimbRules.DEBRIS_STEAM_INTERVAL_START) * t;
		if (p.age - p.lastSteam >= interval) {
			p.lastSteam = p.age;
			puff(level, p, 1);
		}
	}

	// ---- vapor ----------------------------------------------------------------------------------------------------

	/** Mesma partícula de vapor do DAOT que o Steam Heal usa (por reflexão); sem ela, só a fumaça comum. */
	private static ParticleOptions steamParticle() {
		if (!steamLooked) {
			steamLooked = true;
			try {
				Object value = Class.forName("daot.DannysAot").getField("PLAYER_DISMOUNT_PARTICLE").get(null);
				if (value instanceof ParticleOptions options) daotSteam = options;
			} catch (Throwable ignored) {
				// sem DAOT / campo renomeado: fica só a fumaça de campfire
			}
		}
		return daotSteam;
	}

	private static void puff(ClientLevel level, Piece p, int count) {
		ParticleOptions steam = steamParticle();
		for (int i = 0; i < count; i++) {
			double ox = (RNG.nextDouble() - 0.5) * 0.5;
			double oy = (RNG.nextDouble() - 0.3) * 0.3;
			double oz = (RNG.nextDouble() - 0.5) * 0.5;
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x + ox, p.y + oy, p.z + oz, 0.0, 0.03, 0.0);
			if (steam != null) level.addParticle(steam, p.x + ox, p.y + oy, p.z + oz, 0.0, 0.02, 0.0);
		}
	}

	/** Evaporou de uma vez (o membro voltou a crescer, ou o tempo acabou). */
	private static void burst(ClientLevel level, Piece p) {
		puff(level, p, 10);
		level.playLocalSound(p.x, p.y, p.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.2f, false);
	}

	// ---- desenho --------------------------------------------------------------------------------------------------

	private static void render(WorldRenderContext ctx) {
		if (PIECES.isEmpty()) return;
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		ClientLevel level = ctx.world();
		if (pose == null || buffers == null || level == null) return;
		Vec3 cam = ctx.camera().getPosition();
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		int lifetime = LimbRules.DEBRIS_LIFETIME_TICKS;

		for (Piece p : PIECES) {
			double x = lerp(pt, p.px, p.x);
			double y = lerp(pt, p.py, p.y);
			double z = lerp(pt, p.pz, p.z);
			float t = fadeProgress(p.age + pt, lifetime);
			float k = t <= 0f ? 1f : Math.max(0.02f, 1f - t * t * (3f - 2f * t)); // 1 -> ~0, suave

			pose.pushPose();
			pose.translate(x - cam.x, y - cam.y, z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(lerpF(pt, p.pYaw, p.yaw)));
			pose.mulPose(Axis.XP.rotationDegrees(lerpF(pt, p.pPitch, p.pitch)));
			pose.mulPose(Axis.ZP.rotationDegrees(lerpF(pt, p.pRoll, p.roll)));
			pose.scale(-k, -k, k); // o modelo do jogador é desenhado de cabeça para baixo (+Y para baixo)
			int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
			VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(p.skin));
			p.model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
	}

	private static double lerp(float t, double a, double b) {
		return a + (b - a) * t;
	}

	private static float lerpF(float t, float a, float b) {
		return a + (b - a) * t;
	}
}
