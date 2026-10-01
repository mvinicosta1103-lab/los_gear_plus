package com.example.losgearplus.client.mode;

import com.example.losgearplus.mode.HookAngleSyncPayload;
import com.example.losgearplus.mode.HookAngles;
import com.example.losgearplus.mode.SetHookAnglePayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Angulação dos hooks (cliente): segurar a tecla + scroll muda a abertura de 0 a 180 graus,
 * e o HUD mostra um transferidor no centro da tela, como no WoF. Só funciona com o ODMG Mode ligado.
 */
public final class OdmgHookAngleClient {
	private OdmgHookAngleClient() {}

	/** Graus por "clique" do scroll. */
	private static final int SCROLL_STEP = 10;

	private static final int RADIUS = 18;
	private static final int ARC_COLOR = 0xB0FFFFFF;
	private static final int LINE_COLOR = 0xFFFF2A2A;

	private static int angle;
	public static KeyMapping SET_ANGLE_KEY;

	/** Chamado por OdmgModeClient.init(). */
	public static void init() {
		SET_ANGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.los_gear_plus.set_hook_angle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X,
				"key.categories.los_gear_plus"));
		OdmgKeyRegistry.exempt(SET_ANGLE_KEY);

		HookAngles.clientSpread = OdmgHookAngleClient::effectiveAngle;

		ClientPlayNetworking.registerGlobalReceiver(HookAngleSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> angle = HookAngles.clamp(payload.angle())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> angle = 0);
		HudRenderCallback.EVENT.register(OdmgHookAngleClient::renderHud);
	}

	private static int effectiveAngle() {
		return OdmgModeClient.isActive() ? angle : 0;
	}

	private static boolean adjusting(Minecraft mc) {
		return mc.player != null && mc.screen == null && OdmgModeClient.isActive()
				&& SET_ANGLE_KEY != null && SET_ANGLE_KEY.isDown();
	}

	/** Chamado pelo MouseHandlerMixin. Retorna true se consumiu o scroll (a hotbar não deve rolar). */
	public static boolean onScroll(double vertical) {
		Minecraft mc = Minecraft.getInstance();
		if (vertical == 0 || !adjusting(mc)) return false;
		int next = HookAngles.clamp(angle + (vertical > 0 ? SCROLL_STEP : -SCROLL_STEP));
		if (next != angle) {
			angle = next;
			ClientPlayNetworking.send(new SetHookAnglePayload(next));
		}
		return true;
	}

	private static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || !adjusting(mc)) return;

		int cx = graphics.guiWidth() / 2;
		int baseY = graphics.guiHeight() / 2 - 10;

		// Arco do transferidor (0 a 180), com marcas maiores a cada 30 graus.
		for (int deg = 0; deg <= 180; deg += 6) {
			double rad = Math.toRadians(deg);
			int x = cx + (int) Math.round(Math.cos(rad) * RADIUS);
			int y = baseY - (int) Math.round(Math.sin(rad) * RADIUS);
			int size = deg % 30 == 0 ? 3 : 1;
			int off = size / 2;
			graphics.fill(x - off, y - off, x - off + size, y - off + size, ARC_COLOR);
		}

		// Duas linhas vermelhas: cada uma a metade da abertura, para cada lado da vertical.
		float half = angle / 2f;
		drawLine(graphics, cx, baseY, half, RADIUS - 1);
		drawLine(graphics, cx, baseY, -half, RADIUS - 1);

		graphics.drawCenteredString(mc.font, angle + "\u00b0", cx, baseY - RADIUS - 14, 0xFFFFFF);
	}

	/** Linha de 2 px saindo de (x, y) para cima, girada {@code degrees} em relação à vertical. */
	private static void drawLine(GuiGraphics graphics, int x, int y, float degrees, int length) {
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(degrees));
		graphics.fill(-1, -length, 1, 0, LINE_COLOR);
		pose.popPose();
	}
}
