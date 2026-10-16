package com.example.losgearplus.codex;

import daot.DannysAot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Quebra o chão de um impacto: uma cratera no centro e rachaduras que se espalham para fora, ao longo de ~0,7 s,
 * com pedaços de terra voando. Respeita {@code DannysAot.isTitanGriefingEnabled} (a mesma regra dos golpes do
 * DAOT): com a destruição desligada só aparecem as partículas, nenhum bloco é mexido. Blocos inquebráveis
 * (bedrock...), com inventário (baús...) e fluidos nunca são tocados.
 */
public final class GroundBreaker {
	private GroundBreaker() {}

	/** Teto de blocos voando por impacto (cada um é uma entidade). */
	private static final int MAX_DEBRIS = 40;
	private static final int CRACKS = 8;

	private static final class Debris {
		int count;
	}

	private static final class Crack {
		double x, z, dx, dz;
	}

	/**
	 * @param center ponto do impacto (o Y é o do chão em volta)
	 * @param scale  tamanho do titã em relação ao normal (1.0 = um titã de ~13 blocos)
	 */
	public static void slam(ServerLevel level, Vec3 center, double scale) {
		boolean grief = DannysAot.isTitanGriefingEnabled(level);
		Debris debris = new Debris();
		int craterRadius = Math.max(2, (int) Math.round(3.0 * scale));
		int crackLength = Math.max(8, (int) Math.round(12.0 * scale));

		if (grief) crater(level, center, craterRadius, debris);
		impactFx(level, center, craterRadius);

		List<Crack> cracks = new ArrayList<>();
		for (int i = 0; i < CRACKS; i++) {
			double a = Math.PI * 2.0 * i / CRACKS + (level.random.nextDouble() - 0.5) * 0.5;
			Crack c = new Crack();
			c.x = center.x;
			c.z = center.z;
			c.dx = Math.cos(a);
			c.dz = Math.sin(a);
			cracks.add(c);
		}
		crackStep(level, center, cracks, 1, crackLength, grief, debris);
	}

	/**
	 * Parte o chão num ponto só (usado pelas fendas que correm): partículas sempre, e, se a destruição estiver
	 * ligada, tira até {@code depth} blocos de profundidade (às vezes lançando o bloco como entulho).
	 */
	public static void cleave(ServerLevel level, Vec3 point, Vec3 origin, int depth) {
		BlockPos ground = ground(level, (int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
		if (ground == null) return;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(ground)),
				point.x, ground.getY() + 1.0, point.z, 12, 0.5, 0.2, 0.5, 0.08);
		if (!DannysAot.isTitanGriefingEnabled(level)) return;
		for (int i = 0; i < depth; i++) {
			BlockPos pos = ground.below(i);
			BlockState state = level.getBlockState(pos);
			if (!breakable(level, pos, state)) break;
			if (i == 0 && level.random.nextFloat() < 0.2f) fling(level, pos, state, origin, new Debris());
			else level.removeBlock(pos, false);
		}
	}

	// ---- cratera -----------------------------------------------------------------------------------------

