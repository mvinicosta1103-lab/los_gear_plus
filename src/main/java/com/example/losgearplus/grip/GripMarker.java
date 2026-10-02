package com.example.losgearplus.grip;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Marca os grips que pertencem ao sistema de armazenamento ("bound"). Só os marcados são travados e
 * recolhidos; um grip comum que o jogador tenha por outros meios não é tocado.
 */
public final class GripMarker {
	private GripMarker() {}

	private static final String KEY = "los_gear_plus_bound_grip";

	public static ItemStack mark(ItemStack stack) {
		stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putBoolean(KEY, true)));
		return stack;
	}

	/** Devolve o grip a item comum (remove a marca e, se sobrar vazio, o componente). */
	public static ItemStack unmark(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(KEY)) return stack;
		CustomData updated = data.update(tag -> tag.remove(KEY));
		if (updated.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, updated);
		}
		return stack;
	}

	public static boolean isBound(ItemStack stack) {
		if (stack.isEmpty()) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.contains(KEY);
	}
}
