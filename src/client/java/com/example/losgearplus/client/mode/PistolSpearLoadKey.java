package com.example.losgearplus.client.mode;

import cn.blockforge.royalattire.network.QuadSpearKeyPayload;
import com.example.losgearplus.grip.GripItems;
import com.example.losgearplus.grip.HolsterWeapons;
import com.example.losgearplus.mixin.client.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Tecla V (carregar Thunder Spear / Quad Thunder Spear) com as AUTOMATIC PISTOLS nas mãos.
 *
 * Por que isto existe (conferido nos jars DAOT 2.5.0 e los_gear r315):
 *  - O servidor do los_gear JÁ sabe montar a Quad Thunder Spear e a Thunder Spear comum na pistola
 *    (RoyalAttire.handleQuadSpearToggle aceita grip OU pistola em cada mão).
 *  - Mas quem manda o V para o servidor não manda com pistolas: o cliente do DAOT só envia o pacote de carga quando
 *    há um grip (daot.BladeItem) em alguma mão, e o los_gear só reaproveita o V se você segurar o ITEM Quad Thunder
 *    Spear. Com pistola nas duas mãos, ninguém avisa o servidor, e o V não faz nada.
 *
 * Solução: quando V é apertado com pelo menos uma pistola nas mãos e nenhum grip (se houver grip, o DAOT já manda o
 * pacote dele e o los_gear trata as duas mãos), mandamos o pacote de V do próprio los_gear. Quem decide o que carregar
 * (quad primeiro, depois a spear comum) e consome do inventário continua sendo o los_gear. O disparo das spears
 * (Shift + botão de ataque/uso) também é do los_gear e não muda.
 */
public final class PistolSpearLoadKey {
	private PistolSpearLoadKey() {}

	private static final String KEY_NAME = "key.dannys-aot.thunder_spear_load";

	private static boolean wasDown;
	private static KeyMapping mapping;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PistolSpearLoadKey::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null || mc.screen != null) {
			wasDown = false;
			return;
		}
		boolean down = isDown(mc);
		boolean pressed = down && !wasDown;
		wasDown = down;
		if (!pressed || mc.player.isSpectator()) return;

		ItemStack main = mc.player.getMainHandItem();
		ItemStack off = mc.player.getOffhandItem();
		if (isGrip(main) || isGrip(off)) return; // o DAOT já manda o pacote dele
		if (!HolsterWeapons.isPistol(main) && !HolsterWeapons.isPistol(off)) return;
		if (!FabricLoader.getInstance().isModLoaded("los_gear")) return;
		Sender.send();
	}

	private static boolean isGrip(ItemStack stack) {
		return !stack.isEmpty() && GripItems.isAvailable() && stack.getItem() == GripItems.get();
	}

	/** Lê a tecla "cru", como o DAOT faz (a tecla pode ter sido remapeada pelo jogador). */
	private static boolean isDown(Minecraft mc) {
		if (mapping == null) {
			for (KeyMapping km : mc.options.keyMappings) {
				if (KEY_NAME.equals(km.getName())) {
					mapping = km;
					break;
				}
			}
		}
		InputConstants.Key key = mapping != null
				? ((KeyMappingAccessor) mapping).losgearplus$getKey()
				: InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V);
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
		}
		return mapping != null && mapping.isDown();
	}

	/** Isolado para a classe do los_gear só ser carregada quando realmente for usada. */
	private static final class Sender {
		static void send() {
			if (ClientPlayNetworking.canSend(QuadSpearKeyPayload.ID)) {
				ClientPlayNetworking.send(new QuadSpearKeyPayload());
			}
		}
	}
}
