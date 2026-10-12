package com.example.losgearplus.colossal;

import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Assigns the V2 appearance while letting DAOT handle its normal Colossal power registration. */
public final class ColossalV2Command {
	private ColossalV2Command() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("lgp")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("shifter")
					.then(Commands.literal("set")
						.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.literal("colossalv2")
								.executes(context -> setV2(context.getSource(), EntityArgument.getPlayers(context, "targets")))))))));
	}

	private static int setV2(net.minecraft.commands.CommandSourceStack source, Iterable<ServerPlayer> targets)
			throws CommandSyntaxException {
		int assigned = 0;
		for (ServerPlayer target : targets) {
			// Reuse DAOT's public command so its power data and normal Colossal tag stay consistent.
			source.getServer().getCommands().getDispatcher().execute(
				"daot shifter set " + target.getScoreboardName() + " colossal", source);
			target.addTag(ColossalV2Support.V2_TAG);
			ColossalV2Support.ensureDaotColossalTag(target);
			assigned++;
		}
		final int count = assigned;
		source.sendSuccess(() -> Component.literal("Colossal V2 assigned to " + count + " player(s)."), true);
		return assigned;
	}
}