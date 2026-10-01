package com.example.losgearplus.compat;

import cn.blockforge.royalattire.RoyalAttire;
import com.example.losgearplus.LosGearPlus;
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
}