	private static void crater(ServerLevel level, Vec3 center, int radius, Debris debris) {
		int cx = (int) Math.floor(center.x), cz = (int) Math.floor(center.z), cy = (int) Math.floor(center.y);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > radius) continue;
				BlockPos ground = ground(level, cx + dx, cy, cz + dz);
				if (ground == null) continue;
				int depth = dist < radius * 0.5 ? 3 : dist < radius * 0.8 ? 2 : 1; // mais fundo no meio
				for (int i = 0; i < depth; i++) {
					BlockPos pos = ground.below(i);
					BlockState state = level.getBlockState(pos);
					if (!breakable(level, pos, state)) break;
					if (i == 0 && debris.count < MAX_DEBRIS && level.random.nextFloat() < 0.35f) fling(level, pos, state, center, debris);
					else level.removeBlock(pos, false);
				}
			}
		}
	}

	// ---- rachaduras (uma "geração" por tick, para parecerem se espalhando) ---------------------------------

	private static void crackStep(ServerLevel level, Vec3 center, List<Crack> cracks, int step, int length, boolean grief, Debris debris) {
		for (Crack c : cracks) {
			// dá um zigue-zague na direção da rachadura
			c.dx += (level.random.nextDouble() - 0.5) * 0.6;
			c.dz += (level.random.nextDouble() - 0.5) * 0.6;
			double norm = Math.max(1.0E-4, Math.hypot(c.dx, c.dz));
			c.dx /= norm;
			c.dz /= norm;
			c.x += c.dx;
			c.z += c.dz;

			BlockPos ground = ground(level, (int) Math.floor(c.x), (int) Math.floor(center.y), (int) Math.floor(c.z));
			if (ground == null) continue;
			BlockState top = level.getBlockState(ground);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top), c.x, ground.getY() + 1.0, c.z,
					10, 0.4, 0.15, 0.4, 0.06);

			if (!grief) continue;
			int depth = step < length / 2 ? 2 : 1; // mais profunda perto do centro, rasa na ponta
			for (int i = 0; i < depth; i++) {
				BlockPos pos = ground.below(i);
				BlockState state = level.getBlockState(pos);
				if (!breakable(level, pos, state)) break;
				if (i == 0 && debris.count < MAX_DEBRIS && level.random.nextFloat() < 0.15f) fling(level, pos, state, center, debris);
				else level.removeBlock(pos, false);
			}
		}
		if (step < length) {
			CodexScheduler.runLater(level.getServer(), 1,
					() -> crackStep(level, center, cracks, step + 1, length, grief, debris));
		}
	}

	// ---- peças ---------------------------------------------------------------------------------------------

	/** Primeiro bloco sólido de cima para baixo na coluna (x,z), a partir de uns blocos acima do impacto. */
	private static BlockPos ground(ServerLevel level, int x, int startY, int z) {
		if (!level.hasChunkAt(new BlockPos(x, startY, z))) return null;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, startY + 2, z);
		for (int i = 0; i < 12; i++) {
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(level, p).isEmpty()) return p.immutable();
			p.move(Direction.DOWN);
		}
		return null;
	}

	private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir()
				&& state.getDestroySpeed(level, pos) >= 0.0f
				&& !state.is(BlockTags.WITHER_IMMUNE)
				&& !state.hasBlockEntity()
				&& state.getFluidState().isEmpty();
	}

	/** Arranca o bloco e o arremessa para cima e para fora; ao cair ele volta como entulho (sem dropar item). */
	private static void fling(ServerLevel level, BlockPos pos, BlockState state, Vec3 center, Debris debris) {
		FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state); // também remove o bloco do mundo
		Vec3 out = Vec3.atCenterOf(pos).subtract(center).multiply(1.0, 0.0, 1.0);
		out = out.lengthSqr() < 1.0E-4 ? new Vec3(level.random.nextDouble() - 0.5, 0.0, level.random.nextDouble() - 0.5) : out.normalize();
		double power = 0.3 + level.random.nextDouble() * 0.5;
		block.setDeltaMovement(out.x * power, 0.5 + level.random.nextDouble() * 0.7, out.z * power);
		block.dropItem = false;
		block.setHurtsEntities(2.0f, 30); // o entulho machuca quem estiver embaixo
		block.hurtMarked = true;
		debris.count++;
	}

	private static void impactFx(ServerLevel level, Vec3 center, int radius) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		BlockPos ground = ground(level, (int) Math.floor(center.x), (int) Math.floor(center.y), (int) Math.floor(center.z));
		if (ground != null) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(ground)),
					center.x, center.y + 0.5, center.z, 60, radius * 0.6, 0.3, radius * 0.6, 0.15);
		}
		for (int i = 0; i < 36; i++) { // anel de poeira saindo do impacto
			double a = Math.PI * 2.0 * i / 36.0;
			level.sendParticles(ParticleTypes.CLOUD, center.x + Math.cos(a) * radius, center.y + 0.3, center.z + Math.sin(a) * radius,
					0, Math.cos(a), 0.05, Math.sin(a), 0.7);
		}
	}
}
