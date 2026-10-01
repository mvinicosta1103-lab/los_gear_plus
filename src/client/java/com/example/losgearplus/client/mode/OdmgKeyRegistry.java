package com.example.losgearplus.client.mode;

import com.example.losgearplus.mixin.client.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Regras do ODMG Mode para teclas:
 *  - tecla ODMG: só funciona DENTRO do modo;
 *  - qualquer outra tecla na MESMA tecla física de uma tecla ODMG: fica "fechada" DENTRO do modo;
 *  - teclas isentas (ex.: a de alternar o modo) nunca são fechadas.
 */
public final class OdmgKeyRegistry {
	private OdmgKeyRegistry() {}

	private static final Set<KeyMapping> ODMG_KEYS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<KeyMapping> EXEMPT = Collections.newSetFromMap(new IdentityHashMap<>());

	/** Cria uma tecla NOVA que só vale no ODMG Mode (registra no Fabric e marca). */
	public static KeyMapping register(KeyMapping mapping) {
		KeyBindingHelper.registerKeyBinding(mapping);
		ODMG_KEYS.add(mapping);
		return mapping;
	}

	/** Marca uma tecla JÁ registrada (ex.: de outro mod) como tecla ODMG. */
	public static void adopt(KeyMapping mapping) {
		ODMG_KEYS.add(mapping);
	}

	/** Adota pelo nome de tradução (ex.: "key.dannys-aot.hook_left"). Chame depois do CLIENT_STARTED. */
	public static boolean adoptByName(String translationKey) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return false;
		for (KeyMapping km : mc.options.keyMappings) {
			if (km.getName().equals(translationKey)) {
				ODMG_KEYS.add(km);
				return true;
			}
		}
		return false;
	}

	public static void exempt(KeyMapping mapping) {
		EXEMPT.add(mapping);
	}

	private static InputConstants.Key keyOf(KeyMapping mapping) {
		return ((KeyMappingAccessor) mapping).losgearplus$getKey();
	}

	private static boolean sharesKeyWithOdmg(KeyMapping mapping) {
		InputConstants.Key key = keyOf(mapping);
		for (KeyMapping odmg : ODMG_KEYS) {
			if (odmg != mapping && keyOf(odmg).equals(key)) return true;
		}
		return false;
	}

	/** True se esta tecla deve ser ignorada AGORA (estado do modo considerado). */
	public static boolean isSuppressed(KeyMapping mapping) {
		if (ODMG_KEYS.isEmpty() || EXEMPT.contains(mapping)) return false;
		if (ODMG_KEYS.contains(mapping)) return !OdmgModeClient.isActive();
		return OdmgModeClient.isActive() && sharesKeyWithOdmg(mapping);
	}

	/** Todas as teclas ligadas a {@code key}, ou null se nenhuma tecla ODMG usa essa tecla (vanilla segue igual). */
	public static List<KeyMapping> groupWithOdmgKey(InputConstants.Key key) {
		if (ODMG_KEYS.isEmpty()) return null;
		boolean any = false;
		for (KeyMapping odmg : ODMG_KEYS) {
			if (keyOf(odmg).equals(key)) {
				any = true;
				break;
			}
		}
		if (!any) return null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return null;
		List<KeyMapping> group = new ArrayList<>();
		for (KeyMapping km : mc.options.keyMappings) {
			if (keyOf(km).equals(key)) group.add(km);
		}
		return group;
	}

	/** Ao trocar de modo: solta e zera cliques pendentes para não "vazar" ação nem travar tecla. */
	public static void releaseAffected() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return;
		for (KeyMapping km : mc.options.keyMappings) {
			if (ODMG_KEYS.contains(km) || sharesKeyWithOdmg(km)) {
				((KeyMappingAccessor) km).losgearplus$setClickCount(0);
				km.setDown(false);
			}
		}
	}
}
