package com.example.losgearplus.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key (default J) that toggles Force Shifting by running /forceshift toggle. Rebindable in Controls. */
public final class ForceShiftKey {
    private static KeyMapping key;

    private ForceShiftKey() {}

    /** Call once from your ClientModInitializer. */
    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.los_gear_plus.toggle_force_shift",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "key.categories.los_gear_plus"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                if (mc.player != null) mc.player.connection.sendCommand("forceshift toggle");
            }
        });
    }
}