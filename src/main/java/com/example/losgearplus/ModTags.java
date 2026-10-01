package com.example.losgearplus;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
	private ModTags() {}

	/**
	 * Itens listados em data/los_gear_plus/tags/item/odm_gear.json passam a ser
	 * reconhecidos como "ODM gear" pelo DAOT (ver DannysAotOdmMixin).
	 */
	public static final TagKey<Item> ODM_GEAR = TagKey.create(Registries.ITEM, LosGearPlus.id("odm_gear"));
}
