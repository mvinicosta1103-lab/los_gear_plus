package com.example.losgearplus.client.limb;

import com.example.losgearplus.limb.LimbData;
import com.example.losgearplus.limb.LimbState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * Visão: um olho perdido escurece o lado dele; os dois = túnel quase total com um brilho mínimo no centro
 * (jogável). Olho crescendo devolve a visão aos poucos. Sem shader: anéis/faixas de retângulos translúcidos.
 */
public final class LimbVisionHud {
	private LimbVisionHud() {}

	private static final int RINGS = 24;
	private static final int STRIPS = 28;

	public static void init() {
		HudRenderCallback.EVENT.register(LimbVisionHud::render);
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !p.isAlive()) return;
		LimbState s = LimbData.of(p);
		float l = s.eyeLoss(true);
		float r = s.eyeLoss(false);
		if (l <= 0f && r <= 0f) return;

		int w = g.guiWidth();
		int h = g.guiHeight();
		float both = Math.min(l, r);
		if (both > 0f) tunnel(g, w, h, both);
		if (l - both > 0f) side(g, w, h, true, l - both);
		if (r - both > 0f) side(g, w, h, false, r - both);
	}

	private static int argb(float alpha) {
		return (Math.max(0, Math.min(255, (int) (alpha * 255f))) << 24);
	}

	/** Cego: borda quase preta, centro ainda um pouco visível. */
	private static void tunnel(GuiGraphics g, int w, int h, float strength) {
		int stepX = Math.max(1, (w / 2) / RINGS);
		int stepY = Math.max(1, (h / 2) / RINGS);
		float edge = 0.98f * strength;
		float center = 0.80f * strength;
		for (int i = 0; i < RINGS; i++) {
			float t = i / (float) (RINGS - 1);
			float smooth = t * t * (3f - 2f * t);
			int c = argb(edge + (center - edge) * smooth);
			int x0 = i * stepX, y0 = i * stepY, x1 = w - x0, y1 = h - y0;
			g.fill(x0, y0, x1, y0 + stepY, c);
			g.fill(x0, y1 - stepY, x1, y1, c);
			g.fill(x0, y0 + stepY, x0 + stepX, y1 - stepY, c);
			g.fill(x1 - stepX, y0 + stepY, x1, y1 - stepY, c);
		}
		g.fill(RINGS * stepX, RINGS * stepY, w - RINGS * stepX, h - RINGS * stepY, argb(center));
	}

	/** Um olho: faixa escura no lado do olho perdido, sumindo em direção ao centro. */
	private static void side(GuiGraphics g, int w, int h, boolean left, float strength) {
		int sw = Math.max(1, (int) (w * 0.55f) / STRIPS);
		for (int i = 0; i < STRIPS; i++) {
			float t = i / (float) STRIPS;
			float a = (float) Math.pow(1f - t, 1.6) * 0.97f * strength;
			if (a < 0.01f) continue;
			int x0 = left ? i * sw : w - (i + 1) * sw;
			g.fill(x0, 0, x0 + sw, h, argb(a));
		}
	}
}
