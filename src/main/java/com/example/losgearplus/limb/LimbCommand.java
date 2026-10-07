package com.example.losgearplus.limb;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (permissão 2):
 * <pre>
 *  /limb lose &lt;players&gt; &lt;parte&gt;       ex.: /limb lose @s arm_upper_left
 *  /limb restore &lt;players&gt; [parte]   sem parte = tudo
 *  /limb get [player]
 * </pre>
 * Partes: arm_upper_left/right, arm_lower_left/right, leg_upper_left/right, leg_lower_left/right, eye_left/right.
 */
public final class LimbCommand {
	private LimbCommand() {}

	private static Stream<String> partNames() {
		return java.util.Arrays.stream(LimbPart.VALUES).map(LimbPart::id);
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("limb")
						.requires(src -> src.hasPermission(2))
						.then(Commands.literal("lose")
								.then(Commands.argument("targets", EntityArgument.players())
										.then(Commands.argument("part", StringArgumentType.word())
												.suggests((c, b) -> SharedSuggestionProvider.suggest(partNames(), b))
												.executes(ctx -> lose(ctx, EntityArgument.getPlayers(ctx, "targets"),
														StringArgumentType.getString(ctx, "part"))))))
						.then(Commands.literal("restore")
								.then(Commands.argument("targets", EntityArgument.players())
										.executes(ctx -> restore(ctx, EntityArgument.getPlayers(ctx, "targets"), null))
										.then(Commands.argument("part", StringArgumentType.word())
												.suggests((c, b) -> SharedSuggestionProvider.suggest(partNames(), b))
												.executes(ctx -> restore(ctx, EntityArgument.getPlayers(ctx, "targets"),
														StringArgumentType.getString(ctx, "part"))))))
						.then(Commands.literal("get")
								.executes(ctx -> get(ctx, ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("target", EntityArgument.player())
										.executes(ctx -> get(ctx, EntityArgument.getPlayer(ctx, "target")))))));
	}

	private static int lose(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String id) {
		LimbPart part = LimbPart.byId(id);
		if (part == null) {
			ctx.getSource().sendFailure(Component.literal("Parte desconhecida: " + id));
			return 0;
		}
		int n = 0;
		for (ServerPlayer p : targets) if (LimbManager.lose(p, part, true)) n++;
		int count = n;
		ctx.getSource().sendSuccess(() -> Component.literal(count + " jogador(es) perderam " + id + "."), true);
		return n;
	}

	private static int restore(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String id) {
		LimbPart part = null;
		if (id != null) {
			part = LimbPart.byId(id);
			if (part == null) {
				ctx.getSource().sendFailure(Component.literal("Parte desconhecida: " + id));
				return 0;
			}
		}
		for (ServerPlayer p : targets) LimbManager.restore(p, part);
		ctx.getSource().sendSuccess(() -> Component.literal("Restaurado para " + targets.size() + " jogador(es)."), true);
		return targets.size();
	}

	private static int get(CommandContext<CommandSourceStack> ctx, ServerPlayer p) throws CommandSyntaxException {
		LimbState st = LimbData.of(p);
		StringBuilder sb = new StringBuilder(p.getGameProfile().getName()).append(": ");
		if (st.isPristine()) {
			sb.append("todos os membros inteiros");
		} else {
			boolean first = true;
			for (LimbPart part : LimbPart.VALUES) {
				if (st.isIntact(part)) continue;
				if (!first) sb.append(", ");
				first = false;
				sb.append(part.id()).append('=').append(st.status(part));
				if (st.status(part) == LimbStatus.REGROWING) sb.append(' ').append(Math.round(st.progress(part) * 100)).append('%');
			}
		}
		String msg = sb.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
		return 1;
	}
}
