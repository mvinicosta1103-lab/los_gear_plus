package com.example.losgearplus.client.mode;

import com.example.losgearplus.mode.HookAngleSyncPayload;
import com.example.losgearplus.mode.HookAngles;
import com.example.losgearplus.mode.SetHookAnglePayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.example.losgearplus.client.ui.Anim;
import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
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

	private static final int RADIUS = 26;
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

	private static float vis;
	private static float shown;
	private static long lastMs;

	private static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - lastMs) / 1000f);
		lastMs = now;

		boolean show = !mc.options.hideGui && adjusting(mc);
		vis = Anim.smooth(vis, show ? 1f : 0f, 12f, dt);
		shown = Anim.smooth(shown, angle, 16f, dt);
		if (vis < 0.02f) return;

		int cx = graphics.guiWidth() / 2;
		int baseY = graphics.guiHeight() / 2 - 8 + Math.round((1f - vis) * 6f);
		float half = Math.min(90f, shown / 2f);

		// Setor preenchido entre as duas linhas (varredura linha a linha).
		if (half > 0.5f) {
			double tan = half >= 89.5f ? Double.MAX_VALUE : Math.tan(Math.toRadians(half));
			int fillColor = UiDraw.alpha(Palette.CRIMSON, 0.30f * vis);
			for (int r = 1; r <= RADIUS; r++) {
				int ext = (int) Math.min(Math.sqrt((double) RADIUS * RADIUS - (double) r * r), r * tan);
				if (ext > 0) graphics.fill(cx - ext, baseY - r, cx + ext + 1, baseY - r + 1, fillColor);
			}
		}

		// Arco (0 a 180): marcas dentro da abertura acendem em carmesim.
		for (int deg = 0; deg <= 180; deg += 6) {
			double rad = Math.toRadians(deg);
			int x = cx + (int) Math.round(Math.cos(rad) * RADIUS);
			int y = baseY - (int) Math.round(Math.sin(rad) * RADIUS);
			int size = deg % 30 == 0 ? 3 : 1;
			int off = size / 2;
			boolean inside = Math.abs(deg - 90) <= half + 0.5f;
			int c = UiDraw.alpha(inside ? 0xFFFF6A6A : ARC_COLOR, vis);
			graphics.fill(x - off, y - off, x - off + size, y - off + size, c);
		}

		// Linhas: halo translúcido + núcleo vermelho.
		for (int side = -1; side <= 1; side += 2) {
			drawLine(graphics, cx, baseY, side * half, RADIUS - 1, 2, UiDraw.alpha(LINE_COLOR, 0.25f * vis));
			drawLine(graphics, cx, baseY, side * half, RADIUS - 1, 1, UiDraw.alpha(LINE_COLOR, vis));
		}
		graphics.fill(cx - 2, baseY - 2, cx + 3, baseY + 3, UiDraw.alpha(0xFFFFFFFF, vis));

		// Número grande, pulsa de leve.
		float pulse = 1.35f + 0.05f * (float) Math.sin(now / 220.0);
		UiDraw.centeredScaled(graphics, mc.font, Math.round(shown) + "\u00b0", cx, baseY - RADIUS - 18, pulse,
				UiDraw.alpha(UiDraw.mix(Palette.TEXT, Palette.GOLD, shown / 180f), vis));
	}

	/** Linha saindo de (x, y) para cima, girada {@code degrees} em relação à vertical; largura = 2*halfWidth. */
	private static void drawLine(GuiGraphics graphics, int x, int y, float degrees, int length, int halfWidth, int color) {
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(degrees));
		graphics.fill(-halfWidth, -length, halfWidth, 0, color);
		pose.popPose();
	}
}
