package com.example.losgearplus.client.shifter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Show/hide controls for the Mastery HUD:
 * <ul>
 *   <li>key (default M, rebindable in Controls > LOS Gear Plus): toggles the HUD;</li>
 *   <li>{@code /masteryhud} toggles, {@code /masteryhud on} shows, {@code /masteryhud off} hides.</li>
 * </ul>
 * The key belongs to this mod, so the ODMG Mode never locks it.
 */
public final class ShifterMasteryHudControls {
	private ShifterMasteryHudControls() {}

	public static KeyMapping KEY;

	public static void init() {
		ShifterMasteryHudConfig.load();

		KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.mastery_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,
				"key.categories.los_gear_plus"));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (KEY.consumeClick()) {
				if (mc.player != null && mc.screen == null) set(mc, !ShifterMasteryHudConfig.isVisible());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var root = ClientCommandManager.literal("masteryhud");
			root.executes(ctx -> command(ctx.getSource(), !ShifterMasteryHudConfig.isVisible()));
			root.then(ClientCommandManager.literal("on").executes(ctx -> command(ctx.getSource(), true)));
			root.then(ClientCommandManager.literal("off").executes(ctx -> command(ctx.getSource(), false)));
			root.then(ClientCommandManager.literal("toggle")
					.executes(ctx -> command(ctx.getSource(), !ShifterMasteryHudConfig.isVisible())));
			dispatcher.register(root);
		});
	}

	private static void set(Minecraft mc, boolean visible) {
		ShifterMasteryHudConfig.setVisible(visible);
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.translatable(
					visible ? "los_gear_plus.mastery_hud.on" : "los_gear_plus.mastery_hud.off"), true);
		}
	}

	private static int command(FabricClientCommandSource source, boolean visible) {
		ShifterMasteryHudConfig.setVisible(visible);
		source.sendFeedback(Component.translatable(
				visible ? "los_gear_plus.mastery_hud.on" : "los_gear_plus.mastery_hud.off"));
		return 1;
	}
}
