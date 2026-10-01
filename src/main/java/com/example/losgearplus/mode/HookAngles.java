package com.example.losgearplus.mode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Angulação dos hooks (equivalente ao hookAngle do WoF, mas de 0 a 180).
 *
 * O número é a ABERTURA TOTAL entre os dois hooks: 0 = os dois vão retos para onde você olha,
 * 180 = cada um vai 90 graus para o seu lado. Só vale com o ODMG Mode ligado.
 *
 * PONTO DE INTEGRAÇÃO com o DAOT: na hora em que o DAOT calcula a direção de lançamento de um hook,
 * troque o vetor por {@link #direction(Player, HookSide, Vec3)}. Funciona nos dois lados
 * (servidor usa o valor validado; cliente usa o valor local).
 */
public final class HookAngles {
	private HookAngles() {}

	public static final int MAX_ANGLE = 180;

	private static final Map<UUID, Integer> SERVER = new HashMap<>();

	/** Preenchido pelo cliente: abertura atual do jogador local (0 se o modo estiver desligado). */
	public static IntSupplier clientSpread = () -> 0;

	public static int clamp(int angle) {
		return Mth.clamp(angle, 0, MAX_ANGLE);
	}

	/** Abertura total em graus; 0 fora do ODMG Mode. */
	public static int spread(Player player) {
		if (player.level().isClientSide()) {
			return clientSpread.getAsInt();
		}
		if (player instanceof ServerPlayer sp && OdmgModeServer.isActive(sp)) {
			return SERVER.getOrDefault(sp.getUUID(), 0);
		}
		return 0;
	}

	/** Gira a direção de lançamento para o lado do hook. Com abertura 0 devolve o vetor original. */
	public static Vec3 direction(Player player, HookSide side, Vec3 look) {
		int spread = spread(player);
		if (spread <= 0) return look;
		float half = (float) Math.toRadians(spread / 2.0);
		return look.yRot(side == HookSide.RIGHT ? -half : half);
	}

	static void set(ServerPlayer player, int angle) {
		SERVER.put(player.getUUID(), clamp(angle));
	}

	static void forget(UUID id) {
		SERVER.remove(id);
	}
}
