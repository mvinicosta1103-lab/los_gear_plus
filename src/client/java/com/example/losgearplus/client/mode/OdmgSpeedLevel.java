package com.example.losgearplus.client.mode;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Níveis de velocidade do ODMG (0 a 3). Só vale com o ODMG Mode ligado; fora dele o jogo roda como o nível 1.
 *
 * O DAOT calcula a física no cliente (ODMTickHandler), lendo tudo de getters privados. O DaotOdmSpeedMixin
 * passa o retorno desses getters por aqui. AJUSTE OS NÚMEROS NAS TABELAS ABAIXO para calibrar cada nível.
 *
 *  Nível 0: sem gás e mais lento no ar; no chão os hooks não puxam; com Space (boost) usa pouco gás
 *           e anda na velocidade do ODMG normal (sem o bônus do boost).
 *  Nível 1: o ODMG padrão do DAOT, sem alteração.
 *  Níveis 2 e 3: mais rápido (inclusive o boost) e gastam mais gás.
 */
public final class OdmgSpeedLevel {
    private OdmgSpeedLevel() {}

    public static final int MIN = 0;
    public static final int MAX = 3;
    public static final int DEFAULT = 1;

    /** Multiplica a força de puxada e de órbita (nível 0 = sem boost). A velocidade final é proporcional a isso. */
    private static final double[] SPEED = {0.55, 1.00, 1.25, 1.55};
    /** Multiplica os multiplicadores de boost do DAOT (nível 0 é tratado à parte: boost vira 1.0). */
    private static final double[] BOOST = {1.00, 1.00, 1.15, 1.30};
    /** Multiplica o gás gasto por ciclo (arredonda para cima). Nível 0 é tratado à parte. */
    private static final double[] GAS = {0.00, 1.00, 1.60, 2.80};

    /** Nível 0 com boost: gás por ciclo e quanto o ciclo fica mais lento (1 gás a cada 2x o intervalo normal). */
    private static final int L0_BOOST_GAS = 1;
    private static final int L0_BOOST_INTERVAL_MULT = 2;
    /** Intervalo "infinito" (seguro contra overflow: o DAOT ainda multiplica por 5 ou 6). */
    private static final int NEVER = 1_000_000;

    private static int level = DEFAULT;
    public static KeyMapping SET_LEVEL_KEY;

    public static void init() {
        SET_LEVEL_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.los_gear_plus.set_speed_level", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U,
                "key.categories.los_gear_plus"));
        OdmgKeyRegistry.exempt(SET_LEVEL_KEY);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> level = DEFAULT);
    }

    /** Nível que vale agora. */
    public static int current() {
        return OdmgModeClient.isActive() ? level : DEFAULT;
    }

    /** Segurar a tecla + scroll muda o nível. Retorna true se consumiu o scroll. */
    public static boolean onScroll(double vertical) {
        Minecraft mc = Minecraft.getInstance();
        if (vertical == 0 || mc.player == null || mc.screen != null || !OdmgModeClient.isActive()
                || SET_LEVEL_KEY == null || !SET_LEVEL_KEY.isDown()) {
            return false;
        }
        int next = Math.max(MIN, Math.min(MAX, level + (vertical > 0 ? 1 : -1)));
        if (next != level) {
            level = next;
            com.example.losgearplus.client.ui.OdmgHud.flashSpeed();
        }
        return true;
    }

    // ------------------------------------------------------------------ usado pelo DaotOdmSpeedMixin

    private static boolean boosting() {
        return Minecraft.getInstance().options.keyJump.isDown();
    }

    private static boolean airborne() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && !p.onGround();
    }

    /** Nível 0: gás só existe no ar com boost. */
    private static boolean level0UsesGas() {
        return boosting() && airborne();
    }

    /** Puxada/órbita base (getBasePullSpeed e getBaseOrbitSpeed). */
    public static double speedScale() {
        int l = current();
        if (l == 0) {
            if (!airborne()) return 0.0;          // no chão os hooks não puxam
            return boosting() ? 1.0 : SPEED[0];   // boost no nível 0 = velocidade do ODMG normal
        }
        return SPEED[l];
    }

    /** Multiplicadores de boost do DAOT (boostPull, dualHookBoostPull, boostOrbit). */
    public static double boostMultiplier(double original) {
        int l = current();
        return l == 0 ? 1.0 : original * BOOST[l];
    }

    /** Sustentação para cima (upwardLift): some no nível 0 enquanto está no chão. */
    public static double lift(double original) {
        return current() == 0 && !airborne() ? 0.0 : original;
    }

    public static int gasInterval(int original) {
        if (current() != 0) return original;
        return level0UsesGas() ? original * L0_BOOST_INTERVAL_MULT : NEVER;
    }

    /** Vale para gasConsumptionNormal e gasConsumptionBoost. */
    public static int gasAmount(int original) {
        int l = current();
        if (l == 0) return level0UsesGas() ? L0_BOOST_GAS : 0;
        return (int) Math.ceil(original * GAS[l]);
    }
}