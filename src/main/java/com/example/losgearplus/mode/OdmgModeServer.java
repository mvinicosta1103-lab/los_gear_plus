package com.example.losgearplus.mode;

import com.example.losgearplus.grip.GripStorage;
import com.example.losgearplus.grip.HolsterWeapons;
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
		set(player, on, null);
	}

	/** {@code kind}: escolha da tela de seleção (blades ou armas de fogo); null = automático. */
	public static void set(ServerPlayer player, boolean on, HolsterWeapons.Kind kind) {
		if (on == ACTIVE.contains(player.getUUID())) return;
		if (on) {
			// Grip Storage: os grips vêm do inventário do jogador; sem eles o modo não liga.
			if (!GripStorage.equip(player, kind)) {
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
			if (GripStorage.needsChoice(player)) {
				// New ODM Gear + New ODM Uniform e os dois tipos de arma: o jogador escolhe (resposta em choose()).
				ServerPlayNetworking.send(player, new OdmgChoicePromptPayload());
			} else {
				set(player, true);
			}
		} else {
			player.displayClientMessage(Component.translatable("los_gear_plus.mode.odmg_unavailable"), true);
		}
	}

	/** Resposta da tela de seleção. O estado pode ter mudado desde o prompt, então tudo é revalidado. */
	public static void choose(ServerPlayer player, boolean guns) {
		if (isActive(player) || !OdmgEligibility.canUse(player)) return;
		set(player, true, guns ? HolsterWeapons.Kind.GUNS : HolsterWeapons.Kind.BLADES);
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
