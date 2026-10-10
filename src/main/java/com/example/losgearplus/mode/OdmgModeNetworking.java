package com.example.losgearplus.mode;

import com.example.losgearplus.grip.GripStorage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class OdmgModeNetworking {
	private OdmgModeNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(ToggleOdmgModePayload.TYPE, ToggleOdmgModePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OdmgModeSyncPayload.TYPE, OdmgModeSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetHookAnglePayload.TYPE, SetHookAnglePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HookAngleSyncPayload.TYPE, HookAngleSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OdmgChoicePromptPayload.TYPE, OdmgChoicePromptPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(OdmgChoicePayload.TYPE, OdmgChoicePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SwapOdmgKindPayload.TYPE, SwapOdmgKindPayload.CODEC);
		// [ONE-HAND]
		PayloadTypeRegistry.playC2S().register(ToggleOneHandPayload.TYPE, ToggleOneHandPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OdmgHandsSyncPayload.TYPE, OdmgHandsSyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ToggleOdmgModePayload.TYPE,
				(payload, context) -> OdmgModeServer.toggle(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SwapOdmgKindPayload.TYPE,
				(payload, context) -> OdmgModeServer.swapKind(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(OdmgChoicePayload.TYPE,
				(payload, context) -> OdmgModeServer.choose(context.player(), payload.guns()));
		// [ONE-HAND]
		ServerPlayNetworking.registerGlobalReceiver(ToggleOneHandPayload.TYPE,
				(payload, context) -> OdmgModeServer.toggleOneHand(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SetHookAnglePayload.TYPE, (payload, context) -> {
			if (OdmgModeServer.isActive(context.player())) {
				HookAngles.set(context.player(), payload.angle());
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(OdmgModeServer::tick);
		GripStorage.init();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			GripStorage.stow(handler.getPlayer());
			OdmgModeServer.sendHands(handler.getPlayer());   // [ONE-HAND] cliente começa em Two-Hand
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer sp) {
				GripStorage.stow(sp);
				GripStorage.release(sp);
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			GripStorage.stow(handler.getPlayer());
			OdmgModeServer.forget(handler.getPlayer());
			HookAngles.forget(handler.getPlayer().getUUID());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			OdmgModeServer.set(newPlayer, false);
			GripStorage.sync(newPlayer);
			OdmgModeServer.sendHands(newPlayer);             // [ONE-HAND]
			HookAngles.forget(newPlayer.getUUID());
			ServerPlayNetworking.send(newPlayer, new HookAngleSyncPayload(0));
		});
	}
}