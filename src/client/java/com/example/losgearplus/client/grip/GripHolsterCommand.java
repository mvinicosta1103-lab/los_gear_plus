package com.example.losgearplus.client.grip;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** Comando de cliente para calibrar a posição dos grips guardados ao vivo. Veja {@link GripHolsterLayer}. */
public final class GripHolsterCommand {
    private GripHolsterCommand() {}

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("gripholster");
            for (int i = 0; i < GripHolsterLayer.NAMES.length; i++) {
                final int index = i;
                final String name = GripHolsterLayer.NAMES[i];
                root.then(ClientCommandManager.literal(name)
                        .then(ClientCommandManager.argument("valor", FloatArgumentType.floatArg())
                                .executes(ctx -> {
                                    float v = FloatArgumentType.getFloat(ctx, "valor");
                                    GripHolsterLayer.VALUES[index] = v;
                                    ctx.getSource().sendFeedback(Component.literal(name + " = " + v));
                                    return 1;
                                })));
            }
            root.then(ClientCommandManager.literal("show").executes(ctx -> {
                StringBuilder sb = new StringBuilder("DEFAULTS = {");
                for (int i = 0; i < GripHolsterLayer.VALUES.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(GripHolsterLayer.VALUES[i]).append('f');
                }
                sb.append("}  // ").append(String.join(", ", GripHolsterLayer.NAMES));
                ctx.getSource().sendFeedback(Component.literal(sb.toString()));
                return 1;
            }));
            root.then(ClientCommandManager.literal("reset").executes(ctx -> {
                System.arraycopy(GripHolsterLayer.DEFAULTS, 0, GripHolsterLayer.VALUES, 0, GripHolsterLayer.VALUES.length);
                ctx.getSource().sendFeedback(Component.literal("gripholster: valores resetados"));
                return 1;
            }));
            dispatcher.register(root);
        });
    }
}