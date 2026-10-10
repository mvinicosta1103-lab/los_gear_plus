package com.example.losgearplus.grip;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.compat.DaotBridge;
import com.example.losgearplus.limb.LimbRules;
import com.example.losgearplus.mode.OdmgModeServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class GripStorage {
	public static final int MIN_GRIPS = 2;
	private static final int MAIN = 0;
	private static final int OFF = 1;
	private static final int OFFHAND_SLOT = 40;
	private static final int MAIN_INVENTORY_SIZE = 36;

	public static final AttachmentType<List<ItemStack>> STORE = AttachmentRegistry.<List<ItemStack>>builder()
			.persistent(ItemStack.OPTIONAL_CODEC.listOf())
			.buildAndRegister(LosGearPlus.id("grip_storage"));
	public static final AttachmentType<List<ItemStack>> ALT = AttachmentRegistry.<List<ItemStack>>builder()
			.persistent(ItemStack.OPTIONAL_CODEC.listOf())
			.buildAndRegister(LosGearPlus.id("grip_storage_alt"));

	private static final Map<UUID, Integer> LOCK_SLOT = new HashMap<>();
	private static final Map<UUID, ItemStack[]> RECORD = new HashMap<>();

	/** [ONE-HAND] Jogadores cujo equip atual foi feito em One-Hand (só a mão principal travada). */
	private static final Set<UUID> ONE_HAND_ACTIVE = new HashSet<>();

	private static int releaseTimer;

	private GripStorage() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(GripHolsterSyncPayload.TYPE, GripHolsterSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetHolsterSlotPayload.TYPE, SetHolsterSlotPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetHolsterSlotPayload.TYPE,
				(payload, context) -> HolsterSlots.handleCreativeSet(context.player(), payload.slot(), payload.stack()));
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof ServerPlayer target) {
				ServerPlayNetworking.send(viewer, payloadFor(target));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++releaseTimer < 20) {
				return;
			}
			releaseTimer = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (isLocked(player)) continue;
				List<ItemStack> stored = read(player);
				List<ItemStack> alt = readAlt(player);
				if (storedMask(stored) == 0 && storedMask(alt) == 0) continue;
				if (!DaotBridge.wearsOdmGear(player)) {
					release(player);
					continue;
				}
				if (!storedFits(player, stored) || !storedFits(player, alt)) {
					release(player);
					continue;
				}
				boolean changedStore = normalize(player, stored);
				boolean changedAlt = normalize(player, alt);
				if (changedStore) {
					write(player, stored);
				}
				if (changedAlt) {
					writeAlt(player, alt);
				}
				if (!changedStore && !changedAlt) continue;
				sync(player);
			}
		});
	}

	private static boolean normalize(ServerPlayer player, List<ItemStack> stored) {
		boolean sheath = DaotBridge.odmType(player) == DaotBridge.OdmType.NEW;
		boolean changed = false;
		for (ItemStack stack : stored) {
			if (stack.isEmpty() || !HolsterWeapons.isGrip(stack)) continue;
			if (sheath) {
				changed |= GripBlade.sheathe(stack);
				continue;
			}
			if (!GripBlade.isSheathed(stack)) continue;
			GripBlade.restore(stack);
			changed = true;
		}
		return changed;
	}

	public static boolean isLocked(ServerPlayer player) {
		return LOCK_SLOT.containsKey(player.getUUID());
	}

	/** [ONE-HAND] true se o equip atual deste jogador é One-Hand. */
	public static boolean isOneHandActive(ServerPlayer player) {
		return ONE_HAND_ACTIVE.contains(player.getUUID());
	}

	public static boolean canEquip(ServerPlayer player) {
		if (!GripItems.isAvailable()) {
			return true;
		}
		if (isLocked(player)) {
			return true;
		}
		return resolve(player, DaotBridge.loadout(player), readAll(player)) != null;
	}

	public static boolean canEquip(ServerPlayer player, HolsterWeapons.Kind kind) {
		if (!GripItems.isAvailable()) {
			return false;
		}
		return resolve(player, DaotBridge.loadout(player), readAll(player), kind) != null;
	}

	public static boolean needsChoice(ServerPlayer player) {
		if (!GripItems.isAvailable() || isLocked(player)) {
			return false;
		}
		if (!DaotBridge.wearsUniformAndGear(player)) {
			return false;
		}
		DaotBridge.Loadout loadout = DaotBridge.loadout(player);
		List<ItemStack> stored = readAll(player);
		return resolve(player, loadout, stored, HolsterWeapons.Kind.BLADES) != null
				&& resolve(player, loadout, stored, HolsterWeapons.Kind.GUNS) != null;
	}

	private static int available(ServerPlayer player, List<ItemStack> stored, Item item) {
		int count = 0;
		for (ItemStack s : stored) {
			if (s.isEmpty() || s.getItem() != item) continue;
			++count;
		}
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); ++i) {
			if (!isLoose(i, inv.getItem(i), item)) continue;
			++count;
		}
		return count;
	}

	private static Item resolve(ServerPlayer player, DaotBridge.Loadout loadout, List<ItemStack> stored) {
		return resolve(player, loadout, stored, null);
	}

	private static Item resolve(ServerPlayer player, DaotBridge.Loadout loadout, List<ItemStack> stored,
								HolsterWeapons.Kind forced) {
		// [ONE-HAND] quantos grips são necessários: 1 (One-Hand) ou 2 (Two-Hand).
		int needed = OdmgModeServer.isOneHand(player) ? 1 : 2;

		if (forced != null) {
			List<Item> options = HolsterWeapons.candidates(loadout, forced);
			ItemStack heldForced = player.getMainHandItem();
			if (!heldForced.isEmpty() && options.contains(heldForced.getItem())
					&& !GripMarker.isBound(heldForced)
					&& available(player, stored, heldForced.getItem()) >= needed) {
				return heldForced.getItem();
			}
			for (ItemStack s : stored) {
				if (s.isEmpty() || !options.contains(s.getItem())
						|| available(player, stored, s.getItem()) < needed) continue;
				return s.getItem();
			}
			for (Item item : options) {
				if (available(player, stored, item) < needed) continue;
				return item;
			}
			return null;
		}
		ItemStack held = player.getMainHandItem();
		if (HolsterWeapons.fits(loadout, held) && !GripMarker.isBound(held)
				&& available(player, stored, held.getItem()) >= needed) {
			return held.getItem();
		}
		for (ItemStack s : stored) {
			if (s.isEmpty() || !HolsterWeapons.fits(loadout, s)
					|| available(player, stored, s.getItem()) < needed) continue;
			return s.getItem();
		}
		for (Item item : HolsterWeapons.candidates(loadout)) {
			if (available(player, stored, item) < needed) continue;
			return item;
		}
		return null;
	}

	private static boolean storedFits(ServerPlayer player, List<ItemStack> stored) {
		DaotBridge.Loadout loadout = DaotBridge.loadout(player);
		for (ItemStack s : stored) {
			if (s.isEmpty() || HolsterWeapons.fits(loadout, s)) continue;
			return false;
		}
		return true;
	}

	public static boolean equip(ServerPlayer player) {
		return equip(player, null);
	}

	public static boolean equip(ServerPlayer player, HolsterWeapons.Kind kind) {
		if (GripItems.get() == null) {
			return true;
		}
		if (isLocked(player)) {
			return true;
		}
		List<ItemStack> stored = read(player);
		List<ItemStack> alt = readAlt(player);
		List<ItemStack> all = new ArrayList<>(stored);
		all.addAll(alt);
		Item weapon = resolve(player, DaotBridge.loadout(player), all, kind);
		if (weapon == null) {
			return false;
		}

		// [ONE-HAND] só a mão principal recebe grip; o offhand fica intocado.
		boolean one = OdmgModeServer.isOneHand(player);

		boolean keepOthers = DaotBridge.wearsUniformAndGear(player);
		List<ItemStack> remaining = new ArrayList<>(stored);
		List<ItemStack> remainingAlt = new ArrayList<>(alt);
		ItemStack main = ItemStack.EMPTY;
		ItemStack off = ItemStack.EMPTY;
		boolean mainFromStore = false;
		boolean offFromStore = false;
		for (int pass = 0; pass < 2; ++pass) {
			List<ItemStack> src = pass == 0 ? stored : alt;
			List<ItemStack> rem = pass == 0 ? remaining : remainingAlt;
			for (int i = 0; i < src.size(); ++i) {
				ItemStack s = src.get(i);
				if (s.isEmpty()) continue;
				if (s.getItem() == weapon) {
					if (i == 0 && main.isEmpty()) {
						main = s;
						mainFromStore = true;
					} else if (!one && i == 1 && off.isEmpty()) {
						off = s;
						offFromStore = true;
					} else if (main.isEmpty()) {
						main = s;
						mainFromStore = true;
					} else {
						// [ONE-HAND] o segundo grip continua guardado no holster.
						if (one || !off.isEmpty()) continue;
						off = s;
						offFromStore = true;
					}
					rem.set(i, ItemStack.EMPTY);
					continue;
				}
				if (keepOthers) continue;
				giveOrDrop(player, GripMarker.unmark(GripBlade.restore(s.copy())));
				rem.set(i, ItemStack.EMPTY);
			}
		}
		if (main.isEmpty()) {
			main = takeFromInventory(player, weapon);
		}
		if (!one && off.isEmpty()) {
			off = takeFromInventory(player, weapon);
		}
		if (main.isEmpty() || (!one && off.isEmpty())) {
			if (!mainFromStore) {
				giveOrDrop(player, GripMarker.unmark(main));
			}
			if (!offFromStore) {
				giveOrDrop(player, GripMarker.unmark(off));
			}
			return false;
		}

		GripBlade.restore(main);
		GripMarker.mark(main);
		if (!one) {
			GripBlade.restore(off);
			GripMarker.mark(off);
		}

		Inventory inv = player.getInventory();
		int slot = inv.selected;
		ItemStack displacedMain = inv.getItem(slot);
		inv.setItem(slot, main);
		giveOrDrop(player, displacedMain);
		if (!one) {
			ItemStack displacedOff = inv.getItem(OFFHAND_SLOT);
			inv.setItem(OFFHAND_SLOT, off);
			giveOrDrop(player, displacedOff);
		}

		write(player, remaining);
		writeAlt(player, remainingAlt);
		LOCK_SLOT.put(player.getUUID(), slot);
		RECORD.put(player.getUUID(), new ItemStack[]{main.copy(), one ? ItemStack.EMPTY : off.copy()});
		if (one) {
			ONE_HAND_ACTIVE.add(player.getUUID());
		} else {
			ONE_HAND_ACTIVE.remove(player.getUUID());
		}
		enforce(player);
		sync(player);
		return true;
	}

	public static void stow(ServerPlayer player) {
		ONE_HAND_ACTIVE.remove(player.getUUID());
		Integer lockedSlot = LOCK_SLOT.remove(player.getUUID());
		ItemStack[] parked = RECORD.remove(player.getUUID());
		List<ItemStack> stored = read(player);
		List<ItemStack> alt = readAlt(player);
		boolean changed = false;
		boolean changedAlt = false;
		Inventory inv = player.getInventory();
		if (parked != null) {
			for (int index = 0; index < 2; ++index) {
				InteractionHand hand = index == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				int invSlot = index == 0 ? (lockedSlot != null ? lockedSlot : inv.selected) : OFFHAND_SLOT;
				// Em One-Hand parked[1] é EMPTY, então o offhand é pulado aqui.
				if (parked[index].isEmpty() || LimbRules.handUsable(player, hand)
						|| GripMarker.isBound(inv.getItem(invSlot))) continue;
				ItemStack displaced = inv.getItem(invSlot);
				inv.setItem(invSlot, parked[index].copy());
				giveOrDrop(player, displaced);
			}
		}
		for (int i = 0; i < inv.getContainerSize(); ++i) {
			ItemStack stack = inv.getItem(i);
			if (!GripMarker.isBound(stack)) continue;
			int hand = i == OFFHAND_SLOT ? 1 : 0;
			if (storeAccepts(stored, stack)) {
				int index = hand;
				if (!stored.get(index).isEmpty()) {
					index = 1 - index;
				}
				if (stored.get(index).isEmpty()) {
					stored.set(index, GripMarker.unmark(stack.copy()));
				}
				inv.setItem(i, ItemStack.EMPTY);
				changed = true;
				continue;
			}
			if (storeAccepts(alt, stack)) {
				int index = hand;
				if (!alt.get(index).isEmpty()) {
					index = 1 - index;
				}
				inv.setItem(i, ItemStack.EMPTY);
				if (alt.get(index).isEmpty()) {
					alt.set(index, GripMarker.unmark(stack.copy()));
					changedAlt = true;
					continue;
				}
				giveOrDrop(player, GripMarker.unmark(GripBlade.restore(stack.copy())));
				continue;
			}
			inv.setItem(i, ItemStack.EMPTY);
			giveOrDrop(player, GripMarker.unmark(GripBlade.restore(stack.copy())));
		}
		AbstractContainerMenu menu = player.containerMenu;
		if (GripMarker.isBound(menu.getCarried())) {
			menu.setCarried(ItemStack.EMPTY);
		}
		changedAlt |= normalize(player, alt);
		changed |= normalize(player, stored);
		if (changed) {
			write(player, stored);
		}
		if (changedAlt) {
			writeAlt(player, alt);
		}
		sync(player);
	}

	public static void release(ServerPlayer player) {
		List<ItemStack> stored = read(player);
		List<ItemStack> alt = readAlt(player);
		if (storedMask(stored) == 0 && storedMask(alt) == 0) {
			return;
		}
		write(player, List.of(ItemStack.EMPTY, ItemStack.EMPTY));
		writeAlt(player, List.of(ItemStack.EMPTY, ItemStack.EMPTY));
		for (List<ItemStack> list : List.of(stored, alt)) {
			for (ItemStack s : list) {
				if (s.isEmpty()) continue;
				giveOrDrop(player, GripMarker.unmark(GripBlade.restore(s.copy())));
			}
		}
		sync(player);
	}

	public static void enforce(ServerPlayer player) {
		Integer lock = LOCK_SLOT.get(player.getUUID());
		ItemStack[] record = RECORD.get(player.getUUID());
		if (lock == null || record == null || !player.isAlive()) {
			return;
		}
		int slot = lock;
		Inventory inv = player.getInventory();
		if (inv.selected != slot) {
			inv.selected = slot;
			player.connection.send(new ClientboundSetCarriedItemPacket(slot));
		}
		sweepStrays(player, slot);
		if (LimbRules.handUsable(player, InteractionHand.MAIN_HAND)) {
			ensure(player, slot, 0, record);
		} else {
			park(player, slot, 0, record);
		}
		// [ONE-HAND] em One-Hand o offhand é livre: não forçar grip nele.
		if (!ONE_HAND_ACTIVE.contains(player.getUUID())) {
			if (LimbRules.handUsable(player, InteractionHand.OFF_HAND)) {
				ensure(player, OFFHAND_SLOT, 1, record);
			} else {
				park(player, OFFHAND_SLOT, 1, record);
			}
		}
	}

	private static void park(ServerPlayer player, int invSlot, int index, ItemStack[] record) {
		Inventory inv = player.getInventory();
		ItemStack current = inv.getItem(invSlot);
		if (GripMarker.isBound(current)) {
			record[index] = current.copy();
			inv.setItem(invSlot, ItemStack.EMPTY);
		}
	}

	private static void ensure(ServerPlayer player, int invSlot, int index, ItemStack[] record) {
		Inventory inv = player.getInventory();
		ItemStack current = inv.getItem(invSlot);
		if (GripMarker.isBound(current)) {
			record[index] = current.copy();
			return;
		}
		inv.setItem(invSlot, record[index].copy());
		giveOrDrop(player, current);
	}

	private static void sweepStrays(ServerPlayer player, int lockSlot) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); ++i) {
			if (i == lockSlot || i == OFFHAND_SLOT || !GripMarker.isBound(inv.getItem(i))) continue;
			inv.setItem(i, ItemStack.EMPTY);
		}
		for (AbstractContainerMenu menu : new AbstractContainerMenu[]{player.inventoryMenu, player.containerMenu}) {
			if (GripMarker.isBound(menu.getCarried())) {
				menu.setCarried(ItemStack.EMPTY);
			}
			for (Slot s : menu.slots) {
				if (s.container == inv || !GripMarker.isBound(s.getItem())) continue;
				s.set(ItemStack.EMPTY);
			}
		}
		for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(8.0), e -> GripMarker.isBound(e.getItem()))) {
			entity.discard();
		}
	}

	private static GripHolsterSyncPayload payloadFor(ServerPlayer player) {
		List<ItemStack> stored = read(player);
		List<ItemStack> alt = readAlt(player);
		return new GripHolsterSyncPayload(player.getId(), stored.get(0), stored.get(1), alt.get(0), alt.get(1));
	}

	public static void sync(ServerPlayer player) {
		GripHolsterSyncPayload payload = payloadFor(player);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	private static boolean storeAccepts(List<ItemStack> stored, ItemStack stack) {
		for (ItemStack s : stored) {
			if (s.isEmpty() || s.getItem() == stack.getItem()) continue;
			return false;
		}
		return true;
	}

	private static int storedMask(List<ItemStack> stored) {
		return (stored.get(0).isEmpty() ? 0 : 1) | (stored.get(1).isEmpty() ? 0 : 2);
	}

	private static boolean isLoose(int invSlot, ItemStack stack, Item item) {
		if (invSlot >= MAIN_INVENTORY_SIZE && invSlot != OFFHAND_SLOT) {
			return false;
		}
		return !stack.isEmpty() && stack.getItem() == item && !GripMarker.isBound(stack);
	}

	private static ItemStack takeFromInventory(ServerPlayer player, Item item) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); ++i) {
			ItemStack stack = inv.getItem(i);
			if (!isLoose(i, stack, item)) continue;
			ItemStack taken = stack.copy();
			taken.setCount(1);
			stack.shrink(1);
			if (stack.isEmpty()) {
				inv.setItem(i, ItemStack.EMPTY);
			}
			return taken;
		}
		return ItemStack.EMPTY;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
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

	private static List<ItemStack> readAll(ServerPlayer player) {
		List<ItemStack> all = new ArrayList<>(read(player));
		all.addAll(readAlt(player));
		return all;
	}

	private static List<ItemStack> readFrom(ServerPlayer player, AttachmentType<List<ItemStack>> type) {
		List<ItemStack> stored = player.getAttached(type);
		List<ItemStack> out = new ArrayList<>(2);
		for (int i = 0; i < 2; ++i) {
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