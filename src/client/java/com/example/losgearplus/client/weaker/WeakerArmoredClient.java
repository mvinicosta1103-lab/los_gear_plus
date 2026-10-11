package com.example.losgearplus.client.weaker;

import com.example.losgearplus.client.limb.TitanLimbBones;
import com.example.losgearplus.weaker.ShifterForm;
import com.example.losgearplus.weaker.ShifterFormPayload;
import com.example.losgearplus.weaker.ShifterFormSyncPayload;
import com.example.losgearplus.weaker.WeakerEntities;
import com.mojang.blaze3d.platform.InputConstants;
import daot.ArmoredTitanRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

/**
 * Cliente do Armored Weaker: renderer (reaproveita o ArmoredTitanRenderer do DAOT; o geo/textura do Weaker entram
 * pelos mixins DaotWeakModel e DaotWeakGlow), rig de membros (mesmo do Armored), tecla e menu de forma (genérico, ver ShifterFormScreen).
 */
public final class WeakerArmoredClient {
	private WeakerArmoredClient() {}

	private static KeyMapping key;
	/** O menu só abre depois que o servidor responde com o titã atual do portador. */
	private static boolean openOnSync;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void init() {
		EntityRendererProvider provider = ctx -> new ArmoredTitanRenderer(ctx);
		EntityRendererRegistry.register((EntityType) WeakerEntities.WEAK_ARMORED_TITAN, provider);

		// Mesmos ossos do Armored do DAOT -> usa o rig de membros dele.
		TitanLimbBones.registerAlias("weak_armored_titan", "armoredtitan");

		key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.shifter_form",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				"key.categories.los_gear_plus"));

		ClientPlayNetworking.registerGlobalReceiver(ShifterFormSyncPayload.TYPE, (payload, context) -> {
			if (!openOnSync) return;
			openOnSync = false;
			var mc = context.client();
			if (mc.player == null || mc.screen != null) return;
			if (payload.titanId().isEmpty()) {
				mc.player.displayClientMessage(Component.translatable("los_gear_plus.shifter.not_shifter"), true);
				return;
			}
			mc.setScreen(new ShifterFormScreen(payload.titanId(), payload.canWeaker(), ShifterForm.byId(payload.form())));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> openOnSync = false);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.player != null && mc.screen == null && ClientPlayNetworking.canSend(ShifterFormPayload.TYPE)) {
					openOnSync = true;
					ClientPlayNetworking.send(new ShifterFormPayload(-1)); // pede o titã atual do portador
				}
			}
		});
	}
}
