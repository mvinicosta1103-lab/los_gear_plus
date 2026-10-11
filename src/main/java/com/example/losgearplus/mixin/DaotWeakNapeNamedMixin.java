package com.example.losgearplus.mixin;

import com.example.losgearplus.weaker.WeakArmoredTitanEntity;
import daot.ArmoredTitanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Nuca do Armored Weaker sempre atacável por lâmina. O DAOT só aceita quando {@code isNapeShattered()} (vida baixa);
 * aqui o teste dentro de {@code ArmoredTitanNapeEntity.hurt} passa a valer sempre para o Weaker, sem mexer em
 * {@code isNapeShattered} (que também liga vapor e som de ventilação). Versão Named (método "hurt");
 * {@link LosGearPlusMixinPlugin} liga só a do ambiente. require = 0: se o DAOT mudar, o jogo não cai.
 */
@Mixin(targets = "daot.ArmoredTitanNapeEntity", remap = false)
public abstract class DaotWeakNapeNamedMixin {
	@Redirect(method = "hurt",
			at = @At(value = "INVOKE", target = "Ldaot/ArmoredTitanEntity;isNapeShattered()Z", remap = false),
			remap = false, require = 0)
	private boolean losgearplus$napeOpen(ArmoredTitanEntity parent) {
		return parent instanceof WeakArmoredTitanEntity || parent.isNapeShattered();
	}
}
