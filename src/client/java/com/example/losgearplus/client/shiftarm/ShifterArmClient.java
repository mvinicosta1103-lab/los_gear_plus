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
 * Client of the Shifter Arm. The arm is turned on in the shifter form menu (H, "Arm").
 * Keys: G = grab / release; with the arm out, the attack click = punch (or throw).
 * To LEAVE the form (the arm turns black and evaporates): B (the DAOT transform key; the server turns it into "leave
 * the arm", nothing to do here) or L (own key below, rebindable in Controls; polled raw like the DAOT does, and the
 * vanilla key that shares the physical key, Advancements, is muted while the arm is out).
 */
public final class ShifterArmClient {
    private ShifterArmClient() {}

    private static KeyMapping grabKey;
    private static KeyMapping exitKey;
    private static boolean wasAttackDown;
    private static boolean wasExitDown;
    /** The local player has an arm out (not an evaporating one). Refreshed every client tick. */
    private static boolean armActive;
    /** Ticks left of "I just asked for the arm": covers the pre-shift (40 ticks) plus margin, see {@link #skipSpawnZoom}. */
    private static int awaitingSpawnTicks;
    private static final int AWAIT_SPAWN_TICKS = 100;

    public static void init() {
        EntityRendererRegistry.register(ShifterArmEntities.SHIFTER_ARM, ShifterArmRenderer::new);

        grabKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.los_gear_plus.shifter_arm_grab",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.los_gear_plus"));
        exitKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.los_gear_plus.shifter_arm_exit",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.los_gear_plus"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || !ClientPlayNetworking.canSend(ShifterArmActionPayload.TYPE)) {
                wasAttackDown = false;
                wasExitDown = false;
                armActive = false;
                awaitingSpawnTicks = 0;
                return;
            }
            if (awaitingSpawnTicks > 0) awaitingSpawnTicks--;
            boolean active = hasOwnArm(mc);
            armActive = active;
            while (grabKey.consumeClick()) {
                if (active) send(ShifterArmActionPayload.GRAB);
            }
            while (exitKey.consumeClick()) {
                // clicks that reach the mapping are drained here; the raw poll below is what actually leaves
            }
            boolean attackDown = mc.options.keyAttack.isDown() && mc.screen == null;
            if (active && attackDown && !wasAttackDown) send(ShifterArmActionPayload.PRIMARY);
            wasAttackDown = attackDown;

            boolean exitDown = active && mc.screen == null && isExitKeyDown(mc);
            if (exitDown && !wasExitDown) send(ShifterArmActionPayload.EXIT);
            wasExitDown = exitDown;
        });
    }

    /** Raw poll of the key bound to the exit mapping (the vanilla delivers a click to one mapping per physical key only). */
    private static boolean isExitKeyDown(Minecraft mc) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(exitKey);
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        }
        return exitKey.isDown(); // mouse button / scancode binding
    }

    /**
     * Called by {@code OdmgKeyRegistry.isSuppressed}: while the arm is out, any OTHER key (e.g. vanilla Advancements,
     * also on L) bound to the same physical key as the exit key is muted, so pressing it only leaves the arm.
     */
    public static boolean suppressesKey(KeyMapping mapping) {
        if (!armActive || exitKey == null || mapping == exitKey) return false;
        return KeyBindingHelper.getBoundKeyOf(mapping).equals(KeyBindingHelper.getBoundKeyOf(exitKey))
                && !mapping.getName().startsWith("key.los_gear_plus.");
    }

    /** The local player has an arm out and in use (an evaporating one does not count). */
    public static boolean hasOwnArm() {
        return armActive;
    }

    /** Asks the server to turn the arm on (or off, if it is out): used by the shifter form menu. */
    public static void requestToggle() {
        if (!ClientPlayNetworking.canSend(ShifterArmActionPayload.TYPE)) return;
        if (!armActive) awaitingSpawnTicks = AWAIT_SPAWN_TICKS;
        send(ShifterArmActionPayload.TOGGLE);
    }

    /**
     * True when the DAOT's own "titan spawned" camera zoom must NOT run: the arm is not a titan the player rides, so
     * nothing would ever bring the zoom back (it would sit at 17.5x = about 66 blocks behind the shifter). Checked
     * directly (not only {@link #armActive}) because the spawn packet can be handled before the next client tick
     * notices the new arm entity.
     */
    public static boolean skipSpawnZoom() {
        if (armActive || awaitingSpawnTicks > 0) return true;
        return hasOwnArm(Minecraft.getInstance());
    }

    private static boolean hasOwnArm(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        AABB box = mc.player.getBoundingBox().inflate(4.0);
        return !mc.level.getEntitiesOfClass(ShifterArmEntity.class, box,
                a -> a.isOwnedBy(mc.player) && !a.isEvaporating()).isEmpty();
    }

    private static void send(int action) {
        ClientPlayNetworking.send(new ShifterArmActionPayload(action));
    }
}
