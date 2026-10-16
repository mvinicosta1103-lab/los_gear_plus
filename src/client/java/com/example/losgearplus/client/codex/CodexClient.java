package com.example.losgearplus.client.codex;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.client.ui.UiDraw;
import com.example.losgearplus.codex.CodexData;
import com.example.losgearplus.codex.CodexPayloads;
import com.example.losgearplus.codex.TitanCodex;
import com.example.losgearplus.codex.TitanIds;
import com.example.losgearplus.codex.TitanSkill;
import com.example.losgearplus.client.ui.Palette;
import com.mojang.blaze3d.platform.InputConstants;
import daot.ShifterTitan;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Cliente do Titan Ability Codex: tecla (padrão I), cache do estado vindo do servidor, ícones e recargas na barra. */
public final class CodexClient {
	private CodexClient() {}

	/** Textura 100% transparente: slot desligado ("-") fica sem ícone na barra do DAOT. */
	public static final ResourceLocation EMPTY_ICON = LosGearPlus.id("textures/gui/codex/empty.png");

	private static KeyMapping key;
	/** As 3 teclas das skills de stack (padrão , . /): funcionam com qualquer titã. */
	private static final KeyMapping[] STACK_KEYS = new KeyMapping[3];
	private static final Map<String, Integer> STACKS = new HashMap<>();
	private static List<String> titans = List.of();
	private static String active = "";
	private static final Map<String, String[]> LOADOUTS = new HashMap<>();
	private static final Set<String> UNLOCKED = new HashSet<>();
	private static final Map<String, Long> COOLDOWN_END = new HashMap<>();

