package com.example.losgearplus.compat;

import com.example.losgearplus.LosGearPlus;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ponte para o DAOT usada pelo ODMG Mode. Chama daot.DannysAot.isODMGear(Item) por
 * reflexão (nome tirado do README/mixin do projeto, NÃO conferido no jar): se o método
 * não existir ou não for público, loga um aviso e libera o modo (não trava o jogador).
 */
public final class DaotBridge {
	private DaotBridge() {}

	private static final MethodHandle IS_ODM_GEAR = find();

	private static MethodHandle find() {
		try {
			Class<?> cls = Class.forName("daot.DannysAot");
			Method m = cls.getMethod("isODMGear", Item.class);
			return MethodHandles.publicLookup().unreflect(m);
		} catch (Throwable t) {
			LosGearPlus.LOGGER.warn("DaotBridge: daot.DannysAot.isODMGear(Item) não encontrado ({}); "
					+ "o ODMG Mode não vai exigir ODM equipado.", t.toString());
			return null;
		}
	}

	/** ODM nas pernas (DAOT) ou no peito (uniformes do los_gear). */
	public static boolean wearsOdmGear(Player player) {
		if (IS_ODM_GEAR == null) return true;
		return isOdm(player.getItemBySlot(EquipmentSlot.LEGS)) || isOdm(player.getItemBySlot(EquipmentSlot.CHEST));
	}

	private static boolean isOdm(ItemStack stack) {
		if (stack.isEmpty()) return false;
		try {
			return (boolean) IS_ODM_GEAR.invoke(stack.getItem());
		} catch (Throwable t) {
			return false;
		}
	}
}
