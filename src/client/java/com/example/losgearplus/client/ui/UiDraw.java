package com.example.losgearplus.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Helpers de desenho: cores com alpha, contornos, brilho e texto animado. */
public final class UiDraw {
	private UiDraw() {}

	/** Multiplica o alpha de uma cor ARGB por {@code a} (0..1). */
	public static int alpha(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * Anim.clamp01(a));
		return (al << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int c1, int c2, float t) {
		t = Anim.clamp01(t);
		int a = (int) Anim.lerp((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, t);
		int r = (int) Anim.lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
		int g = (int) Anim.lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
		int b = (int) Anim.lerp(c1 & 0xFF, c2 & 0xFF, t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** O vanilla trata alpha < 4 como opaco em texto; por isso texto quase invisível nem é desenhado. */
	public static boolean visible(int argb) {
		return ((argb >>> 24) & 0xFF) >= 8;
	}

	public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** Contorno com brilho que se dissipa para fora. intensity 0..1. */
	public static void glow(GuiGraphics g, int x, int y, int w, int h, int color, float intensity) {
		if (intensity <= 0.01f) return;
		for (int i = 1; i <= 4; i++) {
			outline(g, x - i, y - i, w + 2 * i, h + 2 * i, alpha(color, intensity * 0.22f * (5 - i) / 4f));
		}
		outline(g, x, y, w, h, alpha(color, 0.35f + 0.65f * intensity));
	}

	public static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
		if (visible(color)) g.drawString(font, s, x, y, color, true);
	}

	public static void centered(GuiGraphics g, Font font, String s, int cx, int y, int color) {
		if (visible(color)) g.drawString(font, s, cx - font.width(s) / 2, y, color, true);
	}

	/** Texto centralizado com escala (ex.: 1.5f para números grandes). */
	public static void centeredScaled(GuiGraphics g, Font font, String s, int cx, int y, float scale, int color) {
		if (!visible(color)) return;
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(cx, y, 0);
		pose.scale(scale, scale, 1f);
		g.drawString(font, s, -font.width(s) / 2, 0, color, true);
		pose.popPose();
	}

	/**
	 * Título letra a letra: cada letra entra com atraso (revelação), sobe e desce em onda e ganha um brilho que
	 * percorre o texto. {@code elapsedMs} = tempo desde a abertura da tela.
	 */
	public static void waveText(GuiGraphics g, Font font, String s, int cx, int y, float scale,
			long elapsedMs, long nowMs, int color, int shimmerColor) {
		PoseStack pose = g.pose();
		float total = font.width(s) * scale;
		float startX = cx - total / 2f;
		pose.pushPose();
		pose.translate(startX, y, 0);
		pose.scale(scale, scale, 1f);
		float advance = 0f;
		float shimmerPos = (nowMs % 2600L) / 2600f * (s.length() + 8) - 4f; // faixa de brilho passando pelo texto
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			float reveal = Anim.easeOutCubic((elapsedMs - i * 35L) / 380f);
			float wave = (float) Math.sin(nowMs / 280.0 + i * 0.55) * 1.6f * reveal;
			float rise = (1f - reveal) * 10f;
			float shine = Anim.clamp01(1f - Math.abs(i - shimmerPos) / 2.5f);
			int c = alpha(mix(color, shimmerColor, shine), reveal);
			if (visible(c)) g.drawString(font, ch, Math.round(advance), Math.round(wave + rise), c, true);
			advance += font.width(ch);
		}
		pose.popPose();
	}
}
