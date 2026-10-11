package com.example.losgearplus.client.colossal;

import com.example.losgearplus.colossal.ColossalNukePayload;
import com.example.losgearplus.colossal.ColossalNukeSyncPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Client side of the Colossal nuke choice: key (default Y) -> asks the server -> opens ColossalNukeScreen. */
public final class ColossalNukeClient {
	private ColossalNukeClient() {}

	private static KeyMapping key;
	/** The menu only opens after the server answers (it knows whether the player is a Colossal shifter). */
	private static boolean openOnSync;

	public static void init() {
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.colossal_nuke",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_Y,
				"key.categories.los_gear_plus"));

		ClientPlayNetworking.registerGlobalReceiver(ColossalNukeSyncPayload.TYPE, (payload, context) -> {
			if (!openOnSync) return;
			openOnSync = false;
			var mc = context.client();
			if (mc.player == null || mc.screen != null) return;
			if (!payload.colossal()) {
				mc.player.displayClientMessage(Component.translatable("los_gear_plus.colossal_nuke.not_colossal"), true);
				return;
			}
			mc.setScreen(new ColossalNukeScreen(payload.nuke()));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> openOnSync = false);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.player != null && mc.screen == null && ClientPlayNetworking.canSend(ColossalNukePayload.TYPE)) {
					openOnSync = true;
					ClientPlayNetworking.send(new ColossalNukePayload(-1)); // query only
				}
			}
		});
	}

	public static void choose(boolean nuke) {
		if (ClientPlayNetworking.canSend(ColossalNukePayload.TYPE)) {
			ClientPlayNetworking.send(new ColossalNukePayload(nuke ? 1 : 0));
		}
	}
}
