package com.example.losgearplus.mode;

import com.example.losgearplus.grip.GripStorage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Estado autoritativo do ODMG Mode (só em memória: não persiste entre sessões, igual ao WoF ao relogar). */
public final class OdmgModeServer {
	private OdmgModeServer() {}

	private static final Set<UUID> ACTIVE = new HashSet<>();

	/** Use isto em qualquer lógica de servidor que dependa do modo. */
	public static boolean isActive(Player player) {
		return ACTIVE.contains(player.getUUID());
	}

	public static void set(ServerPlayer player, boolean on) {
		if (on == ACTIVE.contains(player.getUUID())) return;
		if (on) {
			// Grip Storage: os grips vêm do inventário do jogador; sem eles o modo não liga.
			if (!GripStorage.equip(player)) {
				player.displayClientMessage(Component.translatable("los_gear_plus.mode.odmg_no_grips"), true);
				return;
			}
			ACTIVE.add(player.getUUID());
		} else {
			ACTIVE.remove(player.getUUID());
			GripStorage.stow(player); // grips das mãos voltam para o storage (laterais do torso)
		}
		ServerPlayNetworking.send(player, new OdmgModeSyncPayload(on));
	}

	public static void toggle(ServerPlayer player) {
		if (isActive(player)) {
			set(player, false);
		} else if (OdmgEligibility.canUse(player)) {
			set(player, true);
		} else {
			player.displayClientMessage(Component.translatable("los_gear_plus.mode.odmg_unavailable"), true);
		}
	}

	/** Remove sem enviar pacote (o jogador já saiu). */
	public static void forget(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
	}

	/** Desliga sozinho se o jogador perder o ODM, montar numa entidade etc. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (UUID id : new HashSet<>(ACTIVE)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				ACTIVE.remove(id);
			} else if (!OdmgEligibility.canUse(player)) {
				set(player, false);
			} else {
				GripStorage.enforce(player);
			}
		}
	}
}
