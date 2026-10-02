package com.example.losgearplus.grip;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.compat.DaotBridge;
import com.example.losgearplus.compat.DaotBridge.Loadout;
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

	/**
	 * Segundo storage, para o OUTRO tipo de arma: com New ODM Uniform + New ODM Gear, as pistolas ficam em {@link #STORE}
	 * (slots do peitoral) e as blades guardadas vão para cá (ou o contrário). Também é desenhado no corpo.
	 */
	public static final AttachmentType<List<ItemStack>> ALT = AttachmentRegistry.<List<ItemStack>>builder()
			.persistent(ItemStack.OPTIONAL_CODEC.listOf())
			.buildAndRegister(LosGearPlus.id("grip_storage_alt"));

	/** Slot da hotbar travado com o grip da mão principal (só jogadores com o modo ligado). Só thread do servidor. */
	private static final Map<UUID, Integer> LOCK_SLOT = new HashMap<>();
	/** Cópia de referência dos grips em uso, para repor se algo escapar. */
	private static final Map<UUID, ItemStack[]> RECORD = new HashMap<>();

	private static int releaseTimer;

	public static void init() {
		PayloadTypeRegistry.playS2C().register(GripHolsterSyncPayload.TYPE, GripHolsterSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetHolsterSlotPayload.TYPE, SetHolsterSlotPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetHolsterSlotPayload.TYPE,
				(payload, context) -> HolsterSlots.handleCreativeSet(context.player(), payload.slot(), payload.stack()));

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
				if (isLocked(player)) continue;
				List<ItemStack> stored = read(player);
				List<ItemStack> alt = readAlt(player);
				if (storedMask(stored) == 0 && storedMask(alt) == 0) continue;
				if (!DaotBridge.wearsOdmGear(player)) {
					release(player);
				} else if (!storedFits(player, stored) || !storedFits(player, alt)) { // trocou de ODM: as armas guardadas não servem mais
					release(player);
				} else {
					boolean changedStore = normalize(player, stored); // trocou de ODM com grips guardados
					boolean changedAlt = normalize(player, alt);
					if (changedStore) write(player, stored);
					if (changedAlt) writeAlt(player, alt);
					if (changedStore || changedAlt) sync(player);
				}
			}
		});
	}

	/**
	 * Ajusta a lâmina dos grips guardados ao ODM vestido. New ODM Gear (sem scabbard): a lâmina é recolhida
	 * (o grip fica só com o cabo). ODM Gear do DAOT: a lâmina fica no grip e é desenhada na boca do scabbard.
	 * Nos dois casos o estado/desgaste da lâmina é preservado. Retorna true se algo mudou.
	 */
	private static boolean normalize(ServerPlayer player, List<ItemStack> stored) {
		boolean sheath = DaotBridge.odmType(player) == DaotBridge.OdmType.NEW;
		boolean changed = false;
		for (ItemStack stack : stored) {
			if (stack.isEmpty() || !HolsterWeapons.isGrip(stack)) continue; // APG Gun / pistola não têm lâmina
			if (sheath) {
				changed |= GripBlade.sheathe(stack);
			} else if (GripBlade.isSheathed(stack)) {
				GripBlade.restore(stack);
				changed = true;
			}
		}
		return changed;
	}

	public static boolean isLocked(ServerPlayer player) {
		return LOCK_SLOT.containsKey(player.getUUID());
	}

	// ------------------------------------------------------------------ pré-requisito

	/** True se o jogador tem grips suficientes (guardados + no inventário) para ligar o modo. */
	public static boolean canEquip(ServerPlayer player) {
		if (!GripItems.isAvailable()) return true; // item do grip não achado: o sistema fica desligado
		if (isLocked(player)) return true;
		return resolve(player, DaotBridge.loadout(player), readAll(player)) != null;
	}

	/** True se há um par de armas desta família (blades ou armas de fogo) disponível, guardado ou no inventário. */
	public static boolean canEquip(ServerPlayer player, HolsterWeapons.Kind kind) {
		if (!GripItems.isAvailable()) return false;
		return resolve(player, DaotBridge.loadout(player), readAll(player), kind) != null;
	}

	/**
	 * Mostra a tela de seleção? Só com New ODM Uniform + New ODM Gear vestidos E com um par de grips E um par de
	 * armas de fogo disponíveis (guardados nos slots ou soltos no inventário). Com só um ODM, ou só um tipo de
	 * arma, não há o que escolher.
	 */
	public static boolean needsChoice(ServerPlayer player) {
		if (!GripItems.isAvailable() || isLocked(player)) return false;
		if (!DaotBridge.wearsUniformAndGear(player)) return false;
		Loadout loadout = DaotBridge.loadout(player);
		List<ItemStack> stored = readAll(player);
		return resolve(player, loadout, stored, HolsterWeapons.Kind.BLADES) != null
				&& resolve(player, loadout, stored, HolsterWeapons.Kind.GUNS) != null;
	}

	/** Guardado ou solto no inventário: quantas unidades deste item o jogador tem (para formar o par). */
	private static int available(ServerPlayer player, List<ItemStack> stored, Item item) {
		int count = 0;
		for (ItemStack s : stored) if (!s.isEmpty() && s.getItem() == item) count++;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isLoose(i, inv.getItem(i), item)) count++;
		}
		return count;
	}

	/**
	 * Qual item vira o par de armas deste jogador (2 grips, 2 APG Guns ou 2 pistolas), ou null se não houver
	 * duas unidades do mesmo tipo que sirvam para o ODM vestido. Prioridade: o item da mão principal, depois o
	 * que já está guardado, depois grip > APG Gun > pistola.
	 */
	private static Item resolve(ServerPlayer player, Loadout loadout, List<ItemStack> stored) {
		return resolve(player, loadout, stored, null);
	}

	/** Igual ao resolve normal, mas se {@code forced} != null só considera aquela família (blades ou armas de fogo). */
	private static Item resolve(ServerPlayer player, Loadout loadout, List<ItemStack> stored, HolsterWeapons.Kind forced) {
		if (forced != null) {
			List<Item> options = HolsterWeapons.candidates(loadout, forced);
			ItemStack heldForced = player.getMainHandItem();
			if (!heldForced.isEmpty() && options.contains(heldForced.getItem()) && !GripMarker.isBound(heldForced)
					&& available(player, stored, heldForced.getItem()) >= MIN_GRIPS) {
				return heldForced.getItem();
			}
			for (ItemStack s : stored) {
				if (!s.isEmpty() && options.contains(s.getItem()) && available(player, stored, s.getItem()) >= MIN_GRIPS) {
					return s.getItem();
				}
			}
			for (Item item : options) {
				if (available(player, stored, item) >= MIN_GRIPS) return item;
			}
			return null;
		}
		ItemStack held = player.getMainHandItem();
		if (HolsterWeapons.fits(loadout, held) && !GripMarker.isBound(held)
				&& available(player, stored, held.getItem()) >= MIN_GRIPS) {
			return held.getItem();
		}
		for (ItemStack s : stored) {
			if (!s.isEmpty() && HolsterWeapons.fits(loadout, s) && available(player, stored, s.getItem()) >= MIN_GRIPS) {
				return s.getItem();
			}
		}
		for (Item item : HolsterWeapons.candidates(loadout)) {
			if (available(player, stored, item) >= MIN_GRIPS) return item;
		}
		return null;
	}

	/** Tudo que está guardado serve para o ODM vestido agora? */
	private static boolean storedFits(ServerPlayer player, List<ItemStack> stored) {
		Loadout loadout = DaotBridge.loadout(player);
		for (ItemStack s : stored) {
			if (!s.isEmpty() && !HolsterWeapons.fits(loadout, s)) return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ ligar / desligar

	/**
	 * Modo LIGADO: usa os grips guardados e completa com os do inventário. Retorna false (sem mexer em nada)
	 * se não houver grips suficientes.
	 */
	public static boolean equip(ServerPlayer player) {
		return equip(player, null);
	}

	/**
	 * Quando New ODM Uniform e New ODM Gear estão vestidos juntos, {@code kind} diz se o jogador escolheu
	 * blades (grips) ou armas de fogo (pistolas / APG Guns). null = escolha automática de sempre.
	 */
	public static boolean equip(ServerPlayer player, HolsterWeapons.Kind kind) {
		if (GripItems.get() == null) return true;
		if (isLocked(player)) return true;

		List<ItemStack> stored = read(player);
		List<ItemStack> alt = readAlt(player);
		List<ItemStack> all = new ArrayList<>(stored);
		all.addAll(alt);
		Item weapon = resolve(player, DaotBridge.loadout(player), all, kind);
		if (weapon == null) return false;

		// Com New ODM Uniform + New ODM Gear vestidos, o outro tipo de arma (ex.: pistolas nos slots do peitoral enquanto
		// as blades estão nas mãos) CONTINUA guardado até o jogador trocar (tecla P). Nos demais casos, o que estava
		// guardado e é de outro tipo volta ao inventário.
		boolean keepOthers = DaotBridge.wearsUniformAndGear(player);
		List<ItemStack> remaining = new ArrayList<>(stored);
		List<ItemStack> remainingAlt = new ArrayList<>(alt);
		ItemStack main = ItemStack.EMPTY;
		ItemStack off = ItemStack.EMPTY;
		boolean mainFromStore = false;
		boolean offFromStore = false;
		for (int pass = 0; pass < 2; pass++) {
			List<ItemStack> src = pass == 0 ? stored : alt;
			List<ItemStack> rem = pass == 0 ? remaining : remainingAlt;
			for (int i = 0; i < src.size(); i++) {
				ItemStack s = src.get(i);
				if (s.isEmpty()) continue;
				if (s.getItem() == weapon) {
					if (i == MAIN && main.isEmpty()) {
						main = s;
						mainFromStore = true;
					} else if (i == OFF && off.isEmpty()) {
						off = s;
						offFromStore = true;
					} else if (main.isEmpty()) {
						main = s;
						mainFromStore = true;
					} else if (off.isEmpty()) {
						off = s;
						offFromStore = true;
					} else {
						continue; // as duas mãos já têm arma; este fica guardado
					}
					rem.set(i, ItemStack.EMPTY);
				} else if (!keepOthers) {
					giveOrDrop(player, GripMarker.unmark(GripBlade.restore(s.copy())));
					rem.set(i, ItemStack.EMPTY);
				}
			}
		}

		if (main.isEmpty()) main = takeFromInventory(player, weapon);
		if (off.isEmpty()) off = takeFromInventory(player, weapon);
		if (main.isEmpty() || off.isEmpty()) { // não deveria acontecer depois do resolve; desfaz por segurança
			if (!mainFromStore) giveOrDrop(player, GripMarker.unmark(main));
			if (!offFromStore) giveOrDrop(player, GripMarker.unmark(off));
			return false;
		}
		GripBlade.restore(main); // lâmina recolhida volta pronta para o combate
		GripBlade.restore(off);
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

		write(player, remaining); // o que ficou de fora (outro tipo de arma) continua guardado
		writeAlt(player, remainingAlt);
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
		List<ItemStack> alt = readAlt(player);
		boolean changed = false;
		boolean changedAlt = false;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!GripMarker.isBound(stack)) continue;
			int hand = i == OFFHAND_SLOT ? OFF : MAIN;
			if (storeAccepts(stored, stack)) {
				int index = hand;
				if (!stored.get(index).isEmpty()) index = 1 - index;
				if (stored.get(index).isEmpty()) stored.set(index, GripMarker.unmark(stack.copy())); // guardado = item comum (os slots do peitoral o mostram)
				inv.setItem(i, ItemStack.EMPTY); // se já houver os dois guardados, o excedente some (era cópia)
				changed = true;
			} else if (storeAccepts(alt, stack)) {
				// O storage principal (slots do peitoral) guarda o OUTRO tipo de arma (ex.: pistolas com as blades nas mãos):
				// ele fica como está e esta arma vai para o segundo storage, também desenhado no corpo.
				int index = hand;
				if (!alt.get(index).isEmpty()) index = 1 - index;
				inv.setItem(i, ItemStack.EMPTY);
				if (alt.get(index).isEmpty()) {
					alt.set(index, GripMarker.unmark(stack.copy()));
					changedAlt = true;
				} else {
					giveOrDrop(player, GripMarker.unmark(GripBlade.restore(stack.copy()))); // sem lugar: volta ao inventário
				}
			} else {
				inv.setItem(i, ItemStack.EMPTY); // os dois storages têm tipos diferentes: volta ao inventário
				giveOrDrop(player, GripMarker.unmark(GripBlade.restore(stack.copy())));
			}
		}

		AbstractContainerMenu menu = player.containerMenu;
		if (GripMarker.isBound(menu.getCarried())) {
			menu.setCarried(ItemStack.EMPTY);
		}
		changed |= normalize(player, stored);
		changedAlt |= normalize(player, alt);
		if (changed) write(player, stored);
		if (changedAlt) writeAlt(player, alt);
		sync(player);
	}

	/** Devolve os grips guardados ao inventário (sem ODM vestido ou ao morrer). Viram itens comuns de novo. */
	public static void release(ServerPlayer player) {
		List<ItemStack> stored = read(player);
		List<ItemStack> alt = readAlt(player);
		if (storedMask(stored) == 0 && storedMask(alt) == 0) return;
		write(player, List.of(ItemStack.EMPTY, ItemStack.EMPTY));
		writeAlt(player, List.of(ItemStack.EMPTY, ItemStack.EMPTY));
		for (List<ItemStack> list : List.of(stored, alt)) {
			for (ItemStack s : list) {
				if (!s.isEmpty()) giveOrDrop(player, GripMarker.unmark(GripBlade.restore(s.copy())));
			}
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
		List<ItemStack> stored = read(player);
		List<ItemStack> alt = readAlt(player);
		return new GripHolsterSyncPayload(player.getId(), stored.get(MAIN), stored.get(OFF), alt.get(MAIN), alt.get(OFF));
	}

	/** Avisa o próprio jogador e quem o está vendo do que está guardado no storage. */
	public static void sync(ServerPlayer player) {
		GripHolsterSyncPayload payload = payloadFor(player);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	/** O storage está vazio ou guarda o mesmo tipo de arma de {@code stack}? */
	private static boolean storeAccepts(List<ItemStack> stored, ItemStack stack) {
		for (ItemStack s : stored) {
			if (!s.isEmpty() && s.getItem() != stack.getItem()) return false;
		}
		return true;
	}

	private static int storedMask(List<ItemStack> stored) {
		return (stored.get(MAIN).isEmpty() ? 0 : 1) | (stored.get(OFF).isEmpty() ? 0 : 2);
	}

	// ------------------------------------------------------------------ util

	/** Arma comum (não marcada) deste item em um slot de itens do inventário. */
	private static boolean isLoose(int invSlot, ItemStack stack, Item item) {
		if (invSlot >= MAIN_INVENTORY_SIZE && invSlot != OFFHAND_SLOT) return false; // armadura
		return !stack.isEmpty() && stack.getItem() == item && !GripMarker.isBound(stack);
	}

	/** Tira uma unidade deste item do inventário (hotbar, mochila ou mão secundária) e devolve; EMPTY se não houver. */
	private static ItemStack takeFromInventory(ServerPlayer player, Item item) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (isLoose(i, stack, item)) {
				ItemStack taken = stack.copy();
				taken.setCount(1);
				stack.shrink(1);
				if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
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

	static List<ItemStack> read(ServerPlayer player) {
		return readFrom(player, STORE);
	}

	static List<ItemStack> readAlt(ServerPlayer player) {
		return readFrom(player, ALT);
	}

	/** Os dois storages juntos (4 stacks), para contar as armas disponíveis. */
	private static List<ItemStack> readAll(ServerPlayer player) {
		List<ItemStack> all = new ArrayList<>(read(player));
		all.addAll(readAlt(player));
		return all;
	}

	private static List<ItemStack> readFrom(ServerPlayer player, AttachmentType<List<ItemStack>> type) {
		List<ItemStack> stored = player.getAttached(type);
		List<ItemStack> out = new ArrayList<>(2);
		for (int i = 0; i < 2; i++) {
			out.add(stored != null && i < stored.size() ? stored.get(i).copy() : ItemStack.EMPTY);
		}
		return out;
	}

	static void write(ServerPlayer player, List<ItemStack> stacks) {
		player.setAttached(STORE, List.copyOf(stacks));
	}

	static void writeAlt(ServerPlayer player, List<ItemStack> stacks) {
		player.setAttached(ALT, List.copyOf(stacks));
	}
}
