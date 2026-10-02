package com.example.losgearplus.client;

import com.example.losgearplus.grip.GripHolsterSyncPayload;
import com.example.losgearplus.client.grip.ClientGripHolsters;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.example.losgearplus.client.grip.GripHolsterCommand;
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
		GripHolsterCommand.init();

		// Estado dos grips guardados (de todos os jogadores visíveis) vem do servidor.
		ClientPlayNetworking.registerGlobalReceiver(GripHolsterSyncPayload.TYPE,
				(payload, context) -> ClientGripHolsters.accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientGripHolsters.clear());

		// Grips guardados nas laterais do torso quando o ODMG Mode está desligado.
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new GripHolsterLayer(playerRenderer, context.getItemRenderer()));
			}
		});
	}
}