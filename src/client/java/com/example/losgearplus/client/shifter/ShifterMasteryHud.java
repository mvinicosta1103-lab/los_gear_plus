package com.example.losgearplus.client.shifter;

import com.example.losgearplus.client.ui.Anim;
import com.example.losgearplus.client.ui.Palette;
import com.example.losgearplus.client.ui.UiDraw;
import com.example.losgearplus.shifter.ShifterMasterySyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shifter Mastery HUD (top-left corner): big level number, 9 progress pips, XP bar, transformations used/limit
 * and the countdown until they come back. Shown only for shifters (or anyone with level > 0) and can be hidden
 * with the HUD key or /masteryhud (see {@link ShifterMasteryHudControls}).
 * DAOT's stamina bar sits on the right, so this panel does not cover it.
 */
public final class ShifterMasteryHud {
	private ShifterMasteryHud() {}

	/** Panel position (screen px from the top-left corner). Change here to move the HUD. */
	private static final int X = 6, Y = 6;
	private static final int W = 120, H = 52;
	private static final int MAX_LEVEL = 9;
	private static final long POP_MS = 450;

	private static ShifterMasterySyncPayload state;
	/** World game time (ticks) when the last payload arrived. Game time stops while the game is paused. */
	private static long receivedGameTime;

	private static float vis;
	private static float levelShown = -1f;
	private static float xpShown;
	private static int lastLevel = -1;
	private static long popBorn = -100_000L;
	private static long lastMs;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ShifterMasterySyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> accept(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			state = null;
			lastLevel = -1;
			levelShown = -1f;
			vis = 0f;
		});
		HudRenderCallback.EVENT.register(ShifterMasteryHud::render);
	}

	private static void accept(ShifterMasterySyncPayload p) {
		if (lastLevel >= 0 && p.level() != lastLevel) popBorn = Util.getMillis();
		if (levelShown < 0f) {
			levelShown = p.level();
			xpShown = fraction(p);
		}
		lastLevel = p.level();
		state = p;
		Minecraft mc = Minecraft.getInstance();
		receivedGameTime = mc.level == null ? 0L : mc.level.getGameTime();
	}

	private static float fraction(ShifterMasterySyncPayload p) {
		return p.xpNeeded() <= 0 ? 1f : Anim.clamp01(p.xp() / (float) p.xpNeeded());
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) return;
		long now = Util.getMillis();
		float dt = Math.min(0.1f, (now - lastMs) / 1000f);
		lastMs = now;

		ShifterMasterySyncPayload s = state;
		boolean show = s != null && ShifterMasteryHudConfig.isVisible() && (s.shifter() || s.level() > 0);
		vis = Anim.smooth(vis, show ? 1f : 0f, 8f, dt);
		if (s == null || vis < 0.03f) return;
		levelShown = Anim.smooth(levelShown, s.level(), 9f, dt);
		xpShown = Anim.smooth(xpShown, fraction(s), 6f, dt);

		Font font = mc.font;
		boolean master = s.level() >= MAX_LEVEL;
		boolean unlimited = s.max() < 0;
		// The server is the source of truth and resends the state every second while a cooldown runs. Between
		// packets we count down in WORLD ticks (not wall-clock time), so pausing the game never desyncs the HUD.
		long elapsed = mc.level == null ? 0L : Math.max(0L, mc.level.getGameTime() - receivedGameTime);
		long remainTicks = Math.max(0L, s.resetTicks() - elapsed);
		int used = s.used();

		int x = X - Math.round((1f - vis) * 24f);
		int y = Y;
		float a = vis;

		// Background + frame (pulsing gold glow at max level).
		g.fillGradient(x, y, x + W, y + H, UiDraw.alpha(Palette.BG_TOP, 0.90f * a), UiDraw.alpha(Palette.BG_BOTTOM, 0.92f * a));
		if (master) {
			float pulse = 0.55f + 0.45f * (float) Math.sin(now / 350.0);
			UiDraw.glow(g, x, y, W, H, UiDraw.alpha(Palette.GOLD, a), pulse);
		} else {
			UiDraw.outline(g, x, y, W, H, UiDraw.alpha(Palette.STEEL, 0.55f * a));
		}
		g.fill(x + 1, y + 1, x + 3, y + H - 1, UiDraw.alpha(master ? Palette.GOLD : Palette.CRIMSON, a)); // accent strip

		// Big level number with a "pop" when it changes.
		float t = Anim.clamp01((now - popBorn) / (float) POP_MS);
		float scale = 2.2f + (t < 1f ? 0.9f * (1f - Anim.easeOutCubic(t)) : 0f);
		int levelColor = UiDraw.mix(Palette.TEXT, Palette.GOLD, s.level() / (float) MAX_LEVEL);
		UiDraw.centeredScaled(g, font, String.valueOf(s.level()), x + 20, y + 10 - Math.round((scale - 2.2f) * 4f), scale,
				UiDraw.alpha(levelColor, a));

		// Title.
		String title = Component.translatable("los_gear_plus.mastery.title").getString();
		if (master) title += " \u00b7 " + Component.translatable("los_gear_plus.mastery.max").getString();
		UiDraw.text(g, font, title, x + 38, y + 5, UiDraw.alpha(master ? Palette.GOLD : Palette.TEXT_DIM, a));

		// 9 progress pips.
		int px = x + 38, py = y + 17, pw = 6, gap = 2;
		for (int i = 0; i < MAX_LEVEL; i++) {
			int cx = px + i * (pw + gap);
			float fill = Anim.clamp01(levelShown - i);
			int on = UiDraw.mix(Palette.CRIMSON, Palette.GOLD, i / (float) (MAX_LEVEL - 1));
			g.fill(cx, py, cx + pw, py + 5, UiDraw.alpha(Palette.STEEL, 0.22f * a));
			if (fill > 0f) g.fill(cx, py, cx + pw, py + 5, UiDraw.alpha(on, (0.35f + 0.65f * fill) * a));
		}

		// Transformations used / limit.
		String tf = unlimited
				? Component.translatable("los_gear_plus.mastery.transforms_unlimited").getString()
				: Component.translatable("los_gear_plus.mastery.transforms", used, s.max()).getString();
		boolean exhausted = !unlimited && used >= s.max();
		UiDraw.text(g, font, tf, x + 38, y + 26, UiDraw.alpha(exhausted ? Palette.CRIMSON : Palette.TEXT, a));

		// Cooldown countdown.
		if (!unlimited && used > 0) {
			long secs = remainTicks / 20L;
			String time = String.format("%d:%02d", secs / 60L, secs % 60L);
			UiDraw.text(g, font, Component.translatable("los_gear_plus.mastery.resets_in", time).getString(),
					x + 38, y + 35, UiDraw.alpha(Palette.TEXT_DIM, a));
		}

		// XP bar toward the next level (percentage on the right; full and gold at max level).
		int bx = x + 6, bw = W - 12, by = y + H - 8;
		g.fill(bx, by, bx + bw, by + 3, UiDraw.alpha(Palette.STEEL, 0.22f * a));
		int barColor = master ? Palette.GOLD : UiDraw.mix(Palette.CRIMSON, Palette.GOLD, xpShown);
		g.fill(bx, by, bx + Math.round(bw * xpShown), by + 3, UiDraw.alpha(barColor, a));
		if (!master) {
			String pct = Math.round(fraction(s) * 100f) + "%";
			UiDraw.text(g, font, pct, x + W - 6 - font.width(pct), y + 26, UiDraw.alpha(Palette.TEXT_DIM, a));
		}
	}
}
