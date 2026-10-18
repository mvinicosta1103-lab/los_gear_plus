package com.example.losgearplus.client.shifter;

import com.example.losgearplus.shifter.MasteryConfigPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client side of the Shifter Mastery config menu: opens the screen when the server sends the current values. */
public final class MasteryConfigClient {
	private MasteryConfigClient() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MasteryConfigPayloads.Open.TYPE,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(new MasteryConfigScreen(payload.json()))));
	}
}
