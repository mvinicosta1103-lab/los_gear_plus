package com.example.losgearplus.client.colossal;

import com.example.losgearplus.client.ui.Anim;
import com.example.losgearplus.client.ui.FancyButton;
import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Colossal transformation mode menu: 1 = With Nuke (giant explosion), 2 = Normal (like the other titans).
 * Applies to the full transformation and to Tease Shift. The choice is saved on the server.
 */
public final class ColossalNukeScreen extends Screen {
	private static final int CARD_W = 140;
	private static final int CARD_H = 150;
	private static final int GAP = 14;

	private final boolean currentNuke;
	private long opened;

	public ColossalNukeScreen(boolean currentNuke) {
		super(Component.translatable("los_gear_plus.colossal_nuke.title"));
		this.currentNuke = currentNuke;
	}

	@Override
	protected void init() {
		opened = Util.getMillis();
		int x0 = this.width / 2 - (CARD_W * 2 + GAP) / 2;
		int y = this.height / 2 - CARD_H / 2 + 6;
		addRenderableWidget(new FancyButton(x0, y, CARD_W, CARD_H,
				Component.translatable("los_gear_plus.colossal_nuke.nuke"),
				Component.translatable("los_gear_plus.colossal_nuke.nuke.desc"),
				ItemStack.EMPTY, "1", Palette.CRIMSON, 250, b -> choose(true)));
		addRenderableWidget(new FancyButton(x0 + CARD_W + GAP, y, CARD_W, CARD_H,
				Component.translatable("los_gear_plus.colossal_nuke.normal"),
				Component.translatable("los_gear_plus.colossal_nuke.normal.desc"),
				ItemStack.EMPTY, "2", Palette.STEEL, 380, b -> choose(false)));
	}

	private void choose(boolean nuke) {
		ColossalNukeClient.choose(nuke);
		onClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1) { choose(true); return true; }
		if (keyCode == GLFW.GLFW_KEY_2 || keyCode == GLFW.GLFW_KEY_KP_2) { choose(false); return true; }
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float a = Anim.easeOutCubic((Util.getMillis() - opened) / 300f);
		g.fillGradient(0, 0, this.width, this.height, UiDraw.alpha(0xFF04060A, 0.80f * a), UiDraw.alpha(0xFF1A0A10, 0.88f * a));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		long now = Util.getMillis();
		long elapsed = now - opened;
		int cx = this.width / 2;
		int top = this.height / 2 - CARD_H / 2 + 6;

		UiDraw.waveText(g, this.font, this.title.getString().toUpperCase(Locale.ROOT), cx, top - 50, 1.7f, elapsed, now,
				Palette.TEXT, Palette.GOLD);
		float line = Anim.easeOutCubic((elapsed - 150) / 500f);
		int half = Math.round((CARD_W * 2 + GAP) / 2f * line);
		g.fill(cx - half, top - 20, cx + half, top - 19, UiDraw.alpha(Palette.CRIMSON, 0.9f));

		Component cur = Component.translatable(currentNuke
				? "los_gear_plus.colossal_nuke.nuke" : "los_gear_plus.colossal_nuke.normal");
		UiDraw.centered(g, this.font, Component.translatable("los_gear_plus.colossal_nuke.current", cur.getString()).getString(),
				cx, top - 14, UiDraw.alpha(Palette.GOLD, Anim.clamp01((elapsed - 400) / 300f)));

		UiDraw.centered(g, this.font, Component.translatable("los_gear_plus.colossal_nuke.hint").getString(),
				cx, top + CARD_H + 16, UiDraw.alpha(Palette.TEXT_DIM, 0.75f * Anim.clamp01((elapsed - 600) / 300f)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
