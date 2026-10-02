package com.example.losgearplus.grip;

import com.example.losgearplus.compat.DaotBridge.Loadout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Quais itens o ODMG Mode / Grip Storage sabe guardar e empunhar:
 *  - grip (dannys-aot:blade)            -> ODM Gear, New ODM Gear, New ODM Uniform;
 *  - APG Gun (dannys-aot:apg_gun)       -> Anti-Personnel ODM Gear, New ODM Uniform;
 *  - Automatic Pistol (los_gear:mauser_rifle) -> New ODM Uniform.
 * Os dois itens de um par são sempre do MESMO tipo (2 grips, 2 APG Guns ou 2 pistolas).
 * Ids resolvidos pelo registro (sem importar classes do DAOT/los_gear).
 */
public final class HolsterWeapons {
	private HolsterWeapons() {}

	private static final ResourceLocation APG_GUN = ResourceLocation.fromNamespaceAndPath("dannys-aot", "apg_gun");
	private static final ResourceLocation PISTOL = ResourceLocation.fromNamespaceAndPath("los_gear", "mauser_rifle");

	/** Qual \"família\" de arma o jogador escolhe quando veste o New ODM Gear E o New ODM Uniform ao mesmo tempo. */
	public enum Kind { BLADES, GUNS }

	private static boolean is(ItemStack stack, ResourceLocation id) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
	}

	public static boolean isGrip(ItemStack stack) {
		return !stack.isEmpty() && GripItems.isAvailable() && stack.getItem() == GripItems.get();
	}

	public static boolean isApgGun(ItemStack stack) {
		return is(stack, APG_GUN);
	}

	public static boolean isPistol(ItemStack stack) {
		return is(stack, PISTOL);
	}

	/** APG Gun ou Automatic Pistol. */
	public static boolean isGun(ItemStack stack) {
		return isApgGun(stack) || isPistol(stack);
	}

	/** Este item pode ser empunhado/guardado por quem veste este tipo de ODM? */
	public static boolean fits(Loadout loadout, ItemStack stack) {
		return switch (loadout) {
			case GRIPS -> isGrip(stack);
			case GUNS -> isApgGun(stack);
			case EITHER -> isGrip(stack) || isGun(stack);
			case NONE -> false;
		};
	}

	public static boolean isGripItem(Item item) {
		return GripItems.isAvailable() && item == GripItems.get();
	}

	public static boolean isGunItem(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		return id.equals(APG_GUN) || id.equals(PISTOL);
	}

	/**
	 * Itens possíveis para este ODM dentro de UMA família (usado na tela de seleção).
	 * BLADES = grip; GUNS = Automatic Pistol primeiro (New ODM Uniform), depois APG Gun.
	 */
	public static List<Item> candidates(Loadout loadout, Kind kind) {
		List<Item> out = new ArrayList<>(2);
		if (kind == Kind.BLADES) {
			if ((loadout == Loadout.GRIPS || loadout == Loadout.EITHER) && GripItems.isAvailable()) out.add(GripItems.get());
			return out;
		}
		if (loadout == Loadout.EITHER) BuiltInRegistries.ITEM.getOptional(PISTOL).ifPresent(out::add);
		if (loadout == Loadout.GUNS || loadout == Loadout.EITHER) BuiltInRegistries.ITEM.getOptional(APG_GUN).ifPresent(out::add);
		return out;
	}

	/** Itens possíveis para este ODM, em ordem de prioridade (grip, APG Gun, pistola). */
	public static List<Item> candidates(Loadout loadout) {
		List<Item> out = new ArrayList<>(3);
		if (loadout == Loadout.GRIPS || loadout == Loadout.EITHER) {
			if (GripItems.isAvailable()) out.add(GripItems.get());
		}
		if (loadout == Loadout.GUNS || loadout == Loadout.EITHER) {
			BuiltInRegistries.ITEM.getOptional(APG_GUN).ifPresent(out::add);
		}
		if (loadout == Loadout.EITHER) {
			BuiltInRegistries.ITEM.getOptional(PISTOL).ifPresent(out::add);
		}
		return out;
	}
}
