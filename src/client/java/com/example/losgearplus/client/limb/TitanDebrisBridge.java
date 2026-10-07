package com.example.losgearplus.client.limb;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Ponte para o {@code daot.TitanDebris} REAL: o membro decepado voa, cai com física, fica no chão e some com vapor
 * quando o membro cresce de volta, igualzinho aos pure titans.
 *
 * <p>O {@code TitanDebris} em si não depende de rig nenhum: {@code loose(titan, limb, bone, end, frames)} e
 * {@code pose(...)} funcionam com qualquer osso do GeckoLib. Só o {@code frame()} (que decide QUANDO soltar e
 * QUANDO remover) amarra ao {@code TitanBody}; essa parte nós substituímos pelo estado de membros do shifter.
 * Tudo é privado no DAOT, então usamos reflexão. Se algo não for encontrado (DAOT de outra versão), a ponte se
 * desliga sozinha e o corte continua funcionando só escondendo o osso.
 */
final class TitanDebrisBridge {
	private TitanDebrisBridge() {}

	private static final Logger LOG = LoggerFactory.getLogger("los_gear_plus");

	private static boolean tried;
	private static boolean ready;
	private static Method loose;
	private static Method pose;
	private static Method steam;
	private static Method frames;
	private static Field pieces;
	private static Field pieceBone;
	private static Field pieceLimb;

	static boolean ready() {
		if (!tried) {
			tried = true;
			try {
				Class<?> debris = Class.forName("daot.TitanDebris");
				for (Method m : debris.getDeclaredMethods()) {
					switch (m.getName()) {
						case "loose" -> loose = m;
						case "pose" -> pose = m;
						case "steam" -> steam = m;
						case "frames" -> {
							if (m.getParameterCount() == 1) frames = m;
						}
						default -> { }
					}
				}
				pieces = debris.getDeclaredField("PIECES");
				Class<?> piece = Class.forName("daot.TitanDebris$Piece");
				pieceBone = piece.getDeclaredField("bone");
				pieceLimb = piece.getDeclaredField("limb");
				for (Method m : new Method[] { loose, pose, steam, frames }) {
					if (m == null) throw new NoSuchMethodException("metodo do TitanDebris ausente");
					m.setAccessible(true);
				}
				pieces.setAccessible(true);
				pieceBone.setAccessible(true);
				pieceLimb.setAccessible(true);
				ready = true;
			} catch (Throwable t) {
				LOG.warn("TitanDebris indisponivel, o membro cortado so sera escondido: {}", t.toString());
			}
		}
		return ready;
	}

	@SuppressWarnings("unchecked")
	static Object frames(Collection<GeoBone> bones) throws ReflectiveOperationException {
		return frames.invoke(null, bones);
	}

	static void loose(LivingEntity titan, int limb, GeoBone bone, GeoBone end, Object frames) throws ReflectiveOperationException {
		loose.invoke(null, titan, limb, bone, end, frames);
	}

	/** Peças soltas deste titã (lista VIVA do DAOT, pode-se remover dela). Nunca null. */
	@SuppressWarnings("unchecked")
	static List<Object> piecesOf(int entityId) {
		try {
			Map<Integer, List<Object>> all = (Map<Integer, List<Object>>) pieces.get(null);
			List<Object> l = all.get(entityId);
			return l == null ? new ArrayList<>() : l;
		} catch (ReflectiveOperationException e) {
			return new ArrayList<>();
		}
	}

	static String boneOf(Object piece) throws ReflectiveOperationException {
		return (String) pieceBone.get(piece);
	}

	/** Aqui "limb" guarda o {@code LimbPart.ordinal()} (o DAOT usa 0..3, mas só para o TitanBody dele). */
	static int partOf(Object piece) throws ReflectiveOperationException {
		return pieceLimb.getInt(piece);
	}

	static void pose(LivingEntity titan, Object piece, GeoBone bone, Object frames, float partialTick) throws ReflectiveOperationException {
		pose.invoke(null, titan, piece, bone, frames, partialTick);
	}

	static void steam(LivingEntity titan, Object piece) throws ReflectiveOperationException {
		steam.invoke(null, titan, piece);
	}
}
