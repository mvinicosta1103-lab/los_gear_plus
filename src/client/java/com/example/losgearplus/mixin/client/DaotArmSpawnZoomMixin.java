package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.shiftarm.ShifterArmClient;
import daot.TitanShiftHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Camera do Shifter Arm longe demais. Causa: quando o DAOT recebe o {@code TitanSpawnPayload} (o braço o envia, para
 * ter o mesmo raio/som/rugido da transformação normal), {@code TitanShiftSoundHandler.onTitanSpawn} chama
 * {@code TitanShiftHandler.onTitanSpawned()}, que põe o zoom da 3ª pessoa em 17.5x (~66 blocos atrás). No titã de
 * verdade o zoom é recolocado quando o jogador monta/desmonta; o braço não é montaria, então o zoom ficava preso.
 *
 * Aqui só a chamada do zoom é pulada quando o jogador local está ligando/usando o Shifter Arm: sons, partículas e
 * rugido continuam. A câmera fica na 3ª pessoa padrão do Minecraft, bem perto do shifter.
 *
 * Só nomes do DAOT (remap = false) e sem tipo do Minecraft na assinatura, como os outros mixins que miram o DAOT.
 */
@Mixin(targets = "daot.TitanShiftSoundHandler", remap = false)
public abstract class DaotArmSpawnZoomMixin {

    @Redirect(method = "onTitanSpawn",
            at = @At(value = "INVOKE", target = "Ldaot/TitanShiftHandler;onTitanSpawned()V", remap = false),
            remap = false)
    private static void losgearplus$noArmZoom() {
        if (ShifterArmClient.skipSpawnZoom()) return;
        TitanShiftHandler.onTitanSpawned();
    }
}
