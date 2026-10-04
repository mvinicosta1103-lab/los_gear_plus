package com.example.losgearplus.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Botão-cartão animado: entra deslizando com atraso, sobe e cresce no hover, ganha brilho, faixa de acento
 * que se expande, reflexo passando e ícone flutuando. Substitui o Button padrão.
 */
public class FancyButton extends Button {
	private final ItemStack icon;
	private final Component description;
	private final String badge;
	private final int accent;
	private final long entranceDelayMs;

	private long born;
	private long last;
	private float hover;

	public FancyButton(int x, int y, int w, int h, Component label, Component description, ItemStack icon,
			String badge, int accent, long entranceDelayMs, OnPress onPress) {
		super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
		this.description = description;
		this.icon = icon;
		this.badge = badge;
		this.accent = accent;
		this.entranceDelayMs = entranceDelayMs;
	}

	@Override
	public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		if (born == 0L) { born = now; last = now; }
		float dt = Math.min(0.05f, (now - last) / 1000f);
		last = now;
		hover = Anim.smooth(hover, isHoveredOrFocused() ? 1f : 0f, 14f, dt);

		float in = Anim.easeOutCubic((now - born - entranceDelayMs) / 450f);
		if (in <= 0.01f) return;

		Font font = Minecraft.getInstance().font;
		int w = getWidth(), h = getHeight();
		float lift = (1f - in) * 16f - hover * 3f;
		float scale = 1f + hover * 0.03f;

		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(getX() + w / 2f, getY() + h / 2f + lift, 0);
		pose.scale(scale, scale, 1f);
		pose.translate(-w / 2f, -h / 2f, 0);

		// corpo + brilho
		UiDraw.glow(g, 0, 0, w, h, accent, hover * in);
		g.fillGradient(0, 0, w, h, UiDraw.alpha(Palette.BG_TOP, 0.94f * in), UiDraw.alpha(Palette.BG_BOTTOM, 0.96f * in));
		UiDraw.outline(g, 0, 0, w, h, UiDraw.alpha(Palette.STEEL, 0.35f * in));

		// faixa de acento no topo, cresce no hover
		int bar = Math.round(w * (0.22f + 0.78f * hover));
		g.fill(0, 0, bar, 2, UiDraw.alpha(accent, in));

		// reflexo diagonal passando no hover
		if (hover > 0.02f) {
			float p = (now % 1500L) / 1500f;
			int bx = Math.round(p * (w + 40) - 20);
			int x0 = Math.max(1, bx), x1 = Math.min(w - 1, bx + 12);
			if (x1 > x0) g.fill(x0, 2, x1, h - 1, UiDraw.alpha(0xFFFFFFFF, 0.07f * hover * in));
		}

		// ícone grande flutuando
		if (!icon.isEmpty()) {
			float bob = (float) Math.sin(now / 380.0) * 1.8f * hover;
			float is = 3f;
			pose.pushPose();
			pose.translate(w / 2f - 8 * is, 14 + bob, 0);
			pose.scale(is, is, 1f);
			g.renderItem(icon, 0, 0);
			pose.popPose();
		}

		// título
		int titleY = 14 + 48 + 10;
		Component title = getMessage().copy().withStyle(ChatFormatting.BOLD);
		int titleColor = UiDraw.alpha(UiDraw.mix(Palette.TEXT, accent, hover * 0.6f), in);
		if (UiDraw.visible(titleColor)) {
			g.drawString(font, title, w / 2 - font.width(title) / 2, titleY, titleColor, true);
		}

		// descrição (quebra de linha)
		int descColor = UiDraw.alpha(Palette.TEXT_DIM, in);
		if (description != null && UiDraw.visible(descColor)) {
			List<FormattedCharSequence> lines = font.split(FormattedText.of(description.getString()), w - 20);
			int dy = titleY + 14;
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, w / 2 - font.width(line) / 2, dy, descColor, false);
				dy += 10;
			}
		}

		// selo da tecla de atalho
		if (badge != null) {
			int bw = font.width(badge) + 8;
			g.fill(6, 6, 6 + bw, 18, UiDraw.alpha(0xFF000000, 0.55f * in));
			UiDraw.outline(g, 6, 6, bw, 12, UiDraw.alpha(accent, (0.5f + 0.5f * hover) * in));
			UiDraw.text(g, font, badge, 10, 8, UiDraw.alpha(Palette.TEXT, in));
		}

		pose.popPose();
	}
}
