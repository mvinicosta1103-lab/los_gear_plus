package com.example.losgearplus.client.shifter;

import com.example.losgearplus.client.ui.Anim;
import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import com.example.losgearplus.shifter.MasteryConfigPayloads;
import com.example.losgearplus.shifter.ShifterMasteryConfig;
import com.example.losgearplus.shifter.ShifterMasteryConfig.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Shifter Mastery config menu (operators only; the server checks the permission again when saving).
 * Three tabs: General (toggles, time, XP), Levels (a table with one row per mastery level, plus an "All levels" row
 * that fills a whole column at once) and Hearts &amp; Bonuses. "Save &amp; Apply" sends everything to the server, which
 * writes {@code config/los_gear_plus-mastery.json} and applies it immediately.
 */
public final class MasteryConfigScreen extends Screen {
	private static final int TAB_GENERAL = 0, TAB_LEVELS = 1, TAB_HEARTS = 2;
	private static final String[] TAB_NAMES = {"General", "Levels", "Hearts & Bonuses"};
	private static final String[] COLUMNS = {"Transformations", "Cost %", "Drain %", "XP to reach"};
	private static final String[] COLUMN_TIPS = {
			"How many times the shifter can transform before the cooldown. -1 = unlimited.",
			"Stamina the transformation itself costs. 100 = normal, 0 = free.",
			"How fast stamina runs out in titan form. 100 = base, 200 = twice as fast, 0 = never runs out.",
			"Total mastery XP needed to REACH this level (level 0 is always 0)."};
	private static final int LEVELS = 10;

	private Data work;
	private int tab;
	private final long opened = Util.getMillis();

	// text drawn in render(): rebuilt on every init()
	private record Label(int x, int y, String text, int color) {}
	private final List<Label> labels = new ArrayList<>();
	private int hintY;

	public MasteryConfigScreen(String json) {
		super(Component.literal("Shifter Mastery Config"));
		this.work = ShifterMasteryConfig.fromJson(json);
	}

	// ---- layout -------------------------------------------------------------------------------------------

	private int panelW() {
		return Math.min(this.width - 16, 440);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private static final int TOP = 30;

	private int contentTop() {
		return TOP + 28;
	}

	private int bottomY() {
		return this.height - 26;
	}

	@Override
	protected void init() {
		labels.clear();
		int pw = panelW(), left = left();

		// tabs
		int tw = (pw - 8) / 3;
		for (int i = 0; i < 3; i++) {
			final int idx = i;
			Component name = Component.literal(TAB_NAMES[i]).withStyle(i == tab ? ChatFormatting.GOLD : ChatFormatting.WHITE);
			addRenderableWidget(Button.builder(name, b -> { tab = idx; rebuildWidgets(); })
					.bounds(left + 4 + i * tw, TOP, tw - 2, 20).build());
		}

		// bottom buttons
		int bw = (pw - 8) / 3;
		int by = bottomY();
		addRenderableWidget(Button.builder(Component.literal("Save & Apply").withStyle(ChatFormatting.GREEN), b -> save())
				.bounds(left + 4, by, bw - 2, 20)
				.tooltip(Tooltip.create(Component.literal("Send to the server: writes the config file and applies it right now."))).build());
		addRenderableWidget(Button.builder(Component.literal("Defaults"), b -> { work = new Data(); rebuildWidgets(); })
				.bounds(left + 4 + bw, by, bw - 2, 20)
				.tooltip(Tooltip.create(Component.literal("Fill the menu with the default values (nothing is saved until you press Save & Apply)."))).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(left + 4 + 2 * bw, by, bw - 2, 20).build());

		switch (tab) {
			case TAB_GENERAL -> buildGeneral();
			case TAB_LEVELS -> buildLevels();
			default -> buildHearts();
		}
	}

	// ---- tab: General -------------------------------------------------------------------------------------

	private int rowY;

	private void buildGeneral() {
		rowY = contentTop();
		toggleRow("Max level = infinite", work.maxLevelInfinite, v -> work.maxLevelInfinite = v,
				"Level 9: unlimited transformations and no stamina spent at all (transforming, titan form and Codex skills). "
						+ "When off, level 9 follows the Levels table.");
		toggleRow("Cart Titan always infinite", work.cartAlwaysInfinite, v -> work.cartAlwaysInfinite = v,
				"The Cart Titan acts as max level (unlimited, no stamina) whatever its real level.");
		numberRow("Titan form time (seconds)", work.baseTitanSeconds, false, v -> work.baseTitanSeconds = v,
				"Seconds a full stamina bar lasts in titan form at 100% drain.");
		numberRow("Transformation cooldown (s)", work.transformCooldownSeconds, true, v -> work.transformCooldownSeconds = v.intValue(),
				"Seconds until used transformations go back to zero (600 = 10 minutes).");
		numberRow("XP per transformation", work.xpPerTransformation, true, v -> work.xpPerTransformation = v.intValue(),
				"Mastery XP earned each time the shifter transforms.");
		numberRow("XP per second transformed", work.xpPerSecondTransformed, true, v -> work.xpPerSecondTransformed = v.intValue(),
				"Mastery XP earned for every second spent in titan form.");
		hintY = rowY + 4;
		labels.add(new Label(left() + 6, hintY, "Hover any option to see what it does.", Palette.TEXT_DIM));
	}

	// ---- tab: Hearts & Bonuses ---------------------------------------------------------------------------

	private void buildHearts() {
		rowY = contentTop();
		numberRow("Shifter base hearts", work.shifterBaseHearts, false, v -> work.shifterBaseHearts = v,
				"Total hearts a shifter has in human form just for being a shifter (vanilla players have 10).");
		numberRow("Hearts per mastery level", work.heartsPerMasteryLevel, false, v -> work.heartsPerMasteryLevel = v,
				"Extra hearts for each mastery level (level 9 = 9x this value).");
		numberRow("Max hearts (0 = no limit)", work.maxHearts, false, v -> work.maxHearts = v,
				"Cap on the total hearts in human form. 0 = no cap.");
		numberRow("Titan HP % per level", work.titanHpPercentPerLevel, false, v -> work.titanHpPercentPerLevel = v,
				"Extra titan HP per level, in % (10 = level 9 has +90%).");
		numberRow("Damage dealt % per level", work.damageDealtPercentPerLevel, false, v -> work.damageDealtPercentPerLevel = v,
				"Extra damage dealt per level, in % (8 = level 9 deals +72%).");
		numberRow("Damage reduction % per level", work.damageReductionPercentPerLevel, false, v -> work.damageReductionPercentPerLevel = v,
				"Less damage taken per level in human form, in % (max 10, so level 9 never goes above 90%).");

		double h0 = hearts(0), h9 = hearts(9);
		labels.add(new Label(left() + 6, rowY + 4, "Preview (human form):  level 0 = " + fmt(h0) + " hearts   |   level 9 = " + fmt(h9) + " hearts", Palette.GOLD));
	}

	private double hearts(int level) {
		double h = Math.max(0, work.shifterBaseHearts) + Math.max(0, work.heartsPerMasteryLevel) * level;
		if (work.maxHearts > 0) h = Math.min(h, work.maxHearts);
		return h;
	}

	// ---- tab: Levels (table) ------------------------------------------------------------------------------

	private int[][] columns() {
		return new int[][] {work.maxTransformsPerLevel, work.transformCostPercentPerLevel,
				work.titanDrainPercentPerLevel, work.xpToReachLevel};
	}

	private void buildLevels() {
		int left = left(), pw = panelW();
		int labelW = 34;
		int cw = (pw - 12 - labelW) / 4;
		int y0 = contentTop();
		int rowH = Math.max(13, Math.min(18, (bottomY() - y0 - 14 - 26) / (LEVELS + 1)));
		int boxH = rowH - 2;

		// header
		for (int c = 0; c < 4; c++) {
			labels.add(new Label(left + 6 + labelW + c * cw + 2, y0, COLUMNS[c], Palette.GOLD));
		}
		int[][] cols = columns();
		for (int lvl = 0; lvl < LEVELS; lvl++) {
			int y = y0 + 12 + lvl * rowH;
			labels.add(new Label(left + 6, y + (boxH - 8) / 2 + 1, "Lv " + lvl, lvl == LEVELS - 1 ? Palette.GOLD : Palette.TEXT));
			for (int c = 0; c < 4; c++) {
				final int col = c, level = lvl;
				EditBox box = intBox(left + 6 + labelW + c * cw, y, cw - 4, boxH, cols[c][lvl],
						v -> columns()[col][level] = v);
				if (c == 3 && lvl == 0) box.setEditable(false); // level 0 always needs 0 XP
				box.setTooltip(Tooltip.create(Component.literal("Level " + lvl + ": " + COLUMN_TIPS[c])));
				addRenderableWidget(box);
			}
		}

		// "All levels" row: type a value and press "v" to fill the whole column
		int ay = y0 + 12 + LEVELS * rowH + 4;
		labels.add(new Label(left + 6, ay + (boxH - 8) / 2 + 1, "All", Palette.STEEL));
		for (int c = 0; c < 4; c++) {
			final int col = c;
			EditBox all = intBox(left + 6 + labelW + c * cw, ay, cw - 4 - 16, boxH, null, v -> { });
			all.setTooltip(Tooltip.create(Component.literal("Type a value and press the button on the right to set it on all levels of this column.")));
			addRenderableWidget(all);
			addRenderableWidget(Button.builder(Component.literal("v"), b -> {
						Integer v = parseInt(all.getValue());
						if (v == null) return;
						int[] arr = columns()[col];
						for (int i = (col == 3 ? 1 : 0); i < LEVELS; i++) arr[i] = v;
						rebuildWidgets();
					})
					.bounds(left + 6 + labelW + c * cw + cw - 4 - 14, ay, 14, boxH)
					.tooltip(Tooltip.create(Component.literal("Apply to all 10 levels of this column"))).build());
		}
	}

	// ---- widget helpers -----------------------------------------------------------------------------------

	private void toggleRow(String label, boolean value, Consumer<Boolean> setter, String tip) {
		int x = left() + panelW() - 116;
		labels.add(new Label(left() + 6, rowY + 6, label, Palette.TEXT));
		CycleButton<Boolean> b = CycleButton.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.literal(tip)))
				.create(x, rowY, 110, 20, Component.empty(), (btn, v) -> setter.accept(v));
		addRenderableWidget(b);
		rowY += 24;
	}

	private void numberRow(String label, double value, boolean integer, Consumer<Double> setter, String tip) {
		int x = left() + panelW() - 116;
		labels.add(new Label(left() + 6, rowY + 6, label, Palette.TEXT));
		EditBox box = new EditBox(this.font, x, rowY, 110, 20, Component.literal(label));
		box.setMaxLength(12);
		box.setFilter(s -> integer ? s.matches("-?\\d*") : s.matches("-?\\d*(\\.\\d*)?"));
		box.setValue(fmt(value));
		box.setResponder(s -> {
			Double v = parseDouble(s);
			box.setTextColor(v == null ? 0xFF5555 : 0xE0E0E0);
			if (v != null) setter.accept(v);
		});
		box.setTooltip(Tooltip.create(Component.literal(tip)));
		addRenderableWidget(box);
		rowY += 24;
	}

	/** Integer box; {@code initial} null = empty box (the "All" row). */
	private EditBox intBox(int x, int y, int w, int h, Integer initial, Consumer<Integer> setter) {
		EditBox box = new EditBox(this.font, x, y, w, h, Component.empty());
		box.setMaxLength(10);
		box.setFilter(s -> s.matches("-?\\d*"));
		if (initial != null) box.setValue(String.valueOf(initial));
		box.setResponder(s -> {
			Integer v = parseInt(s);
			box.setTextColor(v == null ? 0xFF5555 : 0xE0E0E0);
			if (v != null) setter.accept(v);
		});
		return box;
	}

	private static Integer parseInt(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Double parseDouble(String s) {
		try {
			double d = Double.parseDouble(s.trim());
			return Double.isFinite(d) ? d : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String fmt(double v) {
		return v == Math.rint(v) && Math.abs(v) < 1e9 ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100.0) / 100.0);
	}

	// ---- actions ------------------------------------------------------------------------------------------

	private void save() {
		ShifterMasteryConfig.fixArrays(work);
		ClientPlayNetworking.send(new MasteryConfigPayloads.Save(ShifterMasteryConfig.toJson(work)));
		onClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// ---- drawing ------------------------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float a = Anim.easeOutCubic((Util.getMillis() - opened) / 250f);
		g.fillGradient(0, 0, this.width, this.height, UiDraw.alpha(0xFF04060A, 0.80f * a), UiDraw.alpha(0xFF1A0A10, 0.88f * a));
		int l = left() , pw = panelW();
		g.fill(l, TOP - 6, l + pw, bottomY() + 26, UiDraw.alpha(Palette.BG_BOTTOM, 0.85f * a));
		UiDraw.outline(g, l, TOP - 6, pw, bottomY() + 26 - (TOP - 6), UiDraw.alpha(Palette.CRIMSON, 0.9f * a));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		UiDraw.centered(g, this.font, this.title.getString(), this.width / 2, 10, Palette.GOLD);
		for (Label l : labels) UiDraw.text(g, this.font, l.text, l.x, l.y, l.color);
		// operator hint
		UiDraw.centered(g, this.font, "Operators only - changes are saved to config/los_gear_plus-mastery.json",
				this.width / 2, bottomY() - 10, Palette.TEXT_DIM);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}