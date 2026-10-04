package com.example.losgearplus.compat;

import cn.blockforge.royalattire.HangeGlassesEffectHandler;
import cn.blockforge.royalattire.RoyalAttire;
import cn.blockforge.royalattire.item.HangeGlassesItem;
import com.example.losgearplus.LosGearPlus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Único ponto do seu mod que importa classes do los_gear.
 * Só chame isto depois de checar FabricLoader.isModLoaded("los_gear"),
 * senão o jogo tenta carregar classes que não existem.
 */
public final class LosGearCompat {
	private LosGearCompat() {}

	public static void init() {
		// Evite tocar em campos estáticos de RoyalAttire aqui (ex.: NEW_ODM_GEAR):
		// isso pode inicializar a classe antes do los_gear registrar os itens dele.
		LosGearPlus.LOGGER.info("los_gear detectado: integração ativa.");
	}

	/** True para o "Novo ODM" e o "Novo Uniforme ODM" do los_gear. */
	public static boolean isLosOdmGear(ItemStack stack) {
		return RoyalAttire.isOurOdmGear(stack);
	}

	/**
	 * Alcance de varredura dos óculos da Hange que o jogador está usando (48 no novo, 32 no velho),
	 * ou -1 se ele não estiver com nenhum. É o mesmo alcance que o los_gear usa para aplicar o brilho nos titans.
	 */
	public static double hangeScanRange(Player player) {
		HangeGlassesItem glasses = HangeGlassesItem.equipped(player);
		return glasses == null ? -1.0 : glasses.scanRange();
	}

	/** Mesmo critério de "é titan" que o los_gear usa para o brilho dos óculos da Hange. */
	public static boolean isTitan(Entity entity) {
		return HangeGlassesEffectHandler.isTitan(entity);
	}
}
