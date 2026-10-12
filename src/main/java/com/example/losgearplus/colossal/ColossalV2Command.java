package com.example.losgearplus.colossal;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Atribui o Colossal V1 ou o Colossal V2 (exclusivos entre si), reaproveitando o comando do DAOT para o poder.
 *
 * <pre>
 *   /lgp shifter set &lt;jogadores&gt; colossal     -> Colossal V1 (remove o V2)
 *   /lgp shifter set &lt;jogadores&gt; colossalv2   -> Colossal V2
 * </pre>
 * O {@code /daot shifter set <jogadores> colossal} dá só o Colossal NORMAL do DAOT: o
 * {@link com.example.losgearplus.mixin.DaotShifterCommandV2Mixin} limpa qualquer V2 que ainda estivesse no jogador.
 */
public final class ColossalV2Command {
	private ColossalV2Command() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("lgp")
						.requires(source -> source.hasPermission(2))
						.then(Commands.literal("shifter")
								.then(Commands.literal("set")
										.then(Commands.argument("targets", EntityArgument.players())
												.then(Commands.literal("colossal")
														.executes(context -> assign(context.getSource(),
																EntityArgument.getPlayers(context, "targets"), false)))
												.then(Commands.literal("colossalv2")
														.executes(context -> assign(context.getSource(),
																EntityArgument.getPlayers(context, "targets"), true))))))));
	}

	private static int assign(CommandSourceStack source, Iterable<ServerPlayer> targets, boolean v2)
			throws CommandSyntaxException {
		int assigned = 0;
		for (ServerPlayer target : targets) {
			// O poder (dados + tag base) continua sendo registrado pelo comando público do DAOT.
			source.getServer().getCommands().getDispatcher().execute(
					"daot shifter set " + target.getScoreboardName() + " colossal", source);
			if (v2) ColossalV2Support.setV2(target);
			else ColossalV2Support.clearV2(target);
			assigned++;
		}
		final int count = assigned;
		final String name = v2 ? "Colossal V2" : "Colossal V1";
		source.sendSuccess(() -> Component.literal(name + " assigned to " + count + " player(s)."), true);
		return assigned;
	}
}