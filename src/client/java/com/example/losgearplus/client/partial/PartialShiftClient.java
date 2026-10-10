package com.example.losgearplus.client.partial;

import com.example.losgearplus.client.limb.TitanLimbBones;
import com.example.losgearplus.partial.PartialColossalActionPayload;
import com.example.losgearplus.partial.PartialColossalTitanEntity;
import com.example.losgearplus.partial.PartialEntities;
import com.example.losgearplus.partial.PartialShiftPayload;
import com.example.losgearplus.partial.PartialShifterTitanEntity;
import com.mojang.blaze3d.platform.InputConstants;
import daot.DannysAotClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Cliente do Partial Shifting: renderer, câmera e duas teclas (reconfiguráveis em Controles):
 * Uma só tecla (padrão O): transforma e, com o titã ativo, alterna dentro/emergido pela nuca.
 * Emergido, o sneak sai de vez e o titã evapora (igual ao dismount do DAOT).
 */
public final class PartialShiftClient {
    private PartialShiftClient() {}

    private static KeyMapping key;
    private static boolean wasRiding;
    private static CameraType previousCamera;

    public static void init() {
        EntityRendererRegistry.register(PartialEntities.PARTIAL_SHIFTER_TITAN, PartialShifterTitanRenderer::new);
        EntityRendererRegistry.register(PartialEntities.PARTIAL_COLOSSAL_TITAN, PartialShifterTitanRenderer::new);
        PartialColossalHud.init();

        // Colossal parcial: mesmos ossos de braço do Colossal do DAOT. Sem pernas no modelo: a raiz "no_kneel_root"
        // não existe no geo de propósito (ajoelhar vira no-op), como no rig acima.
        TitanLimbBones.registerRig("partial_titan_colossal", "no_kneel_root", -1f,
                new String[] { "lefthand", "lowerlefthand", "leftwrist" },
                new String[] { "righthand", "lowerrighthand", "rightwrist" },
                new String[] { "leftleg", "lowerleftleg", "leftfoot" },
                new String[] { "rightleg", "lowerrightleg", "rightfoot" });

        // Membros do titã parcial (decepar/regenerar/peça solta): braços com ombro > antebraço > mão. O modelo não
        // tem pernas (os nomes de perna abaixo não existem no geo e são ignorados). O estado de membros é do JOGADOR:
        // se ele perdeu as pernas como humano, o rig tentaria ajoelhar o titã. A raiz abaixo não existe no geo de
        // propósito, o que torna o ajoelhar um no-op (o titã parcial não tem pernas nem anda).
        TitanLimbBones.registerRig("partial_titan_attack", "no_kneel_root", -1f,
                new String[] { "arm_l", "forearm_l", "hand_l" },
                new String[] { "arm_r", "forearm_r", "hand_r" },
                new String[] { "leg_l", "leg2_l", "heel_l" },
                new String[] { "leg_r", "leg2_r", "heel_r" });

        key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.los_gear_plus.partial_shift",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "key.categories.los_gear_plus"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                if (mc.player != null && ClientPlayNetworking.canSend(PartialShiftPayload.TYPE)) {
                    ClientPlayNetworking.send(new PartialShiftPayload());
                }
            }

            // Como o jogador fica invisível dentro do titã, passa para 3ª pessoa ao montar e restaura ao sair.
            boolean riding = mc.player != null && mc.player.getVehicle() instanceof PartialShifterTitanEntity;
            if (riding && !wasRiding) {
                previousCamera = mc.options.getCameraType();
                if (previousCamera.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else if (!riding && wasRiding && previousCamera != null) {
                mc.options.setCameraType(previousCamera);
            }
            wasRiding = riding;

            tickColossalInput(mc);
        });
    }

    private static boolean wasAttackDown;
    private static final boolean[] WAS_ABILITY_DOWN = new boolean[4];

    /**
     * O cliente do DAOT só envia ataque/habilidades quando o jogador monta um titã do próprio DAOT, então aqui o
     * Colossal parcial lê as mesmas teclas (clique de ataque do jogo e as teclas de habilidade 1..4 configuradas
     * no DAOT) e envia a ação ao servidor, que valida tudo.
     */
    private static void tickColossalInput(net.minecraft.client.Minecraft mc) {
        boolean riding = mc.player != null && mc.player.getVehicle() instanceof PartialColossalTitanEntity;
        if (!riding || mc.screen != null || !ClientPlayNetworking.canSend(PartialColossalActionPayload.TYPE)) {
            wasAttackDown = false;
            java.util.Arrays.fill(WAS_ABILITY_DOWN, false);
            return;
        }
        boolean attackDown = mc.options.keyAttack.isDown();
        if (attackDown && !wasAttackDown) {
            ClientPlayNetworking.send(new PartialColossalActionPayload(PartialColossalTitanEntity.ACTION_ATTACK));
        }
        wasAttackDown = attackDown;

        KeyMapping[] abilityKeys = {
                DannysAotClient.SHIFTER_ABILITY_1_KEY, DannysAotClient.SHIFTER_ABILITY_2_KEY,
                DannysAotClient.SHIFTER_ABILITY_3_KEY, DannysAotClient.SHIFTER_ABILITY_4_KEY };
        long window = mc.getWindow().getWindow();
        for (int i = 0; i < abilityKeys.length; i++) {
            KeyMapping km = abilityKeys[i];
            boolean down = false;
            if (km != null) {
                int code = KeyBindingHelper.getBoundKeyOf(km).getValue();
                down = code > 0 && InputConstants.isKeyDown(window, code);
            }
            if (down && !WAS_ABILITY_DOWN[i]) {
                ClientPlayNetworking.send(new PartialColossalActionPayload(i + 1));
            }
            WAS_ABILITY_DOWN[i] = down;
        }
    }
}
