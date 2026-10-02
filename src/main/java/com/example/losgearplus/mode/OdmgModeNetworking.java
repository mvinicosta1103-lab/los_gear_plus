package com.example.losgearplus.mode;

import com.example.losgearplus.grip.GripStorage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registro comum (cliente + servidor) do ODMG Mode. Chame uma vez em LosGearPlus.onInitialize(). */
public final class OdmgModeNetworking {
	private OdmgModeNetworking() {}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(ToggleOdmgModePayload.TYPE, ToggleOdmgModePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OdmgModeSyncPayload.TYPE, OdmgModeSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetHookAnglePayload.TYPE, SetHookAnglePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HookAngleSyncPayload.TYPE, HookAngleSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OdmgChoicePromptPayload.TYPE, OdmgChoicePromptPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(OdmgChoicePayload.TYPE, OdmgChoicePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ToggleOdmgModePayload.TYPE,
				(payload, context) -> OdmgModeServer.toggle(context.player()));

		ServerPlayNetworking.registerGlobalReceiver(OdmgChoicePayload.TYPE,
				(payload, context) -> OdmgModeServer.choose(context.player(), payload.guns()));

		// Angulação dos hooks: só aceita com o ODMG Mode ligado (o valor é limitado a 0..180).
		ServerPlayNetworking.registerGlobalReceiver(SetHookAnglePayload.TYPE, (payload, context) -> {
			if (OdmgModeServer.isActive(context.player())) {
				HookAngles.set(context.player(), payload.angle());
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(OdmgModeServer::tick);

		GripStorage.init();
		// Grips que sobraram nas mãos (queda do servidor, por exemplo) voltam para o storage ao entrar.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GripStorage.stow(handler.getPlayer()));
		// Morrer: os grips (nas mãos ou guardados) viram itens comuns de novo antes de o inventário cair.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
				// Os grips voltam para o inventário (como itens comuns) e caem junto com ele, sem duplicar.
				GripStorage.stow(sp);
				GripStorage.release(sp);
			}
			return true;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			GripStorage.stow(handler.getPlayer()); // sai com os grips guardados, nunca presos nas mãos
			OdmgModeServer.forget(handler.getPlayer());
			HookAngles.forget(handler.getPlayer().getUUID());
		});
		// Ao morrer/respawnar o modo cai (e o cliente é avisado).
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			OdmgModeServer.set(newPlayer, false);
			GripStorage.sync(newPlayer); // o id de entidade muda ao respawnar
			HookAngles.forget(newPlayer.getUUID());
			ServerPlayNetworking.send(newPlayer, new HookAngleSyncPayload(0));
		});
	}
}
