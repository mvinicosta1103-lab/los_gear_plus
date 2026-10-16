package com.example.losgearplus.client.codex;

import com.example.losgearplus.client.ui.Anim;
import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import com.example.losgearplus.codex.CodexData;
import com.example.losgearplus.codex.CodexPayloads;
import com.example.losgearplus.codex.CustomTitanSkill;
import com.example.losgearplus.codex.TitanCodex;
import com.example.losgearplus.codex.TitanIds;
import com.example.losgearplus.codex.TitanSkill;
import com.example.losgearplus.codex.stacks.StackKills;
import com.example.losgearplus.codex.stacks.StackSkill;
import daot.DannysAotClient;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Titan Ability Codex: abas por titã, biblioteca de skills e a barra de slots. Clique numa skill e depois num slot
 * para equipar; botão direito no slot = habilidade original; botão do meio = slot vazio. Tudo é validado no servidor.
 */
public final class TitanCodexScreen extends Screen {
	private static final ResourceLocation SLOT_TEX = ResourceLocation.fromNamespaceAndPath("dannys-aot", "textures/gui/abilities_slot_unselected.png");
	private static final ResourceLocation SLOT_SEL = ResourceLocation.fromNamespaceAndPath("dannys-aot", "textures/gui/abilities_slot_selected.png");

	private static final int CARD = 58, GAP = 8, SLOT = 34, SLOT_GAP = 6, TAB_H = 18;

	private int tab;
	private int scroll;
	private String picked = "";
	private long opened;

	public TitanCodexScreen() {
		super(Component.translatable("los_gear_plus.codex.title"));
	}

	@Override
	protected void init() {
		opened = Util.getMillis();
		int i = CodexClient.titans().indexOf(CodexClient.active());
		tab = Math.max(0, i);
	}

	// ---- geometria (compartilhada entre desenho e cliques) ---------------------------------------------------

	private String titan() {
		List<String> t = CodexClient.titans();
		return t.isEmpty() ? null : t.get(Math.min(tab, t.size() - 1));
	}

	private int panelW() { return Math.min(this.width - 20, 440); }
	private int panelX() { return (this.width - panelW()) / 2; }
	private int top() { return 36; }
	private int tabW() { return Math.min(96, (panelW() - 8) / Math.max(1, CodexClient.titans().size())); }
	private int tabX(int i) { return panelX() + 4 + i * tabW(); }
	private int tabY() { return top() + 26; }
	private int libTop() { return tabY() + TAB_H + 14; }
	private int slotsY() { return this.height - 78; }
	private int libBottom() { return slotsY() - 22; }
	private int cols() { return Math.max(1, (panelW() - 16 + GAP) / (CARD + GAP)); }
	private int visibleRows() { return Math.max(1, (libBottom() - libTop() + GAP) / (CARD + GAP)); }

	private int cardX(int idx) { return panelX() + 8 + (idx % cols()) * (CARD + GAP); }
	private int cardY(int idx) { return libTop() + (idx / cols() - scroll) * (CARD + GAP); }

	private int slotCount() { String t = titan(); return t == null ? 0 : TitanIds.slotCount(t); }
	private int slotsX0() { return this.width / 2 - (slotCount() * (SLOT + SLOT_GAP) - SLOT_GAP) / 2; }
	private int slotX(int i) { return slotsX0() + i * (SLOT + SLOT_GAP); }
	private int resetX() { return panelX() + panelW() - 78; }

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private List<TitanSkill> library() {
		String t = titan();
		return t == null ? List.of() : TitanCodex.skillsFor(t);
	}

	// ---- desenho ---------------------------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float a = Anim.easeOutCubic((Util.getMillis() - opened) / 300f);
		g.fillGradient(0, 0, this.width, this.height, UiDraw.alpha(0xFF04060A, 0.82f * a), UiDraw.alpha(0xFF1A0A10, 0.90f * a));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		long now = Util.getMillis();
		long elapsed = now - opened;
		int cx = this.width / 2;

		UiDraw.waveText(g, this.font, this.title.getString(), cx, top() - 2, 1.5f, elapsed, now, Palette.TEXT, Palette.GOLD);

		String titan = titan();
		if (titan == null) {
			UiDraw.centered(g, this.font, Component.translatable("los_gear_plus.codex.not_shifter").getString(), cx, this.height / 2, Palette.TEXT_DIM);
			return;
		}

		// painel
		int px = panelX(), pw = panelW();
		g.fillGradient(px, top() + 20, px + pw, this.height - 18, UiDraw.alpha(Palette.BG_TOP, 0.94f), UiDraw.alpha(Palette.BG_BOTTOM, 0.96f));
		UiDraw.outline(g, px, top() + 20, pw, this.height - 18 - (top() + 20), UiDraw.alpha(Palette.STEEL, 0.4f));

		List<Component> tooltip = null;

		// abas
		List<String> titans = CodexClient.titans();
		for (int i = 0; i < titans.size(); i++) {
			boolean sel = i == tab;
			boolean hov = in(mouseX, mouseY, tabX(i), tabY(), tabW() - 2, TAB_H);
			int x = tabX(i);
			g.fill(x, tabY(), x + tabW() - 2, tabY() + TAB_H, UiDraw.alpha(sel ? Palette.CRIMSON : Palette.STEEL, sel ? 0.55f : hov ? 0.30f : 0.15f));
			if (sel) g.fill(x, tabY() + TAB_H - 2, x + tabW() - 2, tabY() + TAB_H, Palette.CRIMSON);
			UiDraw.centered(g, this.font, Component.translatable("los_gear_plus.codex.titan." + titans.get(i)).getString(),
					x + (tabW() - 2) / 2, tabY() + 5, sel ? Palette.TEXT : Palette.TEXT_DIM);
		}

		// biblioteca
		UiDraw.text(g, this.font, Component.translatable("los_gear_plus.codex.library").getString(), px + 8, libTop() - 11, Palette.GOLD);
		List<TitanSkill> skills = library();
		int rows = (skills.size() + cols() - 1) / cols();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows - visibleRows())));
		for (int i = 0; i < skills.size(); i++) {
			int row = i / cols();
			if (row < scroll || row >= scroll + visibleRows()) continue;
			TitanSkill s = skills.get(i);
			int x = cardX(i), y = cardY(i);
			boolean unlocked = CodexClient.isUnlocked(s);
			boolean isPicked = s.id().equals(picked);
			boolean hov = in(mouseX, mouseY, x, y, CARD, CARD);

			g.fill(x, y, x + CARD, y + CARD, UiDraw.alpha(0xFF0B0E13, 0.9f));
			UiDraw.glow(g, x, y, CARD, CARD, isPicked ? Palette.GOLD : s.isCustom() ? Palette.CRIMSON : Palette.STEEL, isPicked ? 1f : hov ? 0.7f : 0f);
			if (!isPicked && !hov) UiDraw.outline(g, x, y, CARD, CARD, UiDraw.alpha(s.isCustom() ? Palette.CRIMSON : Palette.STEEL, 0.4f));
			drawIcon(g, s.icon(), x + (CARD - 32) / 2, y + 6, 32, unlocked);
			String name = trim(s.name().getString(), CARD - 4);
			UiDraw.centered(g, this.font, name, x + CARD / 2, y + 42, unlocked ? Palette.TEXT : Palette.TEXT_DIM);
			if (!unlocked) UiDraw.centered(g, this.font, Component.translatable("los_gear_plus.codex.locked").getString(), x + CARD / 2, y + 12, Palette.CRIMSON);

			if (hov) tooltip = skillTooltip(s, unlocked);
		}
		if (rows > visibleRows()) {
			UiDraw.text(g, this.font, (scroll + 1) + "/" + (rows - visibleRows() + 1), px + pw - 36, libTop() - 11, Palette.TEXT_DIM);
		}

		// barra de slots
		int sy = slotsY();
		UiDraw.text(g, this.font, Component.translatable("los_gear_plus.codex.slots").getString(), px + 8, sy - 20, Palette.GOLD);
		if (!picked.isEmpty()) {
			TitanSkill ps = TitanCodex.find(picked).orElse(null);
			if (ps != null) UiDraw.text(g, this.font, Component.translatable("los_gear_plus.codex.picked", ps.name().getString()).getString(),
					px + 8 + 90, sy - 20, Palette.GREEN);
		}
		for (int i = 0; i < slotCount(); i++) {
			int x = slotX(i);
			boolean hov = in(mouseX, mouseY, x, sy, SLOT, SLOT);
			String id = CodexClient.slotSkill(titan, i);
			TitanSkill shown = id.equals(CodexData.EMPTY) ? null
					: id.isEmpty() ? TitanCodex.defaultSkill(titan, i) : TitanCodex.find(id).orElse(null);

			UiDraw.centered(g, this.font, keyName(titan, i), x + SLOT / 2, sy - 10, Palette.TEXT_DIM);
			g.blit(hov && !picked.isEmpty() ? SLOT_SEL : SLOT_TEX, x, sy, SLOT, SLOT, 0f, 0f, 48, 46, 48, 46);
			if (shown != null) drawIcon(g, shown.icon(), x + (SLOT - 26) / 2, sy + (SLOT - 26) / 2, 26, true);
			if (!id.isEmpty() && !id.equals(CodexData.EMPTY)) g.fill(x + SLOT - 6, sy + 2, x + SLOT - 2, sy + 6, Palette.GOLD); // marca "personalizado"

			if (hov) {
				tooltip = new ArrayList<>();
				if (shown == null) {
					tooltip.add(Component.translatable("los_gear_plus.codex.empty").withStyle(ChatFormatting.GRAY));
				} else {
					tooltip.add(shown.name().copy().withStyle(ChatFormatting.GOLD));
					if (id.isEmpty()) tooltip.add(Component.translatable("los_gear_plus.codex.default").withStyle(ChatFormatting.DARK_GRAY));
				}
			}
		}
		boolean resetHov = in(mouseX, mouseY, resetX(), sy + 6, 70, 18);
		g.fill(resetX(), sy + 6, resetX() + 70, sy + 24, UiDraw.alpha(Palette.CRIMSON, resetHov ? 0.6f : 0.25f));
		UiDraw.centered(g, this.font, Component.translatable("los_gear_plus.codex.reset").getString(), resetX() + 35, sy + 11, Palette.TEXT);

		UiDraw.centered(g, this.font, trim(Component.translatable("los_gear_plus.codex.hint").getString(), pw - 8), cx, this.height - 14 + 0,
				UiDraw.alpha(Palette.TEXT_DIM, 0.8f));

		if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
	}

	private List<Component> skillTooltip(TitanSkill s, boolean unlocked) {
		List<Component> t = new ArrayList<>();
		t.add(s.name().copy().withStyle(ChatFormatting.GOLD));
		t.add(s.description().copy().withStyle(ChatFormatting.GRAY));
		if (s instanceof CustomTitanSkill c) {
			t.add(Component.translatable("los_gear_plus.codex.cooldown", c.cooldownTicks() / 20).withStyle(ChatFormatting.AQUA));
			if (c.staminaFraction() > 0f) {
				t.add(Component.translatable("los_gear_plus.codex.stamina", Math.round(c.staminaFraction() * 100f)).withStyle(ChatFormatting.AQUA));
			}
			if (s.titanId().equals(TitanSkill.ALL)) t.add(Component.translatable("los_gear_plus.codex.compat_all").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (s instanceof StackSkill stackSkill) {
			Component source = Component.translatable("los_gear_plus.codex.source." + stackSkill.source().id);
			int level = CodexClient.stackLevel(stackSkill.source().id);
			if (level > 0) t.add(Component.translatable("los_gear_plus.codex.stack.line", source, StackKills.roman(level)).withStyle(ChatFormatting.LIGHT_PURPLE));
			else t.add(Component.translatable("los_gear_plus.codex.stack.locked_tip", source).withStyle(ChatFormatting.RED));
		} else if (!unlocked) {
			t.add(Component.translatable("los_gear_plus.codex.locked_tip").withStyle(ChatFormatting.RED));
		}
		return t;
	}

	private void drawIcon(GuiGraphics g, ResourceLocation icon, int x, int y, int size, boolean enabled) {
		if (!enabled) g.setColor(0.4f, 0.4f, 0.4f, 1f);
		g.blit(icon, x, y, size, size, 0f, 0f, 32, 32, 32, 32);
		g.setColor(1f, 1f, 1f, 1f);
	}

	private String trim(String s, int maxPx) {
		if (this.font.width(s) <= maxPx) return s;
		while (s.length() > 1 && this.font.width(s + "…") > maxPx) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	/** Nome da tecla de habilidade i (a mesma do DAOT, então respeita o que o jogador remapeou). */
	private static String keyName(String titan, int i) {
		if (TitanIds.STACK.equals(titan)) return CodexClient.stackKeyName(i); // as 3 teclas próprias dos stacks
		KeyMapping[] keys = {
				DannysAotClient.SHIFTER_ABILITY_1_KEY, DannysAotClient.SHIFTER_ABILITY_2_KEY, DannysAotClient.SHIFTER_ABILITY_3_KEY,
				DannysAotClient.SHIFTER_ABILITY_4_KEY, DannysAotClient.SHIFTER_ABILITY_5_KEY, DannysAotClient.SHIFTER_ABILITY_6_KEY,
				DannysAotClient.SHIFTER_ABILITY_7_KEY, DannysAotClient.SHIFTER_ABILITY_8_KEY, DannysAotClient.SHIFTER_ABILITY_9_KEY };
		return i < keys.length ? keys[i].getTranslatedKeyMessage().getString().toUpperCase() : String.valueOf(i + 1);
	}

	// ---- entrada ---------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		String titan = titan();
		if (titan == null) return super.mouseClicked(mx, my, button);

		if (button == 0) {
			for (int i = 0; i < CodexClient.titans().size(); i++) {
				if (in(mx, my, tabX(i), tabY(), tabW() - 2, TAB_H)) {
					tab = i;
					scroll = 0;
					picked = "";
					return true;
				}
			}
			List<TitanSkill> skills = library();
			for (int i = 0; i < skills.size(); i++) {
				int row = i / cols();
				if (row < scroll || row >= scroll + visibleRows()) continue;
				if (in(mx, my, cardX(i), cardY(i), CARD, CARD)) {
					TitanSkill s = skills.get(i);
					if (CodexClient.isUnlocked(s)) picked = s.id().equals(picked) ? "" : s.id();
					return true;
				}
			}
			if (in(mx, my, resetX(), slotsY() + 6, 70, 18)) {
				send(titan, -1, CodexData.DEFAULT);
				return true;
			}
		}
		for (int i = 0; i < slotCount(); i++) {
			if (!in(mx, my, slotX(i), slotsY(), SLOT, SLOT)) continue;
			if (button == 0 && !picked.isEmpty()) {
				send(titan, i, picked);
				picked = "";
			} else if (button == 1) {
				send(titan, i, CodexData.DEFAULT);
			} else if (button == 2) {
				send(titan, i, CodexData.EMPTY);
			}
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	private void send(String titan, int slot, String skill) {
		CodexClient.applyLocal(titan, slot, skill);
		if (ClientPlayNetworking.canSend(CodexPayloads.Assign.TYPE)) {
			ClientPlayNetworking.send(new CodexPayloads.Assign(titan, slot, skill));
		}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		scroll -= (int) Math.signum(dy);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (CodexClient.key() != null && CodexClient.key().matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
