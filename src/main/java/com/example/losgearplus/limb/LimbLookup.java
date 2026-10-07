package com.example.losgearplus.limb;

import daot.ShifterTitan;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Acha o {@link LimbState} do shifter dono de um titã (servidor e cliente). */
public final class LimbLookup {
	private LimbLookup() {}

	/** Estado do titã que o jogador está montando, ou null se não estiver num titã shifter. */
	public static LimbState riderState(ServerPlayer player) {
		Entity v = player.getVehicle();
		return v instanceof ShifterTitan ? ofTitan(v) : null;
	}

	/** Estado de membros do shifter dono deste titã; {@link LimbState#PRISTINE} se não for de shifter. */
	public static LimbState ofTitan(Entity titan) {
		if (!(titan instanceof ShifterTitan st)) return LimbState.PRISTINE;
		UUID id = null;
		try {
			id = st.getShifterUUID();
		} catch (Throwable ignored) {
			// no cliente o campo pode não estar sincronizado: cai no passageiro
		}
		if (id == null && titan.getFirstPassenger() instanceof Player rider) id = rider.getUUID();
		if (id == null) return LimbState.PRISTINE;
		if (titan.level().isClientSide) return LimbClientCache.get(id);
		MinecraftServer server = titan.getServer();
		if (server == null) return LimbState.PRISTINE;
		ServerPlayer owner = server.getPlayerList().getPlayer(id);
		return owner == null ? LimbState.PRISTINE : LimbData.of(owner);
	}
}
