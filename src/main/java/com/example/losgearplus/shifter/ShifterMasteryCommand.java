package com.example.losgearplus.shifter;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands (operator permission, level 2):
 * <pre>
 *  /shiftermastery set &lt;players&gt; &lt;0-9&gt;     set the level (XP is set to the start of that level)
 *  /shiftermastery max &lt;players&gt;            set the maximum level (9)
 *  /shiftermastery reset &lt;players&gt;          back to level 0, no XP, cooldown cleared
 *  /shiftermastery cooldown &lt;players&gt;       reset only the transformation cooldown (alias: resetcooldown)
 *  /shiftermastery addxp &lt;players&gt; &lt;amount&gt;  grant mastery XP
 *  /shiftermastery get [player]             show level, XP, transformations and costs
 *  /sm ...                                  short alias for /shiftermastery
 * </pre>
 * The root is {@code shiftermastery} (not {@code shifter}) so it never collides with DAOT commands.
 */
public final class ShifterMasteryCommand {
	private ShifterMasteryCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("shiftermastery")
					.requires(src -> src.hasPermission(2))
					.then(Commands.literal("set")
							.then(Commands.argument("targets", EntityArgument.players())
									.then(Commands.argument("level", IntegerArgumentType.integer(0, ShifterMastery.MAX_LEVEL))
											.executes(ctx -> set(ctx,
													EntityArgument.getPlayers(ctx, "targets"),
													IntegerArgumentType.getInteger(ctx, "level"))))))
					.then(Commands.literal("max")
							.then(Commands.argument("targets", EntityArgument.players())
									.executes(ctx -> set(ctx, EntityArgument.getPlayers(ctx, "targets"), ShifterMastery.MAX_LEVEL))))
					.then(Commands.literal("reset")
							.then(Commands.argument("targets", EntityArgument.players())
									.executes(ctx -> reset(ctx, EntityArgument.getPlayers(ctx, "targets")))))
					.then(cooldown("cooldown"))
					.then(cooldown("resetcooldown"))
					.then(Commands.literal("addxp")
							.then(Commands.argument("targets", EntityArgument.players())
									.then(Commands.argument("amount", IntegerArgumentType.integer(1))
											.executes(ctx -> addXp(ctx,
													EntityArgument.getPlayers(ctx, "targets"),
													IntegerArgumentType.getInteger(ctx, "amount"))))))
					.then(Commands.literal("get")
							.executes(ctx -> get(ctx, ctx.getSource().getPlayerOrException()))
							.then(Commands.argument("target", EntityArgument.player())
									.executes(ctx -> get(ctx, EntityArgument.getPlayer(ctx, "target")))))));

			// Short alias: /sm <anything /shiftermastery accepts>
			dispatcher.register(Commands.literal("sm")
					.requires(src -> src.hasPermission(2))
					.redirect(root));
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> cooldown(String name) {
		return Commands.literal(name)
				.then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> cooldownReset(ctx, EntityArgument.getPlayers(ctx, "targets"))));
	}

	private static int set(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, int level) {
		for (ServerPlayer p : targets) {
			ShifterMastery.setLevel(p.getUUID(), level);
			p.sendSystemMessage(Component.literal("Your Shifter Mastery is now level " + level + "."));
			ShifterMasterySync.send(p, true);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Set Shifter Mastery to level " + level + " for " + targets.size() + " player(s)."), true);
		return targets.size();
	}

	private static int reset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			ShifterMastery.reset(p.getUUID());
			p.sendSystemMessage(Component.literal("Your Shifter Mastery was reset (level 0)."));
			ShifterMasterySync.send(p, true);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Reset Shifter Mastery for " + targets.size() + " player(s)."), true);
		return targets.size();
	}

	private static int cooldownReset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			ShifterMastery.resetCooldown(p.getUUID());
			p.sendSystemMessage(Component.literal("Your transformation cooldown was reset."));
			ShifterMasterySync.send(p, true);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Reset the transformation cooldown for " + targets.size() + " player(s)."), true);
		return targets.size();
	}

	private static int addXp(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, int amount) {
		for (ServerPlayer p : targets) {
			ShifterMastery.addXp(p, amount);
			ShifterMasterySync.send(p, true);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Granted " + amount + " mastery XP to " + targets.size() + " player(s)."), true);
		return targets.size();
	}

	private static int get(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		int level = ShifterMastery.getLevel(p.getUUID());
		int max = ShifterMastery.maxTransforms(level);
		String limit = max == ShifterMastery.UNLIMITED ? "unlimited" : String.valueOf(max);
		String used = max == ShifterMastery.UNLIMITED ? "-" : String.valueOf(ShifterMastery.usedTransforms(p.getUUID()));
		int need = ShifterMastery.xpForNextLevel(level);
		String xp = need < 0 ? "MAX" : ShifterMastery.xpIntoLevel(p.getUUID()) + "/" + need;
		int cost = Math.round(ShifterMastery.shiftCostMultiplier(level) * 100f);
		int drain = Math.round(ShifterMastery.drainMultiplier(level) * 100f);
		ctx.getSource().sendSuccess(() -> Component.literal(
				p.getGameProfile().getName() + ": level " + level + "/" + ShifterMastery.MAX_LEVEL
						+ " | XP " + xp
						+ " | transformations " + used + "/" + limit
						+ " | transform cost " + cost + "% | titan-form drain " + drain + "%"), false);
		return level;
	}
}
