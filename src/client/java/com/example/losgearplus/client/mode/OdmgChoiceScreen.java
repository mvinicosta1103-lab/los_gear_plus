package com.example.losgearplus.client.mode;

import com.example.losgearplus.grip.GripItems;
import com.example.losgearplus.mode.OdmgChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Tela de seleção: aparece ao ligar o ODMG Mode com New ODM Gear + New ODM Uniform vestidos e com grips E pistolas
 * disponíveis. Dois botões: New ODM Gear com blades, ou New ODM Uniform com pistolas. Esc fecha sem escolher.
 */
public final class OdmgChoiceScreen extends Screen {
	private static final int BUTTON_W = 200;
	private static final int BUTTON_H = 24;

	private ItemStack bladeIcon = ItemStack.EMPTY;
	private ItemStack gunIcon = ItemStack.EMPTY;

	public OdmgChoiceScreen() {
		super(Component.translatable("los_gear_plus.choice.title"));
	}

	@Override
	protected void init() {
		Item grip = GripItems.get();
		bladeIcon = grip == null ? ItemStack.EMPTY : new ItemStack(grip);
		gunIcon = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("los_gear", "mauser_rifle"))
				.map(ItemStack::new).orElse(ItemStack.EMPTY);

		int x = this.width / 2 - BUTTON_W / 2;
		int y = this.height / 2 - BUTTON_H - 2;
		addRenderableWidget(Button.builder(Component.translatable("los_gear_plus.choice.blades"), b -> choose(false))
				.bounds(x, y, BUTTON_W, BUTTON_H).build());
		addRenderableWidget(Button.builder(Component.translatable("los_gear_plus.choice.pistols"), b -> choose(true))
				.bounds(x, y + BUTTON_H + 4, BUTTON_W, BUTTON_H).build());
	}

	private void choose(boolean guns) {
		ClientPlayNetworking.send(new OdmgChoicePayload(guns));
		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = this.width / 2 - BUTTON_W / 2;
		int y = this.height / 2 - BUTTON_H - 2;
		graphics.drawCenteredString(this.font, this.title, this.width / 2, y - 18, 0xFFFFFF);
		graphics.renderItem(bladeIcon, x + 4, y + 4);
		graphics.renderItem(gunIcon, x + 4, y + BUTTON_H + 8);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
