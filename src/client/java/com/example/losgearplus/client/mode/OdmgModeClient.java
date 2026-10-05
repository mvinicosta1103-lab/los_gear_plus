package com.example.losgearplus.client.mode;

import com.example.losgearplus.grip.GripItems;
import com.example.losgearplus.mode.OdmgModeSyncPayload;
import com.example.losgearplus.mode.SwapOdmgKindPayload;
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
	/** Modo ligado com armas de fogo (pistolas/APG) nas mãos. */
	private static boolean guns;
	public static KeyMapping TOGGLE;
	/** Troca o funcionamento: New ODM Gear com blades <-> New ODM Uniform com pistolas (padrão: P). */
	public static KeyMapping SWAP;

	public static boolean isActive() {
		return active;
	}

	public static boolean isGuns() {
		return guns;
	}

	/** Grip Storage: com o modo ligado a hotbar fica presa no slot do grip, então o scroll não pode trocar de slot. */
	public static boolean blocksHotbarScroll() {
		Minecraft mc = Minecraft.getInstance();
		return active && GripItems.isAvailable() && mc.player != null && mc.screen == null;
	}

	/** Chame em LosGearPlusClient.onInitializeClient(). */
	public static void init() {
		TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.toggle_odmg_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
				"key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(TOGGLE); // nunca pode ser "fechada", senão não dá para sair do modo

		SWAP = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.swap_odmg_kind", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
				"key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(SWAP); // funciona com o modo ligado ou desligado

		ClientPlayNetworking.registerGlobalReceiver(OdmgModeSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> setActive(payload.active(), payload.guns(), true)));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> OdmgKeyRegistry.logRoles());
		ClientTickEvents.END_CLIENT_TICK.register(OdmgModeClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> setActive(false, false, false));

		OdmgHookAngleClient.init();
		OdmgSpeedLevel.init();
		PistolSpearLoadKey.init(); // V com pistolas nas mãos carrega a (Quad) Thunder Spear
		OdmgRunAnimation.init(); // corrida do ODMG (odmrun) no lugar do sprint vanilla
	}

	private static void tick(Minecraft mc) {
		while (TOGGLE.consumeClick()) {
			if (mc.player != null && mc.screen == null && !mc.player.isSpectator()) {
				ClientPlayNetworking.send(new ToggleOdmgModePayload());
			}
		}
		while (SWAP.consumeClick()) {
			if (mc.player != null && mc.screen == null && !mc.player.isSpectator()) {
				ClientPlayNetworking.send(new SwapOdmgKindPayload());
			}
		}
	}

	static void setActive(boolean value, boolean gunsNow, boolean announce) {
		guns = value && gunsNow;
		if (active == value) return;
		active = value;
		OdmgKeyRegistry.releaseAffected();
		if (announce) {
			Minecraft.getInstance().gui.setOverlayMessage(
					Component.translatable(value ? "los_gear_plus.mode.odmg_on" : "los_gear_plus.mode.odmg_off"), false);
		}
	}
}