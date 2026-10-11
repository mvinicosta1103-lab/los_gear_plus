package com.example.losgearplus.client.weaker;

import com.example.losgearplus.client.ui.Anim;
import com.example.losgearplus.client.ui.FancyButton;
import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import com.example.losgearplus.partial.PartialShiftPayload;
import com.example.losgearplus.weaker.ShifterForm;
import com.example.losgearplus.weaker.ShifterFormPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Menu de forma do shifter, sincronizado com o titã do portador atual (o servidor informa qual é).
 * Todo shifter tem Normal e Partial; o Weaker só aparece para o Armored Titan. Atalhos 1..3; Esc fecha.
 * Normal/Weaker ficam gravados e valem na próxima transformação; Partial transforma na hora.
 */
public final class ShifterFormScreen extends Screen {
	private static final int CARD_W = 124;
	private static final int CARD_H = 150;
	private static final int GAP = 12;
	private static final int PARTIAL_ACCENT = 0xFF5AA9E6;

	private final String titanId;
	private final boolean canWeaker;
	private final ShifterForm current;
	private final List<ShifterForm> forms = new ArrayList<>();
	private long opened;

	public ShifterFormScreen(String titanId, boolean canWeaker, ShifterForm current) {
		super(Component.translatable("los_gear_plus.shifter.title",
				Component.translatable("los_gear_plus.partial.titan." + titanId)));
		this.titanId = titanId;
		this.canWeaker = canWeaker;
		this.current = current;
	}

	@Override
	protected void init() {
		opened = Util.getMillis();
		forms.clear();
		forms.add(ShifterForm.NORMAL);
		if (canWeaker) forms.add(ShifterForm.WEAKER);
		forms.add(ShifterForm.PARTIAL);

		int n = forms.size();
		int x0 = this.width / 2 - (CARD_W * n + GAP * (n - 1)) / 2;
		int y = this.height / 2 - CARD_H / 2 + 6;
		for (int i = 0; i < n; i++) {
			ShifterForm f = forms.get(i);
			String badge = String.valueOf(i + 1);
			int accent = f == ShifterForm.NORMAL ? Palette.CRIMSON : f == ShifterForm.WEAKER ? Palette.GOLD : PARTIAL_ACCENT;
			addRenderableWidget(new FancyButton(x0 + i * (CARD_W + GAP), y, CARD_W, CARD_H,
					name(f), desc(f), ItemStack.EMPTY, badge, accent, 250 + 130L * i, b -> choose(f)));
		}
	}

	private Component name(ShifterForm f) {
		return Component.translatable("los_gear_plus.armored." + f.name().toLowerCase(Locale.ROOT));
	}

	private Component desc(ShifterForm f) {
		if (f == ShifterForm.NORMAL && !canWeaker) return Component.translatable("los_gear_plus.shifter.normal.desc");
		return Component.translatable("los_gear_plus.armored." + f.name().toLowerCase(Locale.ROOT) + ".desc");
	}

	private void choose(ShifterForm form) {
		if (form == ShifterForm.PARTIAL) {
			if (ClientPlayNetworking.canSend(PartialShiftPayload.TYPE)) ClientPlayNetworking.send(new PartialShiftPayload());
		} else if (ClientPlayNetworking.canSend(ShifterFormPayload.TYPE)) {
			ClientPlayNetworking.send(new ShifterFormPayload(form.ordinal()));
		}
		onClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		int idx = keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9 ? keyCode - GLFW.GLFW_KEY_1
				: keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9 ? keyCode - GLFW.GLFW_KEY_KP_1 : -1;
		if (idx >= 0 && idx < forms.size()) { choose(forms.get(idx)); return true; }
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float a = Anim.easeOutCubic((now - opened) / 300f);
		g.fillGradient(0, 0, this.width, this.height, UiDraw.alpha(0xFF04060A, 0.80f * a), UiDraw.alpha(0xFF1A0A10, 0.88f * a));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		long now = Util.getMillis();
		long elapsed = now - opened;
		int cx = this.width / 2;
		int top = this.height / 2 - CARD_H / 2 + 6;
		int n = forms.size();

		UiDraw.waveText(g, this.font, this.title.getString().toUpperCase(Locale.ROOT), cx, top - 50, 1.7f, elapsed, now,
				Palette.TEXT, Palette.GOLD);
		float line = Anim.easeOutCubic((elapsed - 150) / 500f);
		int half = Math.round((CARD_W * n + GAP * (n - 1)) / 2f * line);
		g.fill(cx - half, top - 20, cx + half, top - 19, UiDraw.alpha(Palette.CRIMSON, 0.9f));

		UiDraw.centered(g, this.font,
				Component.translatable("los_gear_plus.armored.current", name(current).getString()).getString(),
				cx, top - 14, UiDraw.alpha(Palette.GOLD, Anim.clamp01((elapsed - 400) / 300f)));

		StringBuilder hint = new StringBuilder();
		for (int i = 0; i < n; i++) hint.append(i + 1).append(' ').append(name(forms.get(i)).getString()).append("   ");
		hint.append(Component.translatable("los_gear_plus.shifter.esc").getString());
		UiDraw.centered(g, this.font, hint.toString(), cx, top + CARD_H + 16,
				UiDraw.alpha(Palette.TEXT_DIM, 0.75f * Anim.clamp01((elapsed - 600) / 300f)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
