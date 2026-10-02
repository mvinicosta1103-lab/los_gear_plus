package com.example.losgearplus.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** addSlot é protected; este invoker deixa o mixin do InventoryMenu adicionar os slots de arma. */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAddSlotInvoker {
	@Invoker("addSlot")
	Slot losgearplus$addSlot(Slot slot);
}
