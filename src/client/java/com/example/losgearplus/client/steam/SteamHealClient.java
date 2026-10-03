package com.example.losgearplus.client.steam;

import com.example.losgearplus.client.mode.OdmgKeyRegistry;
import com.example.losgearplus.steam.SteamHealPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Tecla do Steam Heal (liga/desliga, padrão: J; K = Tease Shift, H = capuz e Z = ODMG Mode já são usadas). */
public final class SteamHealClient {
	private SteamHealClient() {}

	public static KeyMapping KEY;

	public static void init() {
		KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.steam_heal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
				"key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(KEY); // funciona com o ODMG Mode ligado ou desligado

		ClientTickEvents.END_CLIENT_TICK.register(SteamHealClient::tick);
	}

	private static void tick(Minecraft mc) {
		while (KEY.consumeClick()) {
			if (mc.player != null && mc.screen == null && !mc.player.isSpectator()) {
				ClientPlayNetworking.send(new SteamHealPayload());
			}
		}
	}
}
