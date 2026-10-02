package com.example.losgearplus.grip;

import com.example.losgearplus.LosGearPlus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Qual item é o "grip" (empunhadura / handle) do DAOT.
 *
 * Conferido no jar 2.4.3: não existe item separado de grip. O "ODM Grip" é o próprio {@code dannys-aot:blade}
 * (daot.BladeItem: getName() devolve "ODM Grip", o estado da lâmina fica nele: EMPTY/FRESH/CHIPPED_1..3).
 * Por isso {@link #OVERRIDE_ID} já vem preenchido. Se um dia o id mudar, o sistema de grips fica DESLIGADO
 * (o ODMG Mode continua como antes) e o log lista os itens do DAOT para você escolher.
 */
public final class GripItems {
	private GripItems() {}

	/** Preencha aqui o id exato quando souber. Tem prioridade sobre a busca automática. */
	private static final String OVERRIDE_ID = "dannys-aot:blade";

	private static final List<String> NAMESPACES = List.of("dannys-aot", "los_gear");
	private static final List<String> CANDIDATE_PATHS = List.of("odm_grip", "grip", "odm_handle", "handle", "blade_grip");
	private static final List<String> KEYWORDS = List.of("grip", "handle");

	private static boolean resolved;
	private static Item cached;

	/** Item do grip, ou null se não foi encontrado. */
	public static Item get() {
		if (!resolved) {
			resolved = true;
			cached = find();
		}
		return cached;
	}

	public static boolean isAvailable() {
		return get() != null;
	}

	private static Item find() {
		if (OVERRIDE_ID != null) {
			ResourceLocation id = ResourceLocation.parse(OVERRIDE_ID);
			Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null) {
				LosGearPlus.LOGGER.warn("[Grips] OVERRIDE_ID '{}' não existe no registro de itens.", OVERRIDE_ID);
			} else {
				LosGearPlus.LOGGER.info("[Grips] Item do grip (override): {}", id);
			}
			return item;
		}

		for (String ns : NAMESPACES) {
			for (String path : CANDIDATE_PATHS) {
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ns, path);
				Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				if (item != null) {
					LosGearPlus.LOGGER.info("[Grips] Item do grip: {}", id);
					return item;
				}
			}
		}

		List<String> all = new ArrayList<>();
		ResourceLocation guess = null;
		for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
			if (!NAMESPACES.contains(id.getNamespace())) continue;
			all.add(id.toString());
			String p = id.getPath().toLowerCase(Locale.ROOT);
			if (guess == null && KEYWORDS.stream().anyMatch(p::contains)) guess = id;
		}
		if (guess != null) {
			LosGearPlus.LOGGER.info("[Grips] Item do grip (por palavra-chave): {}", guess);
			return BuiltInRegistries.ITEM.get(guess);
		}

		LosGearPlus.LOGGER.warn("[Grips] Nenhum item de grip encontrado: sistema de grips DESLIGADO. "
				+ "Preencha GripItems.OVERRIDE_ID com um destes ids: {}", all);
		return null;
	}
}
