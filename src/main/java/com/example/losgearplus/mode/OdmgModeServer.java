package com.example.losgearplus.mode;

import com.example.losgearplus.compat.DaotBridge;
import com.example.losgearplus.compat.SpearTransfer;
import com.example.losgearplus.grip.GripStorage;
import com.example.losgearplus.grip.HolsterWeapons;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class OdmgModeServer {
	private static final boolean LOS_GEAR = FabricLoader.getInstance().isModLoaded("los_gear");
	private static final Set<UUID> ACTIVE = new HashSet<>();
	private static final Set<UUID> GUNS_ACTIVE = new HashSet<>();
	private static final Map<UUID, HolsterWeapons.Kind> KIND = new HashMap<>();

	/** [ONE-HAND] Jogadores que escolheram One-Hand (preferência; vale ligado ou desligado). */
	private static final Set<UUID> ONE_HAND = new HashSet<>();

	private OdmgModeServer() {
	}

	public static boolean isActive(Player player) {
		return ACTIVE.contains(player.getUUID());
	}

	public static boolean gunsActive(Player player) {
		return GUNS_ACTIVE.contains(player.getUUID());
	}

	public static HolsterWeapons.Kind kindOf(Player player) {
		return KIND.getOrDefault(player.getUUID(), HolsterWeapons.Kind.BLADES);
	}

	/** [ONE-HAND] true se o jogador está em One-Hand (1 grip, offhand livre). */
	public static boolean isOneHand(Player player) {
		return ONE_HAND.contains(player.getUUID());
	}

	private static HolsterWeapons.Kind other(HolsterWeapons.Kind kind) {
		return kind == HolsterWeapons.Kind.BLADES ? HolsterWeapons.Kind.GUNS : HolsterWeapons.Kind.BLADES;
	}

	private static void setGunsFlag(UUID id, boolean guns) {
		if (guns) {
			GUNS_ACTIVE.add(id);
		} else {
			GUNS_ACTIVE.remove(id);
		}
	}

	public static void set(ServerPlayer player, boolean on) {
		set(player, on, null);
	}

	public static void set(ServerPlayer player, boolean on, HolsterWeapons.Kind kind) {
		if (on == ACTIVE.contains(player.getUUID())) {
			return;
		}
		UUID id = player.getUUID();
		if (on) {
			if (!GripStorage.equip(player, kind)) {
				player.displayClientMessage(Component.translatable(
						ONE_HAND.contains(id) ? "los_gear_plus.mode.odmg_no_grip_one"
								: "los_gear_plus.mode.odmg_no_grips"), true);
				return;
			}
			ACTIVE.add(id);
			setGunsFlag(id, kind == HolsterWeapons.Kind.GUNS);
			if (kind != null) {
				KIND.put(id, kind);
			}
		} else {
			ACTIVE.remove(id);
			GUNS_ACTIVE.remove(id);
			GripStorage.stow(player);
		}
		ServerPlayNetworking.send(player, new OdmgModeSyncPayload(on, on && gunsActive(player)));
	}

	public static void toggle(ServerPlayer player) {
		if (isActive(player)) {
			set(player, false);
		} else if (OdmgEligibility.canUse(player)) {
			HolsterWeapons.Kind kind = null;
			if (DaotBridge.wearsUniformAndGear(player)
					&& !GripStorage.canEquip(player, kind = kindOf(player))
					&& GripStorage.canEquip(player, other(kind))) {
				kind = other(kind);
			}
			set(player, true, kind);
		} else {
			player.displayClientMessage(Component.translatable("los_gear_plus.mode.odmg_unavailable"), true);
		}
	}

	// ------------------------------------------------------------------
	// [ONE-HAND] Alternar One-Hand <-> Two-Hand
	// ------------------------------------------------------------------

	/** Envia ao cliente se o jogador está em One-Hand (usado ao entrar/renascer/alternar). */
	public static void sendHands(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OdmgHandsSyncPayload(ONE_HAND.contains(player.getUUID())));
	}

	private static void applyHands(UUID id, boolean oneHand) {
		if (oneHand) {
			ONE_HAND.add(id);
		} else {
			ONE_HAND.remove(id);
		}
	}

	/**
	 * Alterna One-Hand/Two-Hand. Com o ODMG desligado só guarda a preferência; com ele ligado
	 * troca ao vivo (stow + equip), no mesmo padrão do swapKind.
	 */
	public static void toggleOneHand(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean target = !ONE_HAND.contains(id);
		String okMessage = target ? "los_gear_plus.mode.hands_one" : "los_gear_plus.mode.hands_two";

		if (!ACTIVE.contains(id)) {
			applyHands(id, target);
			sendHands(player);
			player.displayClientMessage(Component.translatable(okMessage), true);
			return;
		}

		HolsterWeapons.Kind current = GUNS_ACTIVE.contains(id) ? HolsterWeapons.Kind.GUNS : HolsterWeapons.Kind.BLADES;
		SpearTransfer.Load[] loads = LOS_GEAR ? SpearTransfer.takeFromHands(player) : null;
		GripStorage.stow(player);
		applyHands(id, target);

		if (!GripStorage.equip(player, current)) {
			// Não deu (ex.: Two-Hand sem 2 grips). Volta ao modo anterior.
			applyHands(id, !target);
			if (!GripStorage.equip(player, current)) {
				ACTIVE.remove(id);
				GUNS_ACTIVE.remove(id);
				ServerPlayNetworking.send(player, new OdmgModeSyncPayload(false, false));
				if (loads != null) {
					for (SpearTransfer.Load l : loads) {
						SpearTransfer.giveBack(player, l);
					}
				}
			} else if (loads != null) {
				SpearTransfer.place(player, loads);
			}
			sendHands(player);
			player.displayClientMessage(Component.translatable("los_gear_plus.mode.hands_unavailable"), true);
			return;
		}

		if (loads != null) {
			SpearTransfer.place(player, loads);
		}
		sendHands(player);
		player.displayClientMessage(Component.translatable(okMessage), true);
	}

	// ------------------------------------------------------------------

	public static void swapKind(ServerPlayer player) {
		if (!OdmgEligibility.canUse(player) || !DaotBridge.wearsUniformAndGear(player)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.mode.swap_unavailable"), true);
			return;
		}
		UUID id = player.getUUID();
		boolean active = ACTIVE.contains(id);
		HolsterWeapons.Kind current = active
				? (GUNS_ACTIVE.contains(id) ? HolsterWeapons.Kind.GUNS : HolsterWeapons.Kind.BLADES)
				: kindOf(player);
		HolsterWeapons.Kind target = other(current);
		if (!GripStorage.canEquip(player, target)) {
			player.displayClientMessage(Component.translatable(
					target == HolsterWeapons.Kind.GUNS ? "los_gear_plus.mode.swap_no_pistols"
							: "los_gear_plus.mode.swap_no_blades"), true);
			return;
		}
		if (active) {
			SpearTransfer.Load[] loads = LOS_GEAR ? SpearTransfer.takeFromHands(player) : null;
			GripStorage.stow(player);
			if (!GripStorage.equip(player, target)) {
				if (!GripStorage.equip(player, current)) {
					ACTIVE.remove(id);
					GUNS_ACTIVE.remove(id);
					ServerPlayNetworking.send(player, new OdmgModeSyncPayload(false, false));
					if (loads != null) {
						for (SpearTransfer.Load l : loads) {
							SpearTransfer.giveBack(player, l);
						}
					}
				} else if (loads != null) {
					SpearTransfer.place(player, loads);
				}
				return;
			}
			if (loads != null) {
				SpearTransfer.place(player, loads);
			}
			setGunsFlag(id, target == HolsterWeapons.Kind.GUNS);
			ServerPlayNetworking.send(player, new OdmgModeSyncPayload(true, target == HolsterWeapons.Kind.GUNS));
		}
		KIND.put(id, target);
		player.displayClientMessage(Component.translatable(
				target == HolsterWeapons.Kind.GUNS ? "los_gear_plus.mode.kind_pistols"
						: "los_gear_plus.mode.kind_blades"), true);
	}

	public static void choose(ServerPlayer player, boolean guns) {
		if (isActive(player) || !OdmgEligibility.canUse(player)) {
			return;
		}
		set(player, true, guns ? HolsterWeapons.Kind.GUNS : HolsterWeapons.Kind.BLADES);
	}

	public static void forget(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
		GUNS_ACTIVE.remove(player.getUUID());
		KIND.remove(player.getUUID());
		ONE_HAND.remove(player.getUUID());   // [ONE-HAND]
	}

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (UUID id : new HashSet<>(ACTIVE)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				ACTIVE.remove(id);
				GUNS_ACTIVE.remove(id);
				continue;
			}
			if (!OdmgEligibility.canUse(player)) {
				set(player, false);
				continue;
			}
			GripStorage.enforce(player);
		}
	}
}