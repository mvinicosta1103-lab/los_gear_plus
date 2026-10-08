package com.example.losgearplus.client;

import com.example.losgearplus.grip.GripHolsterSyncPayload;
import com.example.losgearplus.client.grip.ClientGripHolsters;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.example.losgearplus.client.grip.GripHolsterCommand;
import com.example.losgearplus.client.grip.GripHolsterLayer;
import com.example.losgearplus.client.hange.HangeSfxCommand;
import com.example.losgearplus.client.hange.HangeSfxConfig;
import com.example.losgearplus.client.hange.HangeTitanAlert;
import com.example.losgearplus.client.mode.OdmgChoiceScreen;
import com.example.losgearplus.client.mode.OdmgModeClient;
import com.example.losgearplus.client.steam.SteamHealClient;
import com.example.losgearplus.grip.HolsterSlots;
import com.example.losgearplus.mode.OdmgChoicePromptPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

public class LosGearPlusClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Ponto de entrada para lógica só de cliente (renderers, HUD, teclas...).
		OdmgModeClient.init();
		com.example.losgearplus.client.ui.OdmgHud.init();
		SteamHealClient.init();
		com.example.losgearplus.client.shifter.ShifterMasteryHud.init();
		com.example.losgearplus.client.shifter.ShifterMasteryHudControls.init();
		GripHolsterCommand.init();
		com.example.losgearplus.client.limb.LimbClient.init();
		com.example.losgearplus.client.evap.EvaporationClient.init();

		// SFX quando os óculos da Hange (los_gear) avistam titans + comando /hangesfx para ligar/desligar.
		HangeSfxConfig.load();
		HangeTitanAlert.init();
		HangeSfxCommand.init();

		// Os slots de arma ao lado do peitoral somem só com o ODMG Mode ligado COM pistolas nas mãos: o cliente informa o estado.
		HolsterSlots.clientModeActive = player -> OdmgModeClient.isActive();
		HolsterSlots.clientGunsActive = player -> OdmgModeClient.isGuns();

		// Tela de seleção (New ODM Gear com blades ou New ODM Uniform com pistolas).
		ClientPlayNetworking.registerGlobalReceiver(OdmgChoicePromptPayload.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(new OdmgChoiceScreen())));

		// Estado dos grips guardados (de todos os jogadores visíveis) vem do servidor.
		ClientPlayNetworking.registerGlobalReceiver(GripHolsterSyncPayload.TYPE,
				(payload, context) -> ClientGripHolsters.accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientGripHolsters.clear());

		// Grips guardados nas laterais do torso quando o ODMG Mode está desligado.
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new GripHolsterLayer(playerRenderer, context.getItemRenderer()));
				// Braço/perna cortado ou crescendo no corpo humano (o membro vanilla fica escondido).
				helper.register(new com.example.losgearplus.client.limb.HumanLimbLayer(playerRenderer));
			}
		});
	}
}