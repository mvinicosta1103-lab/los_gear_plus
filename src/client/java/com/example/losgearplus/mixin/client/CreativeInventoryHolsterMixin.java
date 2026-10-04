package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import com.example.losgearplus.grip.HolsterSlot;
import com.example.losgearplus.grip.HolsterSlots;
import com.example.losgearplus.grip.SetHolsterSlotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aba de inventário do criativo. Mesmo padrão do slot de harness do DAOT: o criativo troca os slots do inventário por
 * "wrappers" posicionados à mão, então tiramos o wrapper dos nossos slots e colocamos um slot nosso na posição certa.
 * Como o criativo não envia cliques de slots que não são do inventário, o clique é tratado aqui e o resultado vai ao
 * servidor por {@link SetHolsterSlotPayload}.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryHolsterMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

	private CreativeInventoryHolsterMixin() {
		super(null, null, null);
	}

	@Inject(method = "selectTab", at = @At("TAIL"))
	private void losgearplus$placeHolsterSlots(CreativeModeTab tab, CallbackInfo ci) {
		if (this.minecraft == null || this.minecraft.player == null) return;
		NonNullList<Slot> slots = this.menu.slots;
		slots.removeIf(s -> s instanceof HolsterSlot
				|| (s instanceof CreativeSlotWrapperTargetAccessor w && w.losgearplus$getTarget() instanceof HolsterSlot));
		if (tab.getType() != CreativeModeTab.Type.INVENTORY) return;

		Player player = this.minecraft.player;
		for (Slot source : player.inventoryMenu.slots) {
			if (source instanceof HolsterSlot holster) {
				int side = holster.getContainerSlot();
				HolsterSlot slot = new HolsterSlot(holster.container, side,
						HolsterSlots.CREATIVE_X + side * HolsterSlots.CREATIVE_STEP, HolsterSlots.CREATIVE_Y, player);
				slot.index = holster.index;
				slots.add(Math.min(holster.index, slots.size()), slot);
			}
		}
	}

	@Inject(method = "renderBg", at = @At("TAIL"))
	private void losgearplus$drawHolsterFrames(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
		for (Slot slot : this.menu.slots) {
			if (!(slot instanceof HolsterSlot) || !slot.isActive()) continue;
			int fx = this.leftPos + slot.x - 1;
			int fy = this.topPos + slot.y - 1;
			graphics.fill(fx, fy, fx + 17, fy + 17, 0xFF373737);          // borda escura (cima/esquerda)
			graphics.fill(fx + 1, fy + 1, fx + 18, fy + 18, 0xFFFFFFFF);  // borda clara (baixo/direita)
			graphics.fill(fx + 1, fy + 1, fx + 17, fy + 17, 0xFF8B8B8B);  // fundo
			float pulse = 0.55f + 0.25f * (float) Math.sin(net.minecraft.Util.getMillis() / 450.0);
			UiDraw.outline(graphics, fx, fy, 18, 18, UiDraw.alpha(Palette.CRIMSON, pulse));
		}
	}

	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void losgearplus$clickHolsterSlot(Slot slot, int slotId, int mouseButton, ClickType type, CallbackInfo ci) {
		if (!(slot instanceof HolsterSlot holster)) return;
		ci.cancel(); // o clique nunca segue para a lógica do criativo (ela não conhece este slot)
		if (this.minecraft == null || this.minecraft.player == null || !holster.isActive()) return;
		if (type != ClickType.PICKUP) return; // só pegar/soltar com o mouse; shift/swap/drop não se aplicam aqui

		ItemStack carried = this.menu.getCarried();
		if (!carried.isEmpty() && !holster.mayPlace(carried)) return;

		ItemStack inSlot = holster.getItem().copy();
		holster.set(carried.copy());     // troca: o que estava na mão vai para o slot...
		this.menu.setCarried(inSlot);    // ...e o que estava no slot vai para a mão
		ClientPlayNetworking.send(new SetHolsterSlotPayload(holster.getContainerSlot(), carried.copy()));
	}
}
