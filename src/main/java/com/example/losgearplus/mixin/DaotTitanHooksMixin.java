package com.example.losgearplus.mixin;

import com.example.losgearplus.codex.stacks.CrystalTitans;
import com.example.losgearplus.codex.stacks.StackKills;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ganchos nos titãs do DAOT que SOBRESCREVEM {@code die} e {@code hurt} (os seis principais; Cart, Jaw, Ogre e
 * Founding herdam do Attack, então também passam por aqui):
 * <ul>
 *   <li>{@code die}: o titã foi derrotado: conta o abate para o sistema de stacks (o DAOT não chama o die() de
 *       LivingEntity, então os eventos de morte do Fabric nunca disparam para eles);</li>
 *   <li>{@code hurt}: titã cristalizado: cancela o dano (só o Thunder Spear estilhaça).</li>
 * </ul>
 * Nomes dos dois jeitos (mapeado/intermediário) e {@code require = 0}: se o DAOT mudar, o resto continua funcionando.
 */
@Mixin(targets = {
		"daot.AttackTitanEntity", "daot.ArmoredTitanEntity", "daot.FemaleTitanEntity",
		"daot.ColossalTitanEntity", "daot.BeastTitanEntity", "daot.WarhammerTitanEntity"}, remap = false)
public abstract class DaotTitanHooksMixin {
	@Inject(method = {"die", "method_6078"}, at = @At("HEAD"), remap = false, require = 0)
	private void los_gear_plus$onDefeat(DamageSource source, CallbackInfo ci) {
		StackKills.onTitanDefeated((LivingEntity) (Object) this, source);
	}

	@Inject(method = {"hurt", "method_5643"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void los_gear_plus$crystalGuard(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (CrystalTitans.guard((LivingEntity) (Object) this, source)) cir.setReturnValue(false);
	}
}
