package com.example.losgearplus.mixin;

import com.example.losgearplus.codex.CodexServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Titan Ability Codex: intercepta {@code ModNetworking.handleTitanAbility(player, n)} (o DAOT chama isto quando o
 * jogador aperta a tecla de habilidade n) e troca o que a tecla faz conforme o loadout do jogador.
 *
 * <ul>
 *   <li>slot padrão / titã que o Codex não cobre: não faz nada, o DAOT segue normalmente;</li>
 *   <li>skill ORIGINAL em outro slot: chama o próprio handler do DAOT com o número original (as checagens do
 *       DAOT e as do mod, como a de membros, rodam normalmente nessa segunda chamada);</li>
 *   <li>skill PRÓPRIA: roda o código dela (com recarga, stamina e bloqueio) e cancela a chamada original.</li>
 * </ul>
 *
 * Prioridade 300: roda ANTES do {@link DaotTitanActionsLimbMixin} (padrão 1000), que então confere os braços/pernas
 * com o número que realmente vai ser executado. Alvo só pelo NOME (não há sobrecarga), como nos outros mixins.
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false, priority = 300)
public abstract class DaotTitanAbilityLoadoutMixin {

	/** Evita a recursão quando reencaminhamos para o handler com o número original. */
	@Unique
	private static final ThreadLocal<Boolean> los_gear_plus$BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

	@Shadow(remap = false)
	private static void handleTitanAbility(ServerPlayer player, int abilityNumber) {
		throw new AssertionError("shadow");
	}

	@Inject(method = "handleTitanAbility", at = @At("HEAD"), cancellable = true, remap = false)
	private static void los_gear_plus$codexRoute(ServerPlayer player, int abilityNumber, CallbackInfo ci) {
		if (los_gear_plus$BYPASS.get()) return;
		int route = CodexServer.route(player, abilityNumber);
		if (route == CodexServer.PASS) return;
		ci.cancel();
		if (route > 0) {
			los_gear_plus$BYPASS.set(Boolean.TRUE);
			try {
				handleTitanAbility(player, route);
			} finally {
				los_gear_plus$BYPASS.set(Boolean.FALSE);
			}
		}
	}
}
