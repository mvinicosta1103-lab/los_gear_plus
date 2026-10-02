package com.example.losgearplus.mixin;

import com.example.losgearplus.grip.HolsterContainer;
import com.example.losgearplus.grip.HolsterSlot;
import com.example.losgearplus.grip.HolsterSlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mesmo padrão do slot de harness do DAOT: acrescenta ao menu do inventário (cliente e servidor) os 2 slots de arma
 * ao lado do peitoral. Eles ficam no fim da lista de slots, então os índices do vanilla não mudam.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuHolsterSlotMixin {

	@Inject(method = "<init>", at = @At("TAIL"))
	private void losgearplus$addHolsterSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
		AbstractContainerMenuAddSlotInvoker menu = (AbstractContainerMenuAddSlotInvoker) (Object) this;
		HolsterContainer container = new HolsterContainer(owner);
		menu.losgearplus$addSlot(new HolsterSlot(container, 0, HolsterSlots.SURVIVAL_X, HolsterSlots.SURVIVAL_Y0, owner));
		menu.losgearplus$addSlot(new HolsterSlot(container, 1, HolsterSlots.SURVIVAL_X, HolsterSlots.SURVIVAL_Y1, owner));
	}
}
