package com.example.losgearplus.grip;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.compat.DaotBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * ODM Grip Storage (lado servidor).
 *
 * Os grips NÃO são criados: vêm do inventário do jogador (os {@code dannys-aot:blade} que ele tem).
 *  - Modo LIGADO ({@link #equip}): pega os grips guardados no storage ou, se não houver, os do inventário,
 *    e coloca nas mãos (direito no slot da hotbar selecionado, esquerdo na mão secundária), travados.
 *    Sem {@link #MIN_GRIPS} grips disponíveis o modo não liga ({@link #canEquip}).
 *  - Modo DESLIGADO ({@link #stow}): os grips saem das mãos para o storage (attachment persistente) e o
 *    cliente os desenha nas laterais do torso.
 *  - Sem ODM vestido, ou ao morrer, os grips guardados voltam para o inventário ({@link #release}).
 *
 * Só existe UMA cópia de cada grip: inventário, mãos ou storage. Com o modo ligado {@link #enforce} roda todo
 * tick e impede a remoção (soltar, trocar de mão, mover no inventário, baú, trocar de slot da hotbar).
 */
public final class GripStorage {
	private GripStorage() {}

	/** Quantos grips são necessários para ligar o modo (um por mão). */
	public static final int MIN_GRIPS = 2;

	private static final int MAIN = 0;
	private static final int OFF = 1;
	/** Inventory.SLOT_OFFHAND. */
	private static final int OFFHAND_SLOT = 40;
	/** Inventário "de itens": hotbar + mochila (0..35) e a mão secundária (40). Não inclui armadura. */
	private static final int MAIN_INVENTORY_SIZE = 36;

	/** [0] = grip da mão principal, [1] = grip da mão secundária. EMPTY = nada guardado. */
	public static final AttachmentType<List<ItemStack>> STORE = AttachmentRegistry.<List<ItemStack>>builder()
			.persistent(ItemStack.OPTIONAL_CODEC.listOf())
			.buildAndRegister(LosGearPlus.id("grip_storage"));

	/** Slot da hotbar travado com o grip da mão principal (só jogadores com o modo ligado). Só thread do servidor. */
	private static final Map<UUID, Integer> LOCK_SLOT = new HashMap<>();
	/** Cópia de referência dos grips em uso, para repor se algo escapar. */
	private static final Map<UUID, ItemStack[]> RECORD = new HashMap<>();

	private static int releaseTimer;

	public static void init() {
		PayloadTypeRegistry.playS2C().register(GripHolsterSyncPayload.TYPE, GripHolsterSyncPayload.CODEC);

		// Quem começa a ver um jogador recebe o estado dos grips dele.
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof ServerPlayer target) {
				ServerPlayNetworking.send(viewer, payloadFor(target));
			}
		});

		// A cada 1 s: quem tem grips guardados mas tirou o ODM recebe os grips de volta no inventário.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++releaseTimer < 20) return;
			releaseTimer = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!isLocked(player) && storedMask(read(player)) != 0 && !DaotBridge.wearsOdmGear(player)) {
					release(player);
				}
			}
		});
	}

	public static boolean isLocked(ServerPlayer player) {
		return LOCK_SLOT.containsKey(player.getUUID());
	}

	// ------------------------------------------------------------------ pré-requisito

	/** True se o jogador tem grips suficientes (guardados + no inventário) para ligar o modo. */
	public static boolean canEquip(ServerPlayer player) {
		if (!GripItems.isAvailable()) return true; // item do grip não achado: o sistema fica desligado
		if (isLocked(player)) return true;
		int count = 0;
		for (ItemStack s : read(player)) if (!s.isEmpty()) count++;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isLooseGrip(i, inv.getItem(i))) count++;
		}
		return count >= MIN_GRIPS;
	}

	// ------------------------------------------------------------------ ligar / desligar

	/**
	 * Modo LIGADO: usa os grips guardados e completa com os do inventário. Retorna false (sem mexer em nada)
	 * se não houver grips suficientes.
	 */
	public static boolean equip(ServerPlayer player) {
		Item gripItem = GripItems.get();
		if (gripItem == null) return true;
		if (isLocked(player)) return true;
		if (!canEquip(player)) return false;

		List<ItemStack> stored = read(player);
		ItemStack main = stored.get(MAIN);
		ItemStack off = stored.get(OFF);
		if (main.isEmpty()) main = takeFromInventory(player);
		if (off.isEmpty()) off = takeFromInventory(player);
		if (main.isEmpty() || off.isEmpty()) { // não deveria acontecer depois do canEquip; desfaz por segurança
			giveOrDrop(player, GripMarker.unmark(main));
			giveOrDrop(player, GripMarker.unmark(off));
			return false;
		}
		GripMarker.mark(main);
		GripMarker.mark(off);

		Inventory inv = player.getInventory();
		int slot = inv.selected;

		// Primeiro põe o grip, depois devolve o que estava lá (senão o item voltaria para o mesmo slot).
		ItemStack displacedMain = inv.getItem(slot);
		ItemStack displacedOff = inv.getItem(OFFHAND_SLOT);
		inv.setItem(slot, main);
		inv.setItem(OFFHAND_SLOT, off);
		giveOrDrop(player, displacedMain);
		giveOrDrop(player, displacedOff);

		write(player, List.of(ItemStack.EMPTY, ItemStack.EMPTY));
		LOCK_SLOT.put(player.getUUID(), slot);
		RECORD.put(player.getUUID(), new ItemStack[] {main.copy(), off.copy()});
		sync(player);
		return true;
	}

	/**
	 * Modo DESLIGADO (também ao relogar, morrer ou perder o ODM): recolhe todo grip marcado do inventário
	 * para o storage. Seguro de chamar a qualquer momento.
	 */
	public static void stow(ServerPlayer player) {
		LOCK_SLOT.remove(player.getUUID());
		RECORD.remove(player.getUUID());

		List<ItemStack> stored = read(player);
		boolean changed = false;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!GripMarker.isBound(stack)) continue;
			int index = i == OFFHAND_SLOT ? OFF : MAIN;
			if (!stored.get(index).isEmpty()) index = 1 - index;
			if (stored.get(index).isEmpty()) stored.set(index, stack.copy());
			inv.setItem(i, ItemStack.EMPTY); // se já houver os dois guardados, o excedente some (era cópia)
			changed = true;
		}

		AbstractContainerMenu menu = player.containerMenu;
		if (GripMarker.isBound(menu.getCarried())) {
			menu.setCarried(ItemStack.EMPTY);
		}
		if (changed) write(player, stored);
		sync(player);
	}

	/** Devolve os grips guardados ao inventário (sem ODM vestido ou ao morrer). Viram itens comuns de novo. */
	public static void release(ServerPlayer player) {
		List<ItemStack> stored = read(player);
		if (storedMask(stored) == 0) return;
		write(player, List.of(ItemStack.EMPTY, ItemStack.EMPTY));
		for (ItemStack s : stored) {
			if (!s.isEmpty()) giveOrDrop(player, GripMarker.unmark(s.copy()));
		}
		sync(player);
	}

	// ------------------------------------------------------------------ trava (todo tick, modo ligado)

	public static void enforce(ServerPlayer player) {
		Integer lock = LOCK_SLOT.get(player.getUUID());
		ItemStack[] record = RECORD.get(player.getUUID());
		if (lock == null || record == null || !player.isAlive()) return;

		int slot = lock;
		Inventory inv = player.getInventory();

		// 1) hotbar presa no slot do grip
		if (inv.selected != slot) {
			inv.selected = slot;
			player.connection.send(new ClientboundSetCarriedItemPacket(slot));
		}

		// 2) apaga qualquer grip marcado fora dos dois slots travados (soltar, baú, bancada, cursor, chão)
		sweepStrays(player, slot);

		// 3) repõe o que faltar e guarda o estado atual (durabilidade, lâminas...) como nova referência
		ensure(player, slot, MAIN, record);
		ensure(player, OFFHAND_SLOT, OFF, record);
	}

	private static void ensure(ServerPlayer player, int invSlot, int index, ItemStack[] record) {
		Inventory inv = player.getInventory();
		ItemStack current = inv.getItem(invSlot);
		if (GripMarker.isBound(current)) {
			record[index] = current.copy();
			return;
		}
		inv.setItem(invSlot, record[index].copy());
		giveOrDrop(player, current); // o que o jogador colocou no lugar volta para o inventário
	}

	private static void sweepStrays(ServerPlayer player, int lockSlot) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (i == lockSlot || i == OFFHAND_SLOT) continue;
			if (GripMarker.isBound(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
		}

		for (AbstractContainerMenu menu : new AbstractContainerMenu[] {player.inventoryMenu, player.containerMenu}) {
			if (GripMarker.isBound(menu.getCarried())) menu.setCarried(ItemStack.EMPTY);
			for (Slot s : menu.slots) {
				if (s.container != inv && GripMarker.isBound(s.getItem())) s.set(ItemStack.EMPTY);
			}
		}

		for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(8.0), e -> GripMarker.isBound(e.getItem()))) {
			entity.discard();
		}
	}

	// ------------------------------------------------------------------ sincronização com os clientes

	private static GripHolsterSyncPayload payloadFor(ServerPlayer player) {
		return new GripHolsterSyncPayload(player.getId(), storedMask(read(player)));
	}

	/** Avisa o próprio jogador e quem o está vendo do que está guardado no storage. */
	public static void sync(ServerPlayer player) {
		GripHolsterSyncPayload payload = payloadFor(player);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	private static int storedMask(List<ItemStack> stored) {
		return (stored.get(MAIN).isEmpty() ? 0 : 1) | (stored.get(OFF).isEmpty() ? 0 : 2);
	}

	// ------------------------------------------------------------------ util

	/** Grip comum (não marcado) em um slot de itens do inventário. */
	private static boolean isLooseGrip(int invSlot, ItemStack stack) {
		if (invSlot >= MAIN_INVENTORY_SIZE && invSlot != OFFHAND_SLOT) return false; // armadura
		return !stack.isEmpty() && stack.getItem() == GripItems.get() && !GripMarker.isBound(stack);
	}

	/** Tira um grip do inventário (hotbar, mochila ou mão secundária) e devolve; EMPTY se não houver. */
	private static ItemStack takeFromInventory(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (isLooseGrip(i, stack)) {
				ItemStack taken = stack.copy();
				inv.setItem(i, ItemStack.EMPTY);
				return taken;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) return;
		ItemStack copy = stack.copy();
		if (!player.getInventory().add(copy)) {
			player.drop(copy, false);
		}
	}

	private static List<ItemStack> read(ServerPlayer player) {
		List<ItemStack> stored = player.getAttached(STORE);
		List<ItemStack> out = new ArrayList<>(2);
		for (int i = 0; i < 2; i++) {
			out.add(stored != null && i < stored.size() ? stored.get(i).copy() : ItemStack.EMPTY);
		}
		return out;
	}

	private static void write(ServerPlayer player, List<ItemStack> stacks) {
		player.setAttached(STORE, List.copyOf(stacks));
	}
}
