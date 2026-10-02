package com.example.losgearplus.grip;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Estado da lâmina dentro do grip (dannys-aot:blade). O DAOT guarda no custom data: "BladeState" (0 = sem lâmina,
 * 1 = nova, 2..4 = lascada) e "BladeDamage" (desgaste). Aqui só mexemos em "BladeState" e guardamos o valor
 * original em outra chave enquanto a lâmina está "recolhida" (New ODM Gear), então é sempre a MESMA lâmina
 * (mesmo estado e mesmo desgaste) que volta quando o grip é puxado de novo.
 */
public final class GripBlade {
    private GripBlade() {}

    private static final String STATE = "BladeState";
    private static final String SHEATHED = "los_gear_plus_sheathed_state";
    private static final String EJECT = "EjectAnimation";
    private static final String VISUAL = "VisualState";

    /** Estado atual da lâmina (0 = sem lâmina visível). */
    public static int state(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(STATE);
    }

    public static boolean isSheathed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(SHEATHED);
    }

    /** Recolhe a lâmina (New ODM Gear não tem scabbard): o grip fica sem lâmina, o estado fica guardado. */
    public static boolean sheathe(ItemStack stack) {
        int st = state(stack);
        if (st <= 0) return false;
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putInt(SHEATHED, st);
            tag.putInt(STATE, 0);
            tag.remove(EJECT);
            tag.remove(VISUAL);
        }));
        return true;
    }

    /** Devolve a lâmina recolhida ao grip (pronta para o combate). Não faz nada se não houver nada recolhido. */
    public static ItemStack restore(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(SHEATHED)) return stack;
        int st = data.copyTag().getInt(SHEATHED);
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, d -> d.update(tag -> {
            tag.putInt(STATE, st);
            tag.remove(SHEATHED);
        }));
        return stack;
    }

    /** Cópia só para DESENHO: o grip sem lâmina (só o cabo). */
    public static ItemStack withoutBlade(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putInt(STATE, 0);
            tag.remove(EJECT);
            tag.remove(VISUAL);
        }));
        return copy;
    }
}