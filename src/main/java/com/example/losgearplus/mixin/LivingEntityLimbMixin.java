package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Limb Dismemberment: decepa partes em golpes de lâmina/titã e limita o dano para o golpe não matar. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLimbMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float los_gear_plus$limbs(float amount, DamageSource source) {
		return LimbDamage.onHurt((LivingEntity) (Object) this, source, amount);
	}
}
