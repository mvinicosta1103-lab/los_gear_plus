package com.example.losgearplus.client.hange;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Comando de cliente para ligar/desligar o SFX dos óculos da Hange:
 * /hangesfx (alterna), /hangesfx on, /hangesfx off, /hangesfx toggle, /hangesfx test (toca o som).
 */
public final class HangeSfxCommand {
    private HangeSfxCommand() {}

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("hangesfx");
            root.executes(ctx -> set(ctx.getSource(), !HangeSfxConfig.isEnabled()));
            root.then(ClientCommandManager.literal("on").executes(ctx -> set(ctx.getSource(), true)));
            root.then(ClientCommandManager.literal("off").executes(ctx -> set(ctx.getSource(), false)));
            root.then(ClientCommandManager.literal("toggle")
                    .executes(ctx -> set(ctx.getSource(), !HangeSfxConfig.isEnabled())));
            root.then(ClientCommandManager.literal("test").executes(ctx -> {
                HangeTitanAlert.playTest();
                return 1;
            }));
            dispatcher.register(root);
        });
    }

    private static int set(FabricClientCommandSource source, boolean value) {
        HangeSfxConfig.setEnabled(value);
        source.sendFeedback(Component.translatable(
                value ? "los_gear_plus.hange_sfx.on" : "los_gear_plus.hange_sfx.off"));
        return 1;
    }
}
