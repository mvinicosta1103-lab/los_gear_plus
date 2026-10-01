package com.example.losgearplus.client;

import com.example.losgearplus.client.mode.OdmgModeClient;
import net.fabricmc.api.ClientModInitializer;

public class LosGearPlusClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Ponto de entrada para lógica só de cliente (renderers, HUD, teclas...).
		OdmgModeClient.init();
	}
}
