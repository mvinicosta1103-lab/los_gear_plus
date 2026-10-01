package com.example.losgearplus.client.mode;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.mixin.client.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Regras do ODMG Mode para TODAS as teclas do jogo. Cada tecla tem um papel:
 *
 *  FREE   - sempre funciona (inventário, slots da hotbar, trocar de mão, teclas do próprio mod);
 *  GEAR   - teclas do DAOT / los_gear: só funcionam COM o modo ligado (e nunca são travadas por ele);
 *  LOCKED - todas as outras: funcionam normalmente com o modo desligado e TRAVAM com o modo ligado.
 */
public final class OdmgKeyRegistry {
	private OdmgKeyRegistry() {}

	/** true = andar/pular/agachar/correr também travam dentro do modo. */
	private static final boolean LOCK_MOVEMENT = false;

	/** Nome do seu próprio mod: as teclas dele (alternar modo, abrir gear...) ficam sempre livres. */
	private static final String OWN_MOD = "los_gear_plus";

	/** Pedaços de nome/categoria que identificam as teclas do DAOT e do los_gear. */
	private static final List<String> GEAR_MARKERS = List.of("dannys", "daot", "los_gear", "losgear", "royalattire");

	private static final Set<String> ALWAYS_FREE = Set.of(
			"key.inventory", "key.swapOffhand",
			"key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5",
			"key.hotbar.6", "key.hotbar.7", "key.hotbar.8", "key.hotbar.9");

	private static final Set<String> MOVEMENT = Set.of(
			"key.forward", "key.back", "key.left", "key.right", "key.jump", "key.sneak", "key.sprint");

	private enum Role { FREE, GEAR, LOCKED }

	private static final Set<KeyMapping> EXEMPT = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<KeyMapping, Role> ROLES = new IdentityHashMap<>();

	/** Marca uma tecla como sempre livre (usada pela tecla de alternar o modo). */
	public static void exempt(KeyMapping mapping) {
		EXEMPT.add(mapping);
		ROLES.remove(mapping);
	}

	private static Role classify(KeyMapping mapping) {
		String name = mapping.getName();
		String text = (name + " " + mapping.getCategory()).toLowerCase(Locale.ROOT);
		if (EXEMPT.contains(mapping) || ALWAYS_FREE.contains(name)
				|| (!LOCK_MOVEMENT && MOVEMENT.contains(name)) || text.contains(OWN_MOD)) {
			return Role.FREE;
		}
		for (String marker : GEAR_MARKERS) {
			if (text.contains(marker)) return Role.GEAR;
		}
		return Role.LOCKED;
	}

	private static Role roleOf(KeyMapping mapping) {
		return ROLES.computeIfAbsent(mapping, OdmgKeyRegistry::classify);
	}

	private static InputConstants.Key keyOf(KeyMapping mapping) {
		return ((KeyMappingAccessor) mapping).losgearplus$getKey();
	}

	/** True se esta tecla deve ser ignorada AGORA (papel + estado do modo). */
	public static boolean isSuppressed(KeyMapping mapping) {
		return switch (roleOf(mapping)) {
			case GEAR -> !OdmgModeClient.isActive();
			case LOCKED -> OdmgModeClient.isActive();
			case FREE -> false;
		};
	}

	/**
	 * Todas as teclas ligadas a {@code key}, mas só quando há conflito real (uma tecla GEAR dividindo a tecla
	 * física com outra). O vanilla entrega o clique a UM só KeyMapping por tecla, então nesse caso distribuímos
	 * nós mesmos. Sem conflito retorna null e o vanilla segue igual.
	 */
	public static List<KeyMapping> groupToRoute(InputConstants.Key key) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return null;
		List<KeyMapping> group = null;
		boolean hasGear = false;
		boolean hasOther = false;
		for (KeyMapping km : mc.options.keyMappings) {
			if (!keyOf(km).equals(key)) continue;
			if (group == null) group = new ArrayList<>();
			group.add(km);
			if (roleOf(km) == Role.GEAR) hasGear = true;
			else hasOther = true;
		}
		return hasGear && hasOther ? group : null;
	}

	/** Ao trocar de modo: solta tudo que muda de estado e zera cliques pendentes (nada vaza, nada trava). */
	public static void releaseAffected() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return;
		for (KeyMapping km : mc.options.keyMappings) {
			if (roleOf(km) != Role.FREE) {
				((KeyMappingAccessor) km).losgearplus$setClickCount(0);
				km.setDown(false);
			}
		}
	}

	/** Lista no log o papel de cada tecla: serve para conferir se o DAOT/los_gear foram reconhecidos. */
	public static void logRoles() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return;
		LosGearPlus.LOGGER.info("[ODMG] Papel de cada tecla (FREE = sempre livre | GEAR = só no modo | LOCKED = trava no modo):");
		for (KeyMapping km : mc.options.keyMappings) {
			LosGearPlus.LOGGER.info("[ODMG]   {} | {} | {}", roleOf(km), km.getName(), km.getCategory());
		}
	}
}
