package com.example.losgearplus.client.ui;

import com.example.losgearplus.client.mode.OdmgModeClient;
import com.example.losgearplus.client.mode.OdmgSpeedLevel;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * HUD do mod: banners animados no topo (substituem as mensagens da action bar) e o medidor de velocidade
 * do ODMG perto da mira.
 */
public final class OdmgHud {
	private OdmgHud() {}

	private static final long IN_MS = 380, HOLD_MS = 2200, OUT_MS = 380;

	private static Component toastText;
	private static long toastBorn;
	private static int toastAccent = Palette.GOLD;

	private static long speedBorn = -100_000L;
	private static float speedShown = OdmgSpeedLevel.DEFAULT;
	private static float speedVis;
	private static long lastMs;

	public static void init() {
		HudRenderCallback.EVENT.register(OdmgHud::render);
	}

	/** Mostra um banner. A cor do acento vem da chave de tradução (ligado/desligado/erro/info). */
	public static void toast(Component text) {
		toastText = text;
		toastBorn = Util.getMillis();
		toastAccent = accentFor(text);
	}

	/** Faz o medidor de velocidade aparecer (chamado ao mudar o nível). */
	public static void flashSpeed() {
		speedBorn = Util.getMillis();
	}

	private static int accentFor(Component c) {
		String key = c.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
		if (key.endsWith("odmg_on") || key.endsWith(".on")) return Palette.GREEN;
		if (key.endsWith("odmg_off") || key.endsWith(".off")) return Palette.STEEL;
		if (key.contains("no_") || key.contains("unavailable") || key.contains("not_")) return Palette.CRIMSON;
		return Palette.GOLD;
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) return;
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - lastMs) / 1000f);
		lastMs = now;
		renderToast(g, mc.font, now);
		renderSpeed(g, mc, now, dt);
	}

	private static void renderToast(GuiGraphics g, Font font, long now) {
		if (toastText == null) return;
		long age = now - toastBorn;
		if (age > IN_MS + HOLD_MS + OUT_MS) { toastText = null; return; }

		float slide = age < IN_MS ? Anim.easeOutBack(age / (float) IN_MS) : 1f;
		float out = age > IN_MS + HOLD_MS ? (age - IN_MS - HOLD_MS) / (float) OUT_MS : 0f;
		float a = 1f - out;

		String s = toastText.getString();
		int w = font.width(s) + 46, h = 22;
		int x = g.guiWidth() / 2 - w / 2;
		int y = Math.round(-h + (26 + h) * slide - out * 10f);

		g.fillGradient(x, y, x + w, y + h, UiDraw.alpha(Palette.BG_TOP, 0.92f * a), UiDraw.alpha(Palette.BG_BOTTOM, 0.94f * a));
		UiDraw.outline(g, x, y, w, h, UiDraw.alpha(Palette.STEEL, 0.4f * a));
		g.fill(x, y, x + 3, y + h, UiDraw.alpha(toastAccent, a));

		// losango pulsante
		int dx = x + 15, dy = y + h / 2;
		int pulse = 3 + (int) (Math.sin(now / 160.0) + 1.0);
		for (int i = -pulse; i <= pulse; i++) {
			int span = pulse - Math.abs(i);
			g.fill(dx - span, dy + i, dx + span + 1, dy + i + 1, UiDraw.alpha(toastAccent, a));
		}
		UiDraw.text(g, font, s, x + 28, y + 7, UiDraw.alpha(Palette.TEXT, a));

		// barra de tempo
		float left = 1f - Anim.clamp01((age - IN_MS) / (float) HOLD_MS);
		g.fill(x + 3, y + h - 2, x + 3 + Math.round((w - 3) * left), y + h - 1, UiDraw.alpha(toastAccent, 0.8f * a));
	}

	private static void renderSpeed(GuiGraphics g, Minecraft mc, long now, float dt) {
		boolean holding = OdmgModeClient.isActive() && OdmgSpeedLevel.SET_LEVEL_KEY != null
				&& OdmgSpeedLevel.SET_LEVEL_KEY.isDown();
		boolean recent = now - speedBorn < 1700L;
		speedVis = Anim.smooth(speedVis, (holding || recent) && OdmgModeClient.isActive() ? 1f : 0f, 12f, dt);
		speedShown = Anim.smooth(speedShown, OdmgSpeedLevel.current(), 14f, dt);
		if (speedVis < 0.03f) return;

		Font font = mc.font;
		int level = OdmgSpeedLevel.current();
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 24 + Math.round((1f - speedVis) * 6f);
		int accent = level == 0 ? Palette.STEEL : level == 1 ? Palette.GREEN : level == 2 ? Palette.GOLD : Palette.CRIMSON;

		int segW = 16, segH = 5, gap = 3, n = OdmgSpeedLevel.MAX + 1;
		int total = n * segW + (n - 1) * gap;
		int x0 = cx - total / 2;

		UiDraw.centered(g, font, Component.translatable("los_gear_plus.hud.speed").getString() + "  " + level,
				cx, y - 12, UiDraw.alpha(UiDraw.mix(Palette.TEXT, accent, 0.5f), speedVis));
		for (int i = 0; i < n; i++) {
			int sx = x0 + i * (segW + gap);
			g.fill(sx, y, sx + segW, y + segH, UiDraw.alpha(0xFF000000, 0.55f * speedVis));
			float fill = Anim.clamp01(speedShown - i + 1f);
			int fw = Math.round(segW * fill);
			if (fw > 0) {
				g.fill(sx, y, sx + fw, y + segH, UiDraw.alpha(accent, speedVis));
				g.fill(sx, y, sx + fw, y + 1, UiDraw.alpha(0xFFFFFFFF, 0.35f * speedVis));
			}
			UiDraw.outline(g, sx - 1, y - 1, segW + 2, segH + 2, UiDraw.alpha(Palette.STEEL, 0.4f * speedVis));
		}
	}
}
