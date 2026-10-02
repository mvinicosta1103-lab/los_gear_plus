package com.example.losgearplus.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** O criativo embrulha cada slot do inventário num SlotWrapper; precisamos do slot de dentro para reconhecer os nossos. */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface CreativeSlotWrapperTargetAccessor {
	@Accessor("target")
	Slot losgearplus$getTarget();
}
