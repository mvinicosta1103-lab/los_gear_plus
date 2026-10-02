package com.example.losgearplus.grip;

import com.example.losgearplus.compat.DaotBridge;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Um dos 2 slots de arma ao lado do peitoral. Ver {@link HolsterSlots}. Aceita só armas que o ODM vestido empunha, do mesmo tipo nos dois. */
public final class HolsterSlot extends Slot {
	private final Player player;

	public HolsterSlot(Container container, int containerSlot, int x, int y, Player player) {
		super(container, containerSlot, x, y);
		this.player = player;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		if (!HolsterSlots.available(player) || stack.isEmpty() || GripMarker.isBound(stack)) return false;
		if (!HolsterWeapons.fits(DaotBridge.loadout(player), stack)) return false;
		ItemStack other = container.getItem(1 - getContainerSlot());
		return other.isEmpty() || other.getItem() == stack.getItem();
	}

	@Override
	public boolean mayPickup(Player picker) {
		return HolsterSlots.available(player);
	}

	@Override
	public boolean isActive() {
		return HolsterSlots.available(player);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}
}
