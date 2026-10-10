package com.example.losgearplus.client.mode;

import com.example.losgearplus.grip.GripItems;
import com.example.losgearplus.mode.OdmgHandsSyncPayload;
import com.example.losgearplus.mode.OdmgModeSyncPayload;
import com.example.losgearplus.mode.SwapOdmgKindPayload;
import com.example.losgearplus.mode.ToggleOdmgModePayload;
import com.example.losgearplus.mode.ToggleOneHandPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class OdmgModeClient {
	/** Código GLFW da tecla O. Troque aqui se quiser outra tecla padrão. */
	private static final int DEFAULT_ONE_HAND_KEY = 79;

	private static boolean active;
	private static boolean guns;
	/** [ONE-HAND] estado sincronizado pelo servidor. */
	private static boolean oneHand;

	public static KeyMapping TOGGLE;
	public static KeyMapping SWAP;
	/** [ONE-HAND] tecla que alterna One-Hand <-> Two-Hand. */
	public static KeyMapping TOGGLE_HANDS;

	private OdmgModeClient() {
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean isGuns() {
		return guns;
	}

	/** [ONE-HAND] true se o jogador está em One-Hand. */
	public static boolean isOneHand() {
		return oneHand;
	}

	public static boolean blocksHotbarScroll() {
		Minecraft mc = Minecraft.getInstance();
		return active && GripItems.isAvailable() && mc.player != null && mc.screen == null;
	}

	public static void init() {
		TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.toggle_odmg_mode", InputConstants.Type.KEYSYM, 90, "key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(TOGGLE);
		SWAP = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.swap_odmg_kind", InputConstants.Type.KEYSYM, 80, "key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(SWAP);
		// [ONE-HAND]
		TOGGLE_HANDS = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.toggle_one_hand", InputConstants.Type.KEYSYM, DEFAULT_ONE_HAND_KEY,
				"key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(TOGGLE_HANDS);

		ClientPlayNetworking.registerGlobalReceiver(OdmgModeSyncPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> OdmgModeClient.setActive(payload.active(), payload.guns(), true)));
		// [ONE-HAND]
		ClientPlayNetworking.registerGlobalReceiver(OdmgHandsSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> oneHand = payload.oneHand()));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> OdmgKeyRegistry.logRoles());
		ClientTickEvents.END_CLIENT_TICK.register(OdmgModeClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			OdmgModeClient.setActive(false, false, false);
			oneHand = false;   // [ONE-HAND]
		});
		OdmgHookAngleClient.init();
		OdmgSpeedLevel.init();
		PistolSpearLoadKey.init();
		OdmgRunAnimation.init();
	}

	private static void tick(Minecraft mc) {
		while (TOGGLE.consumeClick()) {
			if (mc.player == null || mc.screen != null || mc.player.isSpectator()) continue;
			ClientPlayNetworking.send(new ToggleOdmgModePayload());
		}
		while (SWAP.consumeClick()) {
			if (mc.player == null || mc.screen != null || mc.player.isSpectator()) continue;
			ClientPlayNetworking.send(new SwapOdmgKindPayload());
		}
		// [ONE-HAND]
		while (TOGGLE_HANDS.consumeClick()) {
			if (mc.player == null || mc.screen != null || mc.player.isSpectator()) continue;
			ClientPlayNetworking.send(new ToggleOneHandPayload());
		}
	}

	static void setActive(boolean value, boolean gunsNow, boolean announce) {
		guns = value && gunsNow;
		if (active == value) {
			return;
		}
		active = value;
		OdmgKeyRegistry.releaseAffected();
		if (announce) {
			Minecraft.getInstance().gui.setOverlayMessage(Component.translatable(
					value ? "los_gear_plus.mode.odmg_on" : "los_gear_plus.mode.odmg_off"), false);
		}
	}
}