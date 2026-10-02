package com.example.losgearplus.compat;

import cn.blockforge.royalattire.RoyalAttire;
import com.example.losgearplus.grip.HolsterWeapons;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Thunder Spear / Quad Thunder Spear carregadas: passam de um funcionamento para o outro (blades <-> pistolas).
 *
 * Como o los_gear guarda a carga (RoyalAttire, jar r315), igual para grip e pistola:
 *  - componente QUAD_SPEAR_AMMO (1..4)     -> munição da quad (na pistola, 1 = última);
 *  - custom data "ThunderSpear"            -> há spear montada;
 *  - custom data "PistolPlainSpear"        -> a pistola leva uma Thunder Spear COMUM (munição 1);
 *  - custom data "QuadSpearBase"           -> grip com a quad reduzida a 1 spear (munição 0 + base);
 *  - grip com Thunder Spear comum do DAOT  -> só "ThunderSpear" (munição 0, sem base).
 *
 * Só chame com o los_gear carregado (esta classe importa RoyalAttire).
 */
public final class SpearTransfer {
	private SpearTransfer() {}

	private static final String SPEAR = "ThunderSpear";
	private static final String PLAIN = "PistolPlainSpear";
	private static final String BASE = "QuadSpearBase";
	private static final String FALLBACK = "QuadSpearFallback";

	public enum Kind { NONE, PLAIN, QUAD }

	public record Load(Kind kind, int ammo) {
		public boolean isNone() {
			return kind == Kind.NONE;
		}
	}

	public static final Load NONE = new Load(Kind.NONE, 0);

	private static boolean mounts(ItemStack weapon) {
		return HolsterWeapons.isGrip(weapon) || HolsterWeapons.isPistol(weapon);
	}

	private static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static void edit(ItemStack stack, Consumer<CompoundTag> change) {
		stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(change));
	}

	/** O que esta arma está carregando (sem alterar nada). */
	public static Load read(ItemStack weapon) {
		if (!mounts(weapon)) return NONE;
		int ammo = RoyalAttire.getQuadAmmo(weapon);
		CompoundTag t = tag(weapon);
		if (ammo > 0) {
			return t.getBoolean(PLAIN) ? new Load(Kind.PLAIN, 1) : new Load(Kind.QUAD, Math.min(ammo, 4));
		}
		if (t.getBoolean(BASE)) return new Load(Kind.QUAD, 1);
		if (HolsterWeapons.isGrip(weapon) && t.getBoolean(SPEAR)) return new Load(Kind.PLAIN, 1);
		return NONE;
	}

	public static boolean isLoaded(ItemStack weapon) {
		return !read(weapon).isNone();
	}

	/** Lê e DESCARREGA a arma (a carga passa a existir só no {@link Load} devolvido). */
	public static Load take(ItemStack weapon) {
		Load load = read(weapon);
		if (load.isNone()) return NONE;
		RoyalAttire.setQuadAmmo(weapon, 0); // também tira a marca de spear comum
		edit(weapon, t -> {
			t.remove(BASE);
			t.remove(FALLBACK);
			t.putBoolean(SPEAR, false);
		});
		return load;
	}

	/** Monta a carga numa arma descarregada, no formato certo para grip ou pistola. */
	public static void apply(ItemStack weapon, Load load) {
		if (load.isNone() || !mounts(weapon)) return;
		boolean grip = HolsterWeapons.isGrip(weapon);
		if (load.kind() == Kind.QUAD) {
			if (grip && load.ammo() <= 1) {
				RoyalAttire.setQuadAmmo(weapon, 0);
				edit(weapon, t -> {
					t.remove(FALLBACK);
					t.putBoolean(BASE, true);
					t.putBoolean(SPEAR, true);
				});
			} else {
				RoyalAttire.setQuadAmmo(weapon, load.ammo());
				edit(weapon, t -> {
					t.remove(BASE);
					t.remove(FALLBACK);
					t.putBoolean(SPEAR, true);
				});
			}
			return;
		}
		// Thunder Spear comum
		if (grip) {
			RoyalAttire.setQuadAmmo(weapon, 0);
			edit(weapon, t -> {
				t.remove(BASE);
				t.remove(FALLBACK);
				t.putBoolean(SPEAR, true);
			});
		} else {
			RoyalAttire.setQuadAmmo(weapon, 1);
			edit(weapon, t -> {
				t.remove(BASE);
				t.remove(FALLBACK);
				t.putBoolean(PLAIN, true);
				t.putBoolean(SPEAR, true);
			});
		}
	}

	/** Descarrega as duas mãos: [0] = mão principal, [1] = mão secundária. */
	public static Load[] takeFromHands(ServerPlayer player) {
		return new Load[] {take(player.getMainHandItem()), take(player.getOffhandItem())};
	}

	/**
	 * Coloca as cargas nas armas que agora estão nas mãos (mesma mão de antes). Se a arma nova já vinha carregada
	 * (ou não aceita spear), a spear volta ao inventário como item: nada se perde e nada se duplica.
	 */
	public static void place(ServerPlayer player, Load[] loads) {
		ItemStack[] hands = {player.getMainHandItem(), player.getOffhandItem()};
		for (int i = 0; i < 2; i++) {
			if (loads[i].isNone()) continue;
			if (mounts(hands[i]) && !isLoaded(hands[i])) {
				apply(hands[i], loads[i]);
			} else {
				giveBack(player, loads[i]);
			}
		}
		player.inventoryMenu.sendAllDataToRemote();
	}

	/** Devolve a carga ao inventário como item (Thunder Spear ou Quad Thunder Spear com a munição que sobrou). */
	public static void giveBack(ServerPlayer player, Load load) {
		if (load.isNone() || player.isCreative()) return; // no criativo as spears não são consumidas
		ItemStack out = ItemStack.EMPTY;
		if (load.kind() == Kind.PLAIN) {
			out = item("dannys-aot", "thunder_spear");
		} else {
			out = item("los_gear", "quad_thunder_spear");
			if (!out.isEmpty()) RoyalAttire.setQuadAmmo(out, load.ammo());
		}
		if (out.isEmpty()) return;
		if (!player.getInventory().add(out)) player.drop(out, false);
	}

	private static ItemStack item(String namespace, String path) {
		Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, path)).orElse(null);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
