package com.example.losgearplus.client.mode;

import com.example.losgearplus.mode.OdmgModeSyncPayload;
import com.example.losgearplus.mode.ToggleOdmgModePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Lado cliente do ODMG Mode: espelha o estado do servidor e dispara a troca de keybinds. */
public final class OdmgModeClient {
	private OdmgModeClient() {}

	private static boolean active;
	public static KeyMapping TOGGLE;

	public static boolean isActive() {
		return active;
	}

	/** Chame em LosGearPlusClient.onInitializeClient(). */
	public static void init() {
		TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.toggle_odmg_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
				"key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(TOGGLE); // nunca pode ser "fechada", senão não dá para sair do modo

		ClientPlayNetworking.registerGlobalReceiver(OdmgModeSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> setActive(payload.active(), true)));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> OdmgKeyRegistry.logRoles());
		ClientTickEvents.END_CLIENT_TICK.register(OdmgModeClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> setActive(false, false));
	}

	private static void tick(Minecraft mc) {
		while (TOGGLE.consumeClick()) {
			if (mc.player != null && mc.screen == null && !mc.player.isSpectator()) {
				ClientPlayNetworking.send(new ToggleOdmgModePayload());
			}
		}
	}

	static void setActive(boolean value, boolean announce) {
		if (active == value) return;
		active = value;
		OdmgKeyRegistry.releaseAffected();
		if (announce) {
			Minecraft.getInstance().gui.setOverlayMessage(
					Component.translatable(value ? "los_gear_plus.mode.odmg_on" : "los_gear_plus.mode.odmg_off"), false);
		}
	}
}
