package com.example.losgearplus.mixin;

import com.example.losgearplus.codex.stacks.CrystalTitans;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rede de segurança do titã cristalizado: ele não pode ser removido ({@code discard}/{@code kill}) enquanto for de
 * cristal, nem pelo sumiço do DAOT (o titã vazio evapora em 60 s). Só vale para esses dois motivos de remoção: descarregar
 * o chunk, trocar de dimensão etc. continuam normais. Quem estilhaça ({@code CrystalTitans.shatter}) tira o estado de
 * cristal ANTES de remover.
 */
@Mixin(Entity.class)
public abstract class EntityRemoveMixin {
	@Inject(method = "remove", at = @At("HEAD"), cancellable = true)
	private void los_gear_plus$keepCrystal(Entity.RemovalReason reason, CallbackInfo ci) {
		if ((reason == Entity.RemovalReason.DISCARDED || reason == Entity.RemovalReason.KILLED)
				&& CrystalTitans.isCrystal((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
