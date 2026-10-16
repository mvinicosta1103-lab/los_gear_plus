package com.example.losgearplus.mixin;

import com.example.losgearplus.codex.stacks.StackBuffs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Stacks: aplica os buffs de dano das skills de stack (Hardened Fists, Crystal Shell...). Multiplica o dano que o
 * atacante CAUSA e o que a vítima RECEBE. Mesmo ponto e mesmo padrão do {@link LivingEntityLimbMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStackBuffMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float los_gear_plus$stackBuffs(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide) return amount;
		double mult = StackBuffs.dealt(source.getEntity()) * StackBuffs.taken(self);
		return mult == 1.0 ? amount : (float) (amount * mult);
	}
}