	public static void init() {
		TitanCodex.init();
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.los_gear_plus.codex",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.los_gear_plus"));

		int[] stackDefaults = {GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH};
		for (int i = 0; i < STACK_KEYS.length; i++) {
			STACK_KEYS[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.los_gear_plus.stack_" + (i + 1),
					InputConstants.Type.KEYSYM, stackDefaults[i], "key.categories.los_gear_plus"));
		}

		ClientPlayNetworking.registerGlobalReceiver(CodexPayloads.Sync.TYPE, (payload, context) -> context.client().execute(() -> {
			accept(payload);
			Minecraft mc = context.client();
			if (payload.open() && mc.player != null && mc.screen == null) mc.setScreen(new TitanCodexScreen());
		}));
		ClientPlayNetworking.registerGlobalReceiver(CodexPayloads.Cooldown.TYPE, (payload, context) -> context.client().execute(() -> {
			Minecraft mc = context.client();
			if (mc.level != null) COOLDOWN_END.put(payload.skill(), mc.level.getGameTime() + payload.ticks());
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.player != null && mc.screen == null && ClientPlayNetworking.canSend(CodexPayloads.Request.TYPE)) {
					ClientPlayNetworking.send(new CodexPayloads.Request());
				}
			}
			for (int i = 0; i < STACK_KEYS.length; i++) {
				while (STACK_KEYS[i].consumeClick()) {
					if (mc.player != null && mc.screen == null && ClientPlayNetworking.canSend(CodexPayloads.UseStack.TYPE)) {
						ClientPlayNetworking.send(new CodexPayloads.UseStack(i));
					}
				}
			}
		});
		HudRenderCallback.EVENT.register(CodexClient::renderHud);
	}

	public static KeyMapping key() {
		return key;
	}

	private static void accept(CodexPayloads.Sync p) {
		titans = List.copyOf(p.titans());
		active = p.active();
		LOADOUTS.clear();
		for (String line : p.loadouts()) {
			String[] parts = line.split("\\|", -1);
			String[] slots = new String[CodexData.MAX_SLOTS];
			java.util.Arrays.fill(slots, CodexData.DEFAULT);
			for (int i = 1; i < parts.length && i <= CodexData.MAX_SLOTS; i++) slots[i - 1] = parts[i];
			LOADOUTS.put(parts[0], slots);
		}
		UNLOCKED.clear();
		UNLOCKED.addAll(p.unlocked());
		STACKS.clear();
		for (String line : p.stacks()) {
			String[] parts = line.split("\\|");
			if (parts.length == 2) STACKS.put(parts[0], Integer.parseInt(parts[1]));
		}
	}

	private static void clear() {
		titans = List.of();
		active = "";
		LOADOUTS.clear();
		UNLOCKED.clear();
		STACKS.clear();
		COOLDOWN_END.clear();
	}

	// ---- leitura (usada pela tela e pelo mixin do HUD) -----------------------------------------------------

	public static List<String> titans() {
		return titans;
	}

	public static String active() {
		return active;
	}

	/** Id da skill no slot (0-based): "" = padrão, "-" = vazio. */
	public static String slotSkill(String titan, int slot) {
		String[] s = LOADOUTS.get(titan);
		return s == null || slot < 0 || slot >= s.length ? CodexData.DEFAULT : s[slot];
	}

	/** Nível (0 = não tem) do stack deste titã de origem ("armored", "cart"...). */
	public static int stackLevel(String source) {
		return STACKS.getOrDefault(source, 0);
	}

	/** Nome da tecla da skill de stack i (0 a 2). */
	public static String stackKeyName(int i) {
		return i >= 0 && i < STACK_KEYS.length ? STACK_KEYS[i].getTranslatedKeyMessage().getString().toUpperCase() : String.valueOf(i + 1);
	}

	public static boolean isUnlocked(TitanSkill skill) {
		return skill.unlockedByDefault() || UNLOCKED.contains(skill.id());
	}

	/** Atualiza o cache na hora (o servidor confirma logo depois com um Sync). */
	public static void applyLocal(String titan, int slot, String skill) {
		String[] s = LOADOUTS.computeIfAbsent(titan, k -> {
			String[] a = new String[CodexData.MAX_SLOTS];
			java.util.Arrays.fill(a, CodexData.DEFAULT);
			return a;
		});
		if (slot == -1) java.util.Arrays.fill(s, CodexData.DEFAULT);
		else if (slot >= 0 && slot < s.length) s[slot] = skill;
	}

	/** Ícone que a barra de habilidades do DAOT deve mostrar neste slot, ou null para deixar o original. */
	public static ResourceLocation iconOverride(String titan, int slot) {
		Minecraft mc = Minecraft.getInstance();
		if (titan == null || mc.player == null || !titan.equals(TitanIds.of(mc.player.getVehicle()))) return null;
		String id = slotSkill(titan, slot);
		if (id.equals(CodexData.DEFAULT)) return null;
		if (id.equals(CodexData.EMPTY)) return EMPTY_ICON;
		return TitanCodex.find(id).filter(s -> s.compatibleWith(titan)).map(TitanSkill::icon).orElse(null);
	}

	/** Nome do tipo do HUD do DAOT ({@code ShifterAbilityHUD.TitanType}) -> id do Codex; null se não coberto. */
	public static String titanFromHud(Object hudType) {
		if (!(hudType instanceof Enum<?> e)) return null;
		String id = e.name().toLowerCase(java.util.Locale.ROOT);
		return TitanIds.isSupported(id) ? id : null;
	}

	// ---- HUD: barra de stacks e recargas das skills próprias ----------------------------------------------------

	private static void renderHud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
		long now = mc.level.getGameTime();
		renderStackBar(g, mc, now);
		renderSlotCooldowns(g, mc, now);
	}

	/** Os 3 slots de stack, no canto inferior direito, só enquanto você monta um titã e tem alguma skill equipada. */
	private static void renderStackBar(GuiGraphics g, Minecraft mc, long now) {
		if (!(mc.player.getVehicle() instanceof ShifterTitan)) return;
		boolean any = false;
		for (int i = 0; i < STACK_KEYS.length; i++) {
			String id = slotSkill(TitanIds.STACK, i);
			if (!id.isEmpty() && !id.equals(CodexData.EMPTY)) any = true;
		}
		if (!any) return;
		int x0 = g.guiWidth() - STACK_KEYS.length * 26 - 8;
		int y = g.guiHeight() - 30;
		for (int i = 0; i < STACK_KEYS.length; i++) {
			int x = x0 + i * 26;
			String id = slotSkill(TitanIds.STACK, i);
			g.fill(x, y, x + 22, y + 22, 0xAA0B0E13);
			UiDraw.outline(g, x, y, 22, 22, UiDraw.alpha(Palette.STEEL, 0.6f));
			UiDraw.centered(g, mc.font, stackKeyName(i), x + 11, y - 9, Palette.TEXT_DIM);
			TitanCodex.find(id).ifPresent(s -> g.blit(s.icon(), x + 2, y + 2, 18, 18, 0f, 0f, 32, 32, 32, 32));
			Long end = COOLDOWN_END.get(id);
			if (end != null && end > now) {
				g.fill(x, y, x + 22, y + 22, 0xAA000000);
				UiDraw.centered(g, mc.font, ((end - now + 19) / 20) + "s", x + 11, y + 7, 0xFFFFFFFF);
			}
		}
	}

	/** Recarga das skills próprias em cima da barra 1-9 do DAOT (mesma geometria dela). */
	private static void renderSlotCooldowns(GuiGraphics g, Minecraft mc, long now) {
		if (COOLDOWN_END.isEmpty()) return;
		String titan = TitanIds.of(mc.player.getVehicle());
		if (titan == null) return;
		int startX = (g.guiWidth() - 214) / 2;
		int y = g.guiHeight() - 26;
		for (int i = 0; i < TitanIds.slotCount(titan); i++) {
			Long end = COOLDOWN_END.get(slotSkill(titan, i));
			if (end == null || end <= now) continue;
			int x = startX + i * 24;
			g.fill(x, y, x + 22, y + 22, 0xAA000000);
			UiDraw.centered(g, mc.font, ((end - now + 19) / 20) + "s", x + 11, y + 7, 0xFFFFFFFF);
		}
	}
}
