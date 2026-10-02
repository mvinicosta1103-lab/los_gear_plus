package com.example.losgearplus.compat;

import com.example.losgearplus.LosGearPlus;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ponte para o DAOT usada pelo ODMG Mode e pelo Grip Storage.
 *
 * <h3>Como o DAOT guarda o ODM (mudou na versão 2.5.x)</h3>
 * <ul>
 *   <li><b>DAOT 2.5.x (com Harness):</b> o jogador veste o {@code dannys-aot:odm_harness} no slot de pernas e o
 *       ODM Gear / ODM APG fica DENTRO do harness (componente {@code odm_harness_gear}). O DAOT lê o ODM sempre
 *       por {@code daot.OdmHarness.getGear(LivingEntity)}; o ODM Gear solto no slot de pernas NÃO funciona.
 *       Sem harness vestido = sem ODM.</li>
 *   <li><b>DAOT 2.4.x (legado):</b> o ODM Gear era vestido direto no slot de pernas. Se a classe
 *       {@code daot.OdmHarness} não existir, esta ponte cai nesse comportamento antigo.</li>
 * </ul>
 *
 * Tudo é resolvido por reflexão (nomes conferidos no jar 2.5.0). Se algo não for encontrado, loga um aviso
 * e libera o modo (não trava o jogador).
 */
public final class DaotBridge {
	private DaotBridge() {}

	/** daot.DannysAot.isODMGear(Item). */
	private static final MethodHandle IS_ODM_GEAR = findIsOdmGear();
	/** daot.OdmHarness.getGear(LivingEntity) -> ItemStack. null = DAOT sem Harness (versão antiga). */
	private static final MethodHandle HARNESS_GET_GEAR = findHarness("getGear", ItemStack.class, LivingEntity.class);
	/** daot.OdmHarness.hasHarness(LivingEntity) -> boolean. */
	private static final MethodHandle HARNESS_HAS = findHarness("hasHarness", boolean.class, LivingEntity.class);

	private static MethodHandle findIsOdmGear() {
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

	private static MethodHandle findHarness(String name, Class<?> ret, Class<?> arg) {
		try {
			Class<?> cls = Class.forName("daot.OdmHarness");
			Method m = cls.getMethod(name, arg);
			if (m.getReturnType() != ret) throw new NoSuchMethodException(name + " retorna " + m.getReturnType());
			return MethodHandles.publicLookup().unreflect(m);
		} catch (ClassNotFoundException e) {
			return null; // DAOT antigo, sem Harness: modo legado (sem aviso, é esperado)
		} catch (Throwable t) {
			LosGearPlus.LOGGER.warn("DaotBridge: daot.OdmHarness.{} não encontrado ({}).", name, t.toString());
			return null;
		}
	}

	/** True se este DAOT usa o sistema de Harness (2.5.x+). */
	public static boolean usesHarness() {
		return HARNESS_GET_GEAR != null;
	}

	// ------------------------------------------------------------------ consultas

	/** O jogador está vestindo o ODM Harness (slot de pernas)? Em DAOT sem harness devolve false. */
	public static boolean wearsHarness(Player player) {
		if (HARNESS_HAS == null) return false;
		try {
			return (boolean) HARNESS_HAS.invoke((LivingEntity) player);
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * ODM Gear / APG que o DAOT considera "em uso": o que está dentro do Harness vestido (2.5.x) ou, em DAOT
	 * legado, a peça de pernas. EMPTY se não houver.
	 */
	public static ItemStack equippedGear(Player player) {
		if (HARNESS_GET_GEAR != null) {
			try {
				ItemStack gear = (ItemStack) HARNESS_GET_GEAR.invoke((LivingEntity) player);
				return gear == null ? ItemStack.EMPTY : gear;
			} catch (Throwable t) {
				return ItemStack.EMPTY;
			}
		}
		return player.getItemBySlot(EquipmentSlot.LEGS); // DAOT 2.4.x
	}

	/**
	 * Tem ODM funcional? No DAOT 2.5.x exige Harness vestido COM um ODM Gear dentro dele. Os uniformes ODM do
	 * los_gear (peito/pernas) continuam valendo por conta própria.
	 */
	public static boolean wearsOdmGear(Player player) {
		if (IS_ODM_GEAR == null) return true;
		if (isOdm(equippedGear(player))) return true;
		// Uniformes do los_gear vestidos direto (não passam pelo harness).
		return isLosOdm(player.getItemBySlot(EquipmentSlot.LEGS)) || isLosOdm(player.getItemBySlot(EquipmentSlot.CHEST));
	}

	/** Tipo de ODM vestido: NEW = los_gear (New ODM Gear / New ODM Uniform, sem scabbards), CLASSIC = ODM Gear do DAOT. */
	public enum OdmType { NONE, CLASSIC, NEW }

	public static OdmType odmType(Player player) {
		if (isLosOdm(equippedGear(player))
				|| isLosOdm(player.getItemBySlot(EquipmentSlot.LEGS))
				|| isLosOdm(player.getItemBySlot(EquipmentSlot.CHEST))) {
			return OdmType.NEW;
		}
		return wearsOdmGear(player) ? OdmType.CLASSIC : OdmType.NONE;
	}

	private static boolean isLosOdm(ItemStack stack) {
		if (stack.isEmpty()) return false;
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals("los_gear") && id.getPath().contains("odm");
	}

	private static boolean isOdm(ItemStack stack) {
		if (stack.isEmpty() || IS_ODM_GEAR == null) return false;
		try {
			return (boolean) IS_ODM_GEAR.invoke(stack.getItem());
		} catch (Throwable t) {
			return false;
		}
	}
}