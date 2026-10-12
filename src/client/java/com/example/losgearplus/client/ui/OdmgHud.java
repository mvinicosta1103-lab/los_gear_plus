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
import daot.BladeItem;
import java.lang.reflect.Method;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
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

	private static final Item AUTOMATIC_PISTOL = staticItem("cn.blockforge.royalattire.RoyalAttire", "MAUSER_RIFLE");
	private static final Method AUTO_PISTOL_AMMO = findMethod("cn.blockforge.royalattire.RoyalAttire", "getPistolTotalAmmo", ItemStack.class);
	private static final Method APG_LOADED = findMethod("daot.APGGunItem", "isLoaded", ItemStack.class);
	private static final Item MAUSER_CARTRIDGE = staticItem("cn.blockforge.royalattire.RoyalAttire", "MAUSER_CARTRIDGE");
	private static final Item APG_GUN = staticItem("daot.DannysAot", "APG_GUN");
	private static final Item APG_CARTRIDGE = staticItem("daot.DannysAot", "APG_CARTRIDGE");

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
		if (key.endsWith("limb.alert.recovered")) return Palette.GREEN; // membro recuperado
		if (key.contains(".limb.lost.")) return Palette.CRIMSON; // membro perdido
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
		renderOdmgStatus(g, mc);
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

	private static void renderOdmgStatus(GuiGraphics g, Minecraft mc) {
		if (!OdmgModeClient.isActive() || mc.player == null) return;

		Player player = mc.player;
		ItemStack gear = gearForHud(player);
		int gas = gear.isEmpty() ? 0 : gasValue(gear, "gas", "getGasFromGear");
		int maxGas = gear.isEmpty() ? 0 : gasValue(gear, "maxGas", "getMaxGasForGear");
		if (maxGas <= 0) maxGas = gas > 0 ? gas : 1;
		gas = Math.max(0, Math.min(gas, maxGas));

		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		boolean mainBlade = isUsableBlade(main);
		boolean offBlade = isUsableBlade(off);
		int bladeCount = (mainBlade ? 1 : 0) + (offBlade ? 1 : 0);

		int pistolCount = countItems(player, stack -> isAutomaticPistol(stack.getItem()));
		int pistolCartridges = countItems(player, stack -> stack.getItem() == MAUSER_CARTRIDGE);
		int apgCount = countItems(player, stack -> stack.getItem() == APG_GUN);
		int apgCartridges = countItems(player, stack -> stack.getItem() == APG_CARTRIDGE);
		String mainPistol = isAutomaticPistol(main.getItem()) ? pistolAmmo(main) + "/11" : "--";
		String offPistol = isAutomaticPistol(off.getItem()) ? pistolAmmo(off) + "/11" : "--";
		String mainApg = main.getItem() == APG_GUN ? (apgLoaded(main) ? "loaded" : "empty") : "--";
		String offApg = off.getItem() == APG_GUN ? (apgLoaded(off) ? "loaded" : "empty") : "--";

		int w = 124;
		int h = 48;
		int x = 10;
		int y = g.guiHeight() - h - 10;
		g.fill(x, y, x + w, y + h, 0x78080D14);
		UiDraw.outline(g, x, y, w, h, 0x668296A8);

		Font font = mc.font;
		// Silhueta simples de um tanque: bocal, corpo metálico e gás azul preenchendo de baixo para cima.
		int tankX = x + 5, tankY = y + 4, tankH = 18;
		g.fill(tankX + 3, tankY, tankX + 8, tankY + 2, 0xFF9BA8B4);
		g.fill(tankX + 2, tankY + 2, tankX + 9, tankY + tankH - 2, 0xFF252E38);
		g.fill(tankX, tankY + 5, tankX + 11, tankY + tankH - 3, 0xFF8997A4);
		int tankInnerX = tankX + 2, tankInnerY = tankY + 7, tankInnerW = 7, tankInnerH = 8;
		g.fill(tankInnerX, tankInnerY, tankInnerX + tankInnerW, tankInnerY + tankInnerH, 0xFF182430);
		int tankFill = Math.round(tankInnerH * (gas / (float) maxGas));
		if (tankFill > 0) g.fillGradient(tankInnerX, tankInnerY + tankInnerH - tankFill,
				tankInnerX + tankInnerW, tankInnerY + tankInnerH, 0xFF62C8FF, 0xFF1774C9);
		String gasText = compactAmount(gas) + "/" + compactAmount(maxGas);
		UiDraw.text(g, font, gasText, x + 21, y + 4, 0xFFE7F4FF);
		int barX = x + 21, barY = y + 15, barW = w - 27, barH = 3;
		g.fill(barX, barY, barX + barW, barY + barH, 0xFF182430);
		int gasFill = Math.round(barW * (gas / (float) maxGas));
		if (gasFill > 0) g.fillGradient(barX, barY, barX + gasFill, barY + barH, 0xFF62C8FF, 0xFF1774C9);
		UiDraw.outline(g, barX, barY, barW, barH, 0xAA9AC9E5);

		String pistolLine = "P" + pistolCount + " " + pistolCartridges + " " + shortPistol(mainPistol) + " " + shortPistol(offPistol);
		String apgLine = "A" + apgCount + " " + apgCartridges
				+ " M" + compactLoaded(mainApg) + " O" + compactLoaded(offApg);
		if (bladeCount > 0) {
			// O rodapé usa blades ou munição conforme o loadout atual.
			UiDraw.text(g, font, "B" + bladeCount + "/2", x + 5, y + 27, 0xFFEDEFF2);
			int bladeX = x + 29, bladeY = y + 31, bladeW = (w - 38) / 2, bladeH = 3;
			renderBladeDurability(g, main, mainBlade, bladeX, bladeY, bladeW, bladeH);
			renderBladeDurability(g, off, offBlade, bladeX + bladeW + 6, bladeY, bladeW, bladeH);
		} else {
			UiDraw.text(g, font, pistolLine, x + 5, y + 26, pistolCount > 0 ? 0xFFF1F4F8 : 0xFF8D929A);
			UiDraw.text(g, font, apgLine, x + 5, y + 37, apgCount > 0 ? 0xFFF1F4F8 : 0xFF8D929A);
		}
	}

	private static String compactAmount(int value) {
		if (value >= 1_000_000) return (value / 1_000_000) + "m";
		if (value >= 10_000) return (value / 1_000) + "k";
		return Integer.toString(value);
	}

	private static String compactLoaded(String state) {
		return "loaded".equals(state) ? "L" : "empty".equals(state) ? "E" : "--";
	}

	private static String shortPistol(String ammo) {
		return "--".equals(ammo) ? "--" : ammo.substring(0, ammo.indexOf('/'));
	}

	private static int countItems(Player player, java.util.function.Predicate<ItemStack> matches) {
		int count = 0;
		for (ItemStack stack : player.getInventory().items) if (!stack.isEmpty() && matches.test(stack)) count += stack.getCount();
		for (ItemStack stack : player.getInventory().offhand) if (!stack.isEmpty() && matches.test(stack)) count += stack.getCount();
		return count;
	}

	private static boolean isAutomaticPistol(Item item) {
		return item != null && item == AUTOMATIC_PISTOL;
	}

	private static int pistolAmmo(ItemStack stack) {
		return Math.max(0, invokeInt(AUTO_PISTOL_AMMO, stack, 0));
	}

	private static boolean apgLoaded(ItemStack stack) {
		return invokeBoolean(APG_LOADED, stack);
	}

	private static Method findMethod(String className, String name, Class<?> parameter) {
		try { return Class.forName(className).getMethod(name, parameter); }
		catch (Throwable ignored) { return null; }
	}

	private static Item staticItem(String className, String fieldName) {
		try {
			Object item = Class.forName(className).getField(fieldName).get(null);
			return item instanceof Item value ? value : null;
		} catch (Throwable ignored) { return null; }
	}

	private static boolean invokeBoolean(Method method, Object argument) {
		try { return method != null && Boolean.TRUE.equals(method.invoke(null, argument)); }
		catch (Throwable ignored) { return false; }
	}

	private static int invokeInt(Method method, Object argument, int fallback) {
		try { return method == null ? fallback : ((Number) method.invoke(null, argument)).intValue(); }
		catch (Throwable ignored) { return fallback; }
	}
	private static void renderBladeDurability(GuiGraphics g, ItemStack stack, boolean present,
			int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF2B3038);
		if (present) {
			int damage = Math.max(0, BladeItem.getBladeDamage(stack));
			int max = Math.max(1, BladeItem.MAX_DURABILITY);
			int fill = Math.round(w * (1f - Math.min(1f, damage / (float) max)));
			if (fill > 0) g.fillGradient(x, y, x + fill, y + h, 0xFFFFFFFF, 0xFFD9E0E8);
		}
		UiDraw.outline(g, x, y, w, h, 0xAA9AA4B2);
	}

	private static boolean isUsableBlade(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BladeItem
				&& BladeItem.getBladeState(stack) != BladeItem.BladeState.EMPTY;
	}

	/** Finds the DAOT gear or the Los Gear uniform/harness stack which owns the gas value. */
	private static ItemStack gearForHud(Player player) {
		try {
			Class<?> attire = Class.forName("cn.blockforge.royalattire.RoyalAttire");
			Method method = attire.getMethod("wornOdmGear", Player.class);
			Object result = method.invoke(null, player);
			if (result instanceof ItemStack stack && !stack.isEmpty()) return stack;
		} catch (Throwable ignored) {}
		try {
			Class<?> harness = Class.forName("daot.OdmHarness");
			Method method = harness.getMethod("getGear", LivingEntity.class);
			Object result = method.invoke(null, player);
			if (result instanceof ItemStack stack && !stack.isEmpty()) return stack;
		} catch (Throwable ignored) {}
		return player.getItemBySlot(EquipmentSlot.CHEST);
	}

	/** Uses Los Gear's gas bridge when present, with DAOT's public accessors as fallback. */
	private static int gasValue(ItemStack gear, String losMethod, String daotMethod) {
		Integer value = invokeGas("cn.blockforge.royalattire.item.OdmGasLink", losMethod, gear);
		if (value == null) value = invokeGas("daot.DannysAot", daotMethod, gear);
		return value == null ? 0 : Math.max(0, value);
	}

	private static Integer invokeGas(String className, String methodName, ItemStack gear) {
		try {
			Class<?> type = Class.forName(className);
			Method method = type.getMethod(methodName, ItemStack.class);
			return ((Number) method.invoke(null, gear)).intValue();
		} catch (Throwable ignored) {
			return null;
		}
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
