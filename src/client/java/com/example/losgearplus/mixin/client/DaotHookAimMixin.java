package com.example.losgearplus.mixin.client;

import com.example.losgearplus.mode.HookAngles;
import com.example.losgearplus.mode.HookSide;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Liga a angulação dos hooks ao disparo real do DAOT.
 *
 * Como o DAOT funciona (jar 2.4.3, decompilado): o disparo é 100% no CLIENTE. No tick, para cada hook ele
 * chama {@code ODMTickHandler.getPlayerLookTarget(player)} (raycast a partir da mira) e usa o ponto
 * atingido como destino; o servidor só recebe as posições prontas (ODMHookUpdatePayload). Por isso não
 * há nada a fazer no servidor: basta mudar para onde o raycast de cada hook aponta.
 *
 * As duas chamadas ficam em {@code lambda$register$9} (a do hook esquerdo primeiro, a do direito depois),
 * sem receber o lado. Então redirecionamos cada uma (ordinal 0 = esquerdo, 1 = direito) e giramos o yaw do
 * jogador só durante o raycast. Com 0 graus (ou fora do ODMG Mode) o raycast original roda intacto.
 *
 * Os "target" abaixo não levam descritor de propósito: o jar do DAOT usa nomes intermediary e o nosso
 * código usa nomes Mojang; sem descritor não há nome do Minecraft na string. Se uma atualização do DAOT
 * renomear o lambda, o jogo falha ao carregar (defaultRequire = 1) em vez de ficar quebrado em silêncio.
 */
@Mixin(targets = "daot.ODMTickHandler", remap = false)
public abstract class DaotHookAimMixin {

    @Shadow
    private static HitResult getPlayerLookTarget(LocalPlayer player) {
        throw new AssertionError();
    }

    @Redirect(method = "lambda$register$9",
            at = @At(value = "INVOKE", target = "Ldaot/ODMTickHandler;getPlayerLookTarget", ordinal = 0),
            require = 1)
    private static HitResult losgearplus$leftHookTarget(LocalPlayer player) {
        return losgearplus$aim(player, HookSide.LEFT);
    }

    @Redirect(method = "lambda$register$9",
            at = @At(value = "INVOKE", target = "Ldaot/ODMTickHandler;getPlayerLookTarget", ordinal = 1),
            require = 1)
    private static HitResult losgearplus$rightHookTarget(LocalPlayer player) {
        return losgearplus$aim(player, HookSide.RIGHT);
    }

    private static HitResult losgearplus$aim(LocalPlayer player, HookSide side) {
        int spread = HookAngles.spread(player); // 0 se o ODMG Mode estiver desligado
        if (spread <= 0) {
            return getPlayerLookTarget(player); // 0 grau: sai exatamente na direção da mira
        }
        // Yaw cresce virando para a DIREITA: o hook direito soma metade da abertura, o esquerdo subtrai.
        float offset = (side == HookSide.RIGHT ? 1f : -1f) * (spread / 2f);
        float yRot = player.getYRot();
        float headRot = player.getYHeadRot();
        player.setYRot(yRot + offset);
        player.setYHeadRot(headRot + offset);
        try {
            return getPlayerLookTarget(player);
        } finally {
            player.setYRot(yRot);
            player.setYHeadRot(headRot);
        }
    }
}