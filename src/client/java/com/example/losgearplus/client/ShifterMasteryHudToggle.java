package com.example.losgearplus.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Key (default K) that cycles the Mastery HUD: full panel -> compact badge -> hidden. */
public final class ShifterMasteryHudToggle {
    public enum Mode { FULL, COMPACT, HIDDEN }

    private static KeyMapping key;
    private static Mode mode = Mode.FULL;

    private ShifterMasteryHudToggle() {}

    public static Mode mode() {
        return mode;
    }

    /** Call once from your ClientModInitializer. */
    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.los_gear_plus.toggle_mastery_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.los_gear_plus"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                mode = switch (mode) {
                    case FULL -> Mode.COMPACT;
                    case COMPACT -> Mode.HIDDEN;
                    case HIDDEN -> Mode.FULL;
                };
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("Mastery HUD: " + mode.name().toLowerCase()), true);
                }
            }
        });
    }

    /** Minimized version: only the level. */
    public static void renderCompact(GuiGraphics g, int level) {
        Minecraft mc = Minecraft.getInstance();
        String text = "Mastery " + level;
        int x = 8, y = 8;
        int w = mc.font.width(text) + 12;
        int h = 16;
        g.fill(x, y, x + w, y + h, 0xCC1B1F2A);
        g.fill(x, y, x + 2, y + h, 0xFFD62839);
        g.drawString(mc.font, text, x + 7, y + 4, 0xFFFFFF, true);
    }
}