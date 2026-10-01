package com.example.losgearplus.mode;

import com.example.losgearplus.compat.DaotBridge;
import net.minecraft.server.level.ServerPlayer;

/** Quem pode estar em ODMG Mode. Todas as condições precisam passar. */
public final class OdmgEligibility {
	private OdmgEligibility() {}

	public static boolean canUse(ServerPlayer player) {
		if (player.isSpectator() || player.isPassenger()) return false;
		if (!DaotBridge.wearsOdmGear(player)) return false;
		return true;
	}
}
