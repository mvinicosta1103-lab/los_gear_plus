package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.mode.OdmgSpeedLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Níveis de velocidade do ODMG. O ODMTickHandler do DAOT lê toda a física e o gás de getters privados
 * (que devolvem ODMConfig.get().xxx); aqui escalamos o retorno de cada um conforme o nível.
 *
 * Só nomes do DAOT (remap = false) e sem nenhum tipo do Minecraft na assinatura, então não há problema
 * de mapeamento. Se uma atualização do DAOT renomear um getter, o jogo falha ao abrir (defaultRequire = 1).
 */
@Mixin(targets = "daot.ODMTickHandler", remap = false)
public abstract class DaotOdmSpeedMixin {

    // --- velocidade ---------------------------------------------------------------------------

    @Inject(method = "getBasePullSpeed", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$pull(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValueD() * OdmgSpeedLevel.speedScale());
    }

    @Inject(method = "getBaseOrbitSpeed", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$orbit(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValueD() * OdmgSpeedLevel.speedScale());
    }

    @Inject(method = "getUpwardLift", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$lift(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(OdmgSpeedLevel.lift(cir.getReturnValueD()));
    }

    // --- boost (Space) ------------------------------------------------------------------------

    @Inject(method = "getBoostPullMultiplier", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$boostPull(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(OdmgSpeedLevel.boostMultiplier(cir.getReturnValueD()));
    }

    @Inject(method = "getDualHookBoostPullMultiplier", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$dualBoostPull(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(OdmgSpeedLevel.boostMultiplier(cir.getReturnValueD()));
    }

    @Inject(method = "getBoostOrbitMultiplier", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$boostOrbit(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(OdmgSpeedLevel.boostMultiplier(cir.getReturnValueD()));
    }

    // --- gás ----------------------------------------------------------------------------------

    @Inject(method = "getGasTickInterval", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$gasInterval(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(OdmgSpeedLevel.gasInterval(cir.getReturnValueI()));
    }

    @Inject(method = "getGasConsumptionNormal", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$gasNormal(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(OdmgSpeedLevel.gasAmount(cir.getReturnValueI()));
    }

    @Inject(method = "getGasConsumptionBoost", at = @At("RETURN"), cancellable = true)
    private static void losgearplus$gasBoost(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(OdmgSpeedLevel.gasAmount(cir.getReturnValueI()));
    }
}