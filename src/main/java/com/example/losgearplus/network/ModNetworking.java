package com.example.losgearplus.network;

import com.example.losgearplus.gear.GearContainer;
import com.example.losgearplus.gear.GearMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class ModNetworking {
    private ModNetworking() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(OpenGearPayload.TYPE, OpenGearPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenGearPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!player.isAlive() || player.isSpectator()) {
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new GearMenu(id, inventory, new GearContainer(p)),
                    Component.translatable("container.los_gear_plus.gear")));
        });
    }
}