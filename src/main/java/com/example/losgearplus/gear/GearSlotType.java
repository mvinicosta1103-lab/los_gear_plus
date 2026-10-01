package com.example.losgearplus.gear;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Cada slot da aba Gear. A ORDEM aqui é o índice salvo no jogador:
 * só ADICIONE novos tipos no FIM (senão os itens salvos mudam de lugar).
 * O que cada slot aceita é decidido pela tag data/los_gear_plus/tags/item/gear/<tag>.json
 */
public enum GearSlotType {
    CHEST_STRAP("chest_strap", "chest_strap", 8, 8),
    WAIST_STRAP("waist_strap", "waist_strap", 8, 26),
    LEG_STRAP("leg_strap", "leg_strap", 8, 44),
    BOOT_STRAP("boot_strap", "boot_strap", 8, 62),
    ODM_UNIT("odm_unit", "odm_unit", 98, 8),
    GAS_CANISTER("gas_canister", "gas_canister", 116, 8),
    BLADE_LEFT("blade_left", "blade", 98, 26),
    BLADE_RIGHT("blade_right", "blade", 116, 26),
    SPEAR_BAG("spear_bag", "spear_bag", 98, 44);

    private final String id;
    private final int x;
    private final int y;
    private final TagKey<Item> tag;

    GearSlotType(String id, String tagName, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.tag = TagKey.create(Registries.ITEM, LosGearPlus.id("gear/" + tagName));
    }

    public int x() { return x; }
    public int y() { return y; }
    public TagKey<Item> tag() { return tag; }

    /** Nome mostrado ao passar o mouse num slot vazio (lang: slot.los_gear_plus.<id>). */
    public Component label() {
        return Component.translatable("slot." + LosGearPlus.MOD_ID + "." + id);
    }
}