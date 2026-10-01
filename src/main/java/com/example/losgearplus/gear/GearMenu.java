package com.example.losgearplus.gear;

import com.example.losgearplus.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GearMenu extends AbstractContainerMenu {
    public static final int GEAR_SLOTS = GearData.SIZE;

    /** Lado do cliente: container vazio, o servidor sincroniza o conteúdo. */
    public GearMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(GEAR_SLOTS));
    }

    public GearMenu(int containerId, Inventory playerInventory, Container gear) {
        super(ModMenus.GEAR, containerId);
        checkContainerSize(gear, GEAR_SLOTS);

        for (GearSlotType type : GearSlotType.values()) {
            addSlot(new GearSlot(gear, type));
        }

        // Inventário do jogador (mesma posição do inventário vanilla)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            original = stack.copy();
            if (index < GEAR_SLOTS) {
                // gear -> inventário
                if (!moveItemStackTo(stack, GEAR_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, GEAR_SLOTS, false)) {
                // inventário -> primeiro slot de gear que aceitar
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Slot que só aceita itens da tag do tipo dele, 1 por slot. */
    public static class GearSlot extends Slot {
        private final GearSlotType type;

        public GearSlot(Container container, GearSlotType type) {
            super(container, type.ordinal(), type.x(), type.y());
            this.type = type;
        }

        public GearSlotType type() {
            return type;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(type.tag());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}