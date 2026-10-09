package com.example.losgearplus.client.partial;

import com.example.losgearplus.partial.PartialEntities;
import com.example.losgearplus.partial.PartialShiftPayload;
import com.example.losgearplus.partial.PartialShifterTitanEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Cliente do Partial Shifting: renderer, tecla (padrão K, reconfigurável em Controles) e câmera. */
public final class PartialShiftClient {
    private PartialShiftClient() {}

    private static KeyMapping key;
    private static boolean wasRiding;
    private static CameraType previousCamera;

    public static void init() {
        EntityRendererRegistry.register(PartialEntities.PARTIAL_SHIFTER_TITAN, PartialShifterTitanRenderer::new);

        key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.los_gear_plus.partial_shift",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
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
        });
    }
}
