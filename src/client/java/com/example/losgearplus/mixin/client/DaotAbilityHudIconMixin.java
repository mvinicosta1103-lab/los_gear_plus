package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.codex.CodexClient;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Titan Ability Codex: a barra de habilidades do DAOT desenha o ícone de cada slot por
 * {@code ShifterAbilityHUD.getIconForSlot(TitanType, slot)}. Quando o jogador equipou outra skill no slot, trocamos
 * o ícone. O tipo é um enum privado do DAOT, por isso {@code @Coerce Object}. require = 0: se o DAOT mudar isto, a
 * barra só volta a mostrar os ícones originais (o resto do Codex continua funcionando).
 */
@Mixin(targets = "daot.ShifterAbilityHUD", remap = false)
public abstract class DaotAbilityHudIconMixin {
	@Inject(method = "getIconForSlot", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private static void los_gear_plus$codexIcon(@Coerce Object titanType, int slot, CallbackInfoReturnable<ResourceLocation> cir) {
		ResourceLocation icon = CodexClient.iconOverride(CodexClient.titanFromHud(titanType), slot);
		if (icon != null) cir.setReturnValue(icon);
	}
}
