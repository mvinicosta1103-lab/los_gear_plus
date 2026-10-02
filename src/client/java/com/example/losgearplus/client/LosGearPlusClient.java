package com.example.losgearplus.client;

import com.example.losgearplus.client.grip.GripHolsterLayer;
import com.example.losgearplus.client.mode.OdmgModeClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

public class LosGearPlusClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Ponto de entrada para lógica só de cliente (renderers, HUD, teclas...).
		OdmgModeClient.init();

		// Grips guardados nas laterais do torso quando o ODMG Mode está desligado.
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new GripHolsterLayer(playerRenderer, context.getItemRenderer()));
			}
		});
	}
}
