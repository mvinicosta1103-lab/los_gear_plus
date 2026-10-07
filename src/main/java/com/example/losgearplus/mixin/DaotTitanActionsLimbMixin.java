package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbLookup;
import com.example.losgearplus.limb.LimbRules;
import com.example.losgearplus.limb.LimbState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Titã shifter sem braço não soca, não defende e não usa habilidade de braço; ajoelhado também não usa as de perna.
 * Os três handlers são {@code private static} em {@code ModNetworking}; alvo só pelo NOME (todos únicos).
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class DaotTitanActionsLimbMixin {
	/** Soco / ataque do titã. */
	@Inject(method = "handleTitanAttack", at = @At("HEAD"), cancellable = true, remap = false)
	private static void los_gear_plus$noPunch(ServerPlayer player, CallbackInfo ci) {
		LimbState st = LimbLookup.riderState(player);
		if (st != null && !LimbRules.titanArmsOk(st)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.limb.titan_no_arms"), true);
			ci.cancel();
		}
	}

	/** Habilidades (1..n, o significado depende do titã). */
	@Inject(method = "handleTitanAbility", at = @At("HEAD"), cancellable = true, remap = false)
	private static void los_gear_plus$noAbility(ServerPlayer player, int abilityNumber, CallbackInfo ci) {
		LimbState st = LimbLookup.riderState(player);
		if (st == null) return;
		String cls = player.getVehicle().getClass().getSimpleName();
		if (LimbRules.titanAbilityNeedsArms(cls, abilityNumber) && !LimbRules.titanArmsOk(st)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.limb.titan_no_arms"), true);
			ci.cancel();
		} else if (LimbRules.titanAbilityNeedsLegs(cls, abilityNumber) && LimbRules.mustKneel(st)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.limb.titan_no_legs"), true);
			ci.cancel();
		}
	}

	/** Guarda levantada ("armed"): sem braços não sobe (baixar continua liberado). */
	@Inject(method = "handleTitanArm", at = @At("HEAD"), cancellable = true, remap = false)
	private static void los_gear_plus$noGuard(ServerPlayer player, boolean armed, CallbackInfo ci) {
		if (!armed) return;
		LimbState st = LimbLookup.riderState(player);
		if (st != null && !LimbRules.titanArmsOk(st)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.limb.titan_no_arms"), true);
			ci.cancel();
		}
	}
}
