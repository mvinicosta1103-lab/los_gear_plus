package com.example.losgearplus.client;

import com.example.losgearplus.ModMenus;
import com.example.losgearplus.network.OpenGearPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class LosGearPlusClient implements ClientModInitializer {
	public static KeyMapping OPEN_GEAR;

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.GEAR, GearScreen::new);

		// Tecla G: abre a aba Gear de qualquer lugar (inclusive no criativo).
		OPEN_GEAR = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.open_gear", InputConstants.Type.KEYSYM, InputConstants.KEY_G, "key.categories.los_gear_plus"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_GEAR.consumeClick()) {
				if (client.player != null && client.screen == null) {
					ClientPlayNetworking.send(new OpenGearPayload());
				}
			}
		});

		// Abas em cima do inventário de sobrevivência.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen inventoryScreen)) {
				return;
			}
			ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
					GearTabs.render(graphics, client.font, left(inventoryScreen), top(inventoryScreen),
							GearTabs.INVENTORY, mouseX, mouseY));

			ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
				if (button == 0 && GearTabs.hit(left(inventoryScreen), top(inventoryScreen), mouseX, mouseY) == GearTabs.GEAR) {
					GearTabs.playClick();
					ClientPlayNetworking.send(new OpenGearPayload());
					return false; // clique consumido
				}
				return true;
			});
		});
	}

	// Posição do painel do inventário (muda quando o livro de receitas abre).
	private static int left(InventoryScreen screen) {
		return screen.getRecipeBookComponent().updateScreenPosition(screen.width, 176);
	}

	private static int top(InventoryScreen screen) {
		return (screen.height - 166) / 2;
	}
}