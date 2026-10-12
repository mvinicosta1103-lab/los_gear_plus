package com.example.losgearplus.mixin;

import com.example.losgearplus.colossal.ColossalV2Support;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mantém o Colossal V1 e o Colossal V2 individuais quando o poder é mexido pelo comando do próprio DAOT.
 *
 * <p>{@code /daot shifter set <alvos> colossal} dá o Colossal NORMAL do DAOT, mas não conhece a tag
 * {@code colossalv2}: ela ficava pendurada e o jogador continuava com o visual V2 (modelos "fundidos").
 * Aqui, depois que o comando do DAOT termina COM SUCESSO (ele retorna 0 em toda falha e a quantidade de
 * jogadores no sucesso; conferido em {@code daot.ModCommands} 2.5.1.1), o V2 é limpo. O V2 só é dado pelo
 * {@code /lgp shifter set <alvos> colossalv2}.
 *
 * <p>Alvo só pelo NOME ({@code setShifter} e {@code removeShifter} não têm sobrecarga). {@code require = 0}:
 * se uma versão do DAOT não tiver esses métodos, o mod simplesmente não limpa o V2 (não derruba o jogo).
 */
@Mixin(targets = "daot.ModCommands", remap = false)
public abstract class DaotShifterCommandV2Mixin {

	@Unique
	private static List<ServerPlayer> losgearplus$targets = List.of();

	@Unique
	private static String losgearplus$type = "";

	/** Guarda os alvos e o tipo antes do DAOT rodar (mesmos argumentos que ele lê: "targets" e "type"). */
	@Inject(method = {"setShifter", "removeShifter"}, at = @At("HEAD"), remap = false, require = 0)
	private static void losgearplus$captureArgs(CommandContext<CommandSourceStack> context,
			CallbackInfoReturnable<Integer> cir) {
		losgearplus$targets = List.of();
		losgearplus$type = "";
		try {
			losgearplus$targets = new ArrayList<>(EntityArgument.getPlayers(context, "targets"));
			losgearplus$type = StringArgumentType.getString(context, "type").toLowerCase(Locale.ROOT);
		} catch (CommandSyntaxException | IllegalArgumentException ignored) {
			// argumentos que o DAOT também vai recusar: nada a fazer
		}
	}

	@Inject(method = "setShifter", at = @At("RETURN"), remap = false, require = 0)
	private static void losgearplus$afterSet(CommandContext<CommandSourceStack> context,
			CallbackInfoReturnable<Integer> cir) {
		if (cir.getReturnValue() != null && cir.getReturnValue() > 0) {
			ColossalV2Support.onDaotShifterCommand(losgearplus$targets, losgearplus$type);
		}
		losgearplus$targets = List.of();
	}

	@Inject(method = "removeShifter", at = @At("RETURN"), remap = false, require = 0)
	private static void losgearplus$afterRemove(CommandContext<CommandSourceStack> context,
			CallbackInfoReturnable<Integer> cir) {
		if (cir.getReturnValue() != null && cir.getReturnValue() > 0) {
			ColossalV2Support.onDaotShifterCommand(losgearplus$targets, losgearplus$type);
		}
		losgearplus$targets = List.of();
	}
}
