package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbData;
import com.example.losgearplus.limb.LimbRules;
import com.example.losgearplus.limb.LimbState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Perdeu o braço da mão principal: o braço que sobrou vira o principal por obrigação (nos dois lados, então a
 * mão que segura o item, o desenho em primeira pessoa e as regras de uso ficam coerentes). Quando o braço
 * cresce de volta, a escolha normal do jogador volta sozinha.
 */
@Mixin(Player.class)
public abstract class PlayerMainArmLimbMixin {
	@Inject(method = "getMainArm", at = @At("RETURN"), cancellable = true)
	private void los_gear_plus$forceMainArm(CallbackInfoReturnable<HumanoidArm> cir) {
		LimbState s = LimbData.of((Player) (Object) this);
		if (s.isPristine()) return;
		HumanoidArm forced = LimbRules.forcedMainArm(s);
		if (forced != null) cir.setReturnValue(forced);
	}
}
