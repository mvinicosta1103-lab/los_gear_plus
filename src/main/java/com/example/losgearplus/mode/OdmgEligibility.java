package com.example.losgearplus.mode;

import com.example.losgearplus.compat.DaotBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/** Quem pode estar em ODMG Mode. Todas as condições precisam passar. */
public final class OdmgEligibility {
	private OdmgEligibility() {}

	private static final List<Predicate<ServerPlayer>> EXTRA = new ArrayList<>();

	/** Gancho para o resto do mod (ex.: exigir correias na gear tab). */
	public static void addCondition(Predicate<ServerPlayer> condition) {
		EXTRA.add(condition);
	}

	public static boolean canUse(ServerPlayer player) {
		if (player.isSpectator() || player.isPassenger()) return false;
		if (!DaotBridge.wearsOdmGear(player)) return false;
		for (Predicate<ServerPlayer> p : EXTRA) {
			if (!p.test(player)) return false;
		}
		return true;
	}
}
