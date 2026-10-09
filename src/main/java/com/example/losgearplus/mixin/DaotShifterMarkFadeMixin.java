package com.example.losgearplus.mixin;

import daot.ShifterMarkTracker.MarkState;
import com.example.losgearplus.partial.PartialShiftManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faz as marcas de shifter (ShifterMarkTracker, DAOT 2.5.0) sumirem devagar em vez de ficarem para sempre.
 *
 * No DAOT a marca só começa a esmaecer (1200 ticks parada + 600 ticks de fade) se as DUAS condições valerem:
 *   1) o jogador foi registrado como "desmontado por completo" (dismountedAtGameTime != -1), e
 *   2) o jogador NÃO tem a tag {@code titan_stealth}.
 * Com o stealth ligado o DAOT zera o início do fade a cada tick e o cliente volta o alpha para 1.0 na hora.
 * Se a (1) nunca acontece (o titã some/morre/é removido por um caminho que não passa pelo removePassenger com o
 * shifterUUID certo), o estado também fica preso. Este mixin corrige as duas coisas dentro de {@code tickServer}:
 *
 *  - antes do tick: se o jogador está online, não está montado em nada há 40 ticks seguidos e o estado ainda
 *    está como "montado", marca como desmontado agora (o contador de 1200 ticks passa a valer);
 *  - durante o tick: a checagem da tag {@code titan_stealth} (único {@code Set.contains} do método) passa a dar
 *    sempre false, então o stealth não congela nem reinicia mais a marca.
 *
 * Não precisa de broadcast extra: o cliente só usa {@code fadeStartedAtGameTime}, que o próprio DAOT envia
 * quando o fade começa. Código do servidor, numa thread só (os campos estáticos abaixo são seguros).
 *
 * Obs.: sem inicializadores estáticos de propósito (mixins não os suportam bem); o mapa é criado sob demanda.
 */
@Mixin(targets = "daot.ShifterMarkTracker", remap = false)
public abstract class DaotShifterMarkFadeMixin {

    @Shadow
    @Final
    private static Map<UUID, MarkState> serverStates;

    /** Ticks seguidos em que o dono de uma marca "ativa" não está montado em nada. Criado sob demanda. */
    @Unique
    private static Map<UUID, Integer> losgearplus$notRidingTicks;

    @Inject(method = "tickServer", at = @At("HEAD"), remap = false)
    private static void losgearplus$releaseStuckMarks(MinecraftServer server, CallbackInfo ci) {
        if (losgearplus$notRidingTicks == null) {
            losgearplus$notRidingTicks = new HashMap<>();
        }
        if (serverStates.isEmpty()) {
            losgearplus$notRidingTicks.clear();
            return;
        }
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, MarkState> entry : serverStates.entrySet()) {
            UUID id = entry.getKey();
            MarkState state = entry.getValue();
            if (state.dismountedAtGameTime() != -1L) {
                losgearplus$notRidingTicks.remove(id);
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            // Fora do titã parcial (saída parcial) o shifter continua "transformado": a marca não pode esmaecer.
            if (player == null || player.getVehicle() != null || PartialShiftManager.hasPartial(id)) {
                losgearplus$notRidingTicks.remove(id);
                continue;
            }
            int ticks = losgearplus$notRidingTicks.merge(id, 1, Integer::sum);
            if (ticks >= 40) {
                losgearplus$notRidingTicks.remove(id);
                entry.setValue(new MarkState(state.markType(), now, -1L));
            }
        }
        // Limpa contadores de quem não tem mais marca.
        Iterator<UUID> it = losgearplus$notRidingTicks.keySet().iterator();
        while (it.hasNext()) {
            if (!serverStates.containsKey(it.next())) it.remove();
        }
    }

    /** O único Set.contains de tickServer é a checagem de "titan_stealth": ignora-a para o fade nunca congelar. */
    @Redirect(
            method = "tickServer",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z", remap = false),
            remap = false)
    private static boolean losgearplus$stealthDoesNotFreezeMarks(Set<String> tags, Object tag) {
        return false;
    }
}