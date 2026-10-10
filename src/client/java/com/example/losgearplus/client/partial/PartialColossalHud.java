package com.example.losgearplus.client.partial;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.partial.PartialColossalTitanEntity;
import com.example.losgearplus.partial.PartialShiftConfig;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Barra de habilidades do Colossal no lugar da hotbar enquanto o jogador está dentro do Colossal parcial.
 *
 * <p>Reaproveita o desenho do próprio DAOT ({@code ShifterAbilityHUD.renderAbilityBar}, tipo COLOSSAL: vapor, chute,
 * arrastar braço, calor infernal), para ficar idêntico ao do Colossal normal (mesmos ícones, teclas e cooldown).
 * O método é privado no DAOT, por isso é chamado por reflexão; se a assinatura mudar numa versão futura do DAOT
 * a barra simplesmente não aparece (um aviso no log) em vez de derrubar o jogo.
 * Chute (slot 2) e arrastar braço (slot 3) aparecem apagados: o parcial não tem pernas nem a animação.
 */
public final class PartialColossalHud {
    private PartialColossalHud() {}

    private static boolean resolved;
    private static Method renderBar;
    private static Object colossalType;

    public static void init() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
    }

    private static void resolve() {
        resolved = true;
        try {
            Class<?> hud = Class.forName("daot.ShifterAbilityHUD");
            for (Method m : hud.getDeclaredMethods()) {
                if (m.getName().equals("renderAbilityBar") && m.getParameterCount() == 8) {
                    m.setAccessible(true);
                    renderBar = m;
                    break;
                }
            }
            Class<?> type = Class.forName("daot.ShifterAbilityHUD$TitanType");
            for (Object constant : type.getEnumConstants()) {
                if ("COLOSSAL".equals(constant.toString())) colossalType = constant;
            }
        } catch (Throwable t) {
            LosGearPlus.LOGGER.warn("Barra de habilidades do Colossal parcial indisponível: {}", t.toString());
        }
        if (renderBar == null || colossalType == null) {
            renderBar = null;
            LosGearPlus.LOGGER.warn("ShifterAbilityHUD.renderAbilityBar não encontrado (versão do DAOT diferente?)");
        }
    }

    /** O jogador está dentro do Colossal parcial (não emergido): é quando a barra substitui a hotbar. */
    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
                && mc.player.getVehicle() instanceof PartialColossalTitanEntity titan
                && !titan.isDismounting();
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !isActive()) return;
        if (!resolved) resolve();
        if (renderBar == null) return;

        PartialColossalTitanEntity titan = (PartialColossalTitanEntity) mc.player.getVehicle();
        int[] cdRemaining = new int[9];
        int[] cdTotal = new int[9];
        cdRemaining[0] = titan.steamCooldownTicks();
        cdTotal[0] = PartialShiftConfig.COLOSSAL_STEAM_COOLDOWN;
        cdRemaining[3] = titan.infernalCooldownTicks();
        cdTotal[3] = PartialShiftConfig.COLOSSAL_INFERNAL_COOLDOWN;
        boolean[] unavailable = new boolean[9];
        unavailable[1] = true; // chute: sem pernas
        unavailable[2] = true; // arrastar braço: sem animação

        try {
            renderBar.invoke(null, graphics, titan.activeAbilitySlot(), -1, colossalType,
                    cdRemaining, cdTotal, null, unavailable);
        } catch (Throwable t) {
            LosGearPlus.LOGGER.warn("Falha ao desenhar a barra de habilidades do Colossal parcial: {}", t.toString());
            renderBar = null; // não repete o erro todo frame
        }
    }
}
