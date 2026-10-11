package com.example.losgearplus.client.shiftarm;

import com.example.losgearplus.shiftarm.ShifterArmActionPayload;
import com.example.losgearplus.shiftarm.ShifterArmEntities;
import com.example.losgearplus.shiftarm.ShifterArmEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

/**
 * Client of the Shifter Arm. The arm is turned on/off in the shifter form menu (H, "Arm").
 * Key (rebindable in Controls): G = grab / release. With the arm out, the attack click = punch (or throw).
 */
public final class ShifterArmClient {
    private ShifterArmClient() {}

    private static KeyMapping grabKey;
    private static boolean wasAttackDown;

    public static void init() {
        EntityRendererRegistry.register(ShifterArmEntities.SHIFTER_ARM, ShifterArmRenderer::new);

        grabKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.los_gear_plus.shifter_arm_grab",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.los_gear_plus"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || !ClientPlayNetworking.canSend(ShifterArmActionPayload.TYPE)) {
                wasAttackDown = false;
                return;
            }
            boolean active = hasOwnArm(mc);
            while (grabKey.consumeClick()) {
                if (active) send(ShifterArmActionPayload.GRAB);
            }
            boolean attackDown = mc.options.keyAttack.isDown() && mc.screen == null;
            if (active && attackDown && !wasAttackDown) send(ShifterArmActionPayload.PRIMARY);
            wasAttackDown = attackDown;
        });
    }

    private static boolean hasOwnArm(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        AABB box = mc.player.getBoundingBox().inflate(4.0);
        return !mc.level.getEntitiesOfClass(ShifterArmEntity.class, box, a -> a.isOwnedBy(mc.player)).isEmpty();
    }

    private static void send(int action) {
        ClientPlayNetworking.send(new ShifterArmActionPayload(action));
    }
}
