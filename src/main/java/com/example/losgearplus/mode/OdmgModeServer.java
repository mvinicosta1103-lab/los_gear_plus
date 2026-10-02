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

/** Estado autoritativo do ODMG Mode (só em memória: não persiste entre sessões, igual ao WoF ao relogar). */
public final class OdmgModeServer {
	private OdmgModeServer() {}

	/** A carga de spears (Quad/Thunder Spear) é do los_gear: sem ele não há o que transferir. */
	private static final boolean LOS_GEAR = FabricLoader.getInstance().isModLoaded("los_gear");

	private static final Set<UUID> ACTIVE = new HashSet<>();
	/** Quem está com o modo ligado empunhando armas de fogo (pistolas/APG). Subconjunto de ACTIVE. */
	private static final Set<UUID> GUNS_ACTIVE = new HashSet<>();
	/**
	 * Funcionamento escolhido por quem veste New ODM Uniform + New ODM Gear (troca com a tecla P). É o que o Z liga.
	 * Padrão: blades. As armas do outro tipo continuam guardadas nos slots até o jogador trocar.
	 */
	private static final Map<UUID, HolsterWeapons.Kind> KIND = new HashMap<>();

	/** Use isto em qualquer lógica de servidor que dependa do modo. */
	public static boolean isActive(Player player) {
		return ACTIVE.contains(player.getUUID());
	}

	/** Modo ligado com armas de fogo nas mãos? */
	public static boolean gunsActive(Player player) {
		return GUNS_ACTIVE.contains(player.getUUID());
	}

	/** Funcionamento escolhido (New ODM Uniform + New ODM Gear): blades por padrão. */
	public static HolsterWeapons.Kind kindOf(Player player) {
		return KIND.getOrDefault(player.getUUID(), HolsterWeapons.Kind.BLADES);
	}

	private static HolsterWeapons.Kind other(HolsterWeapons.Kind kind) {
		return kind == HolsterWeapons.Kind.BLADES ? HolsterWeapons.Kind.GUNS : HolsterWeapons.Kind.BLADES;
	}

	private static void setGunsFlag(UUID id, boolean guns) {
		if (guns) GUNS_ACTIVE.add(id); else GUNS_ACTIVE.remove(id);
	}

	public static void set(ServerPlayer player, boolean on) {
		set(player, on, null);
	}

	/** {@code kind}: blades ou armas de fogo (só com New ODM Uniform + New ODM Gear); null = automático. */
	public static void set(ServerPlayer player, boolean on, HolsterWeapons.Kind kind) {
		if (on == ACTIVE.contains(player.getUUID())) return;
		UUID id = player.getUUID();
		if (on) {
			// Grip Storage: os grips vêm do inventário do jogador; sem eles o modo não liga.
			if (!GripStorage.equip(player, kind)) {
				player.displayClientMessage(Component.translatable("los_gear_plus.mode.odmg_no_grips"), true);
				return;
			}
			ACTIVE.add(id);
			setGunsFlag(id, kind == HolsterWeapons.Kind.GUNS);
			if (kind != null) KIND.put(id, kind);
		} else {
			ACTIVE.remove(id);
			GUNS_ACTIVE.remove(id);
			GripStorage.stow(player); // armas das mãos voltam para o storage (laterais do torso)
		}
		ServerPlayNetworking.send(player, new OdmgModeSyncPayload(on, on && gunsActive(player)));
	}

	public static void toggle(ServerPlayer player) {
		if (isActive(player)) {
			set(player, false);
		} else if (OdmgEligibility.canUse(player)) {
			HolsterWeapons.Kind kind = null;
			if (DaotBridge.wearsUniformAndGear(player)) {
				// Liga o funcionamento escolhido (P). As armas do outro tipo ficam nos slots. Se o escolhido não tem
				// um par disponível, usa o outro.
				kind = kindOf(player);
				if (!GripStorage.canEquip(player, kind) && GripStorage.canEquip(player, other(kind))) {
					kind = other(kind);
				}
			}
			set(player, true, kind);
		} else {
			player.displayClientMessage(Component.translatable("los_gear_plus.mode.odmg_unavailable"), true);
		}
	}

	/**
	 * Tecla P: troca o funcionamento (New ODM Gear com blades <-> New ODM Uniform com pistolas). Só existe com os dois
	 * ODM vestidos. Com o modo desligado só muda o que o Z vai ligar; com o modo ligado troca as armas das mãos na hora.
	 * A arma que sai das mãos volta para o slot (pistolas) ou para o inventário (blades, se os slots têm pistolas).
	 */
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

		// Confere ANTES de mexer em qualquer coisa (as armas atuais ainda estão nas mãos e não contam para o outro tipo).
		if (!GripStorage.canEquip(player, target)) {
			player.displayClientMessage(Component.translatable(target == HolsterWeapons.Kind.GUNS
					? "los_gear_plus.mode.swap_no_pistols" : "los_gear_plus.mode.swap_no_blades"), true);
			return;
		}

		if (active) {
			// Thunder Spear / Quad Thunder Spear carregadas acompanham a troca: saem das armas atuais e entram nas novas
			// (mesma mão). Se a nova já estava carregada, a spear volta ao inventário.
			SpearTransfer.Load[] loads = LOS_GEAR ? SpearTransfer.takeFromHands(player) : null;
			GripStorage.stow(player);
			if (!GripStorage.equip(player, target)) { // não deveria acontecer (já conferido): volta ao que era
				if (!GripStorage.equip(player, current)) {
					ACTIVE.remove(id);
					GUNS_ACTIVE.remove(id);
					ServerPlayNetworking.send(player, new OdmgModeSyncPayload(false, false));
					if (loads != null) for (SpearTransfer.Load l : loads) SpearTransfer.giveBack(player, l);
				} else if (loads != null) {
					SpearTransfer.place(player, loads);
				}
				return;
			}
			if (loads != null) SpearTransfer.place(player, loads);
			setGunsFlag(id, target == HolsterWeapons.Kind.GUNS);
			ServerPlayNetworking.send(player, new OdmgModeSyncPayload(true, target == HolsterWeapons.Kind.GUNS));
		}
		KIND.put(id, target);
		player.displayClientMessage(Component.translatable(target == HolsterWeapons.Kind.GUNS
				? "los_gear_plus.mode.kind_pistols" : "los_gear_plus.mode.kind_blades"), true);
	}

	/** Resposta da tela de seleção (não é mais aberta pelo Z; mantida por compatibilidade). */
	public static void choose(ServerPlayer player, boolean guns) {
		if (isActive(player) || !OdmgEligibility.canUse(player)) return;
		set(player, true, guns ? HolsterWeapons.Kind.GUNS : HolsterWeapons.Kind.BLADES);
	}

	/** Remove sem enviar pacote (o jogador já saiu). */
	public static void forget(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
		GUNS_ACTIVE.remove(player.getUUID());
		KIND.remove(player.getUUID());
	}

	/** Desliga sozinho se o jogador perder o ODM, montar numa entidade etc. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;
		for (UUID id : new HashSet<>(ACTIVE)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				ACTIVE.remove(id);
				GUNS_ACTIVE.remove(id);
			} else if (!OdmgEligibility.canUse(player)) {
				set(player, false);
			} else {
				GripStorage.enforce(player);
			}
		}
	}
}
