package com.example.losgearplus.mixin.client;

import com.example.losgearplus.grip.HolsterSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Desenha a moldura dos 2 slots de arma no inventário normal (copia a moldura do slot da mão secundária). */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenHolsterMixin extends AbstractContainerScreen<InventoryMenu> {
	private static final ResourceLocation LOSGEARPLUS_INVENTORY_TEXTURE =
			ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

	protected InventoryScreenHolsterMixin(InventoryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Inject(method = "renderBg", at = @At("TAIL"))
	private void losgearplus$drawHolsterFrames(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
		for (Slot slot : this.menu.slots) {
			if (slot instanceof HolsterSlot && slot.isActive()) {
				graphics.blit(LOSGEARPLUS_INVENTORY_TEXTURE, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, 76, 61, 18, 18);
			}
		}
	}
}
