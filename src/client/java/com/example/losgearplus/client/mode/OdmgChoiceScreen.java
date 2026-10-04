package com.example.losgearplus.client.mode;

import com.example.losgearplus.client.ui.Anim;
import com.example.losgearplus.client.ui.FancyButton;
import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import com.example.losgearplus.grip.GripItems;
import com.example.losgearplus.mode.OdmgChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Tela de seleção: aparece ao ligar o ODMG Mode com New ODM Gear + New ODM Uniform vestidos e com grips E pistolas
 * disponíveis. Dois cartões animados (atalhos 1 e 2): New ODM Gear com blades, ou New ODM Uniform com pistolas.
 * Esc fecha sem escolher.
 */
public final class OdmgChoiceScreen extends Screen {
	private static final int CARD_W = 150;
	private static final int CARD_H = 124;
	private static final int GAP = 16;

	private long opened;

	public OdmgChoiceScreen() {
		super(Component.translatable("los_gear_plus.choice.title"));
	}

	@Override
	protected void init() {
		opened = Util.getMillis();
		Item grip = GripItems.get();
		ItemStack bladeIcon = grip == null ? ItemStack.EMPTY : new ItemStack(grip);
		ItemStack gunIcon = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("los_gear", "mauser_rifle"))
				.map(ItemStack::new).orElse(ItemStack.EMPTY);

		int x = this.width / 2 - CARD_W - GAP / 2;
		int y = this.height / 2 - CARD_H / 2 + 6;
		addRenderableWidget(new FancyButton(x, y, CARD_W, CARD_H,
				Component.translatable("los_gear_plus.choice.blades"),
				Component.translatable("los_gear_plus.choice.blades.desc"),
				bladeIcon, "1", Palette.CRIMSON, 250, b -> choose(false)));
		addRenderableWidget(new FancyButton(x + CARD_W + GAP, y, CARD_W, CARD_H,
				Component.translatable("los_gear_plus.choice.pistols"),
				Component.translatable("los_gear_plus.choice.pistols.desc"),
				gunIcon, "2", Palette.GOLD, 380, b -> choose(true)));
	}

	private void choose(boolean guns) {
		ClientPlayNetworking.send(new OdmgChoicePayload(guns));
		onClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1) { choose(false); return true; }
		if (keyCode == GLFW.GLFW_KEY_2 || keyCode == GLFW.GLFW_KEY_KP_2) { choose(true); return true; }
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float a = Anim.easeOutCubic((now - opened) / 300f);
		g.fillGradient(0, 0, this.width, this.height, UiDraw.alpha(0xFF04060A, 0.80f * a), UiDraw.alpha(0xFF1A0A10, 0.88f * a));

		// linhas diagonais lentas ao fundo
		for (int i = -2; i < this.width / 40 + 3; i++) {
			int x = i * 40 + (int) ((now / 40L) % 40L);
			g.fill(x, 0, x + 1, this.height, UiDraw.alpha(Palette.CRIMSON, 0.05f * a));
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick); // fundo + cartões
		long now = Util.getMillis();
		long elapsed = now - opened;
		int cx = this.width / 2;
		int top = this.height / 2 - CARD_H / 2 + 6;

		// título em onda + linha que se abre do centro
		UiDraw.waveText(g, this.font, this.title.getString(), cx, top - 50, 1.7f, elapsed, now, Palette.TEXT, Palette.GOLD);
		float line = Anim.easeOutCubic((elapsed - 150) / 500f);
		int half = Math.round((CARD_W + GAP / 2) * line);
		g.fill(cx - half, top - 20, cx + half, top - 19, UiDraw.alpha(Palette.CRIMSON, 0.9f));
		g.fill(cx - half / 2, top - 18, cx + half / 2, top - 17, UiDraw.alpha(Palette.GOLD, 0.6f));

		// dica de teclas, pulsando de leve
		float pulse = 0.65f + 0.2f * (float) Math.sin(now / 500.0);
		UiDraw.centered(g, this.font, Component.translatable("los_gear_plus.choice.hint").getString(),
				cx, top + CARD_H + 16, UiDraw.alpha(Palette.TEXT_DIM, pulse * Anim.clamp01((elapsed - 600) / 300f)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
