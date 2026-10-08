package com.example.losgearplus.mixin;

import daot.ShifterTitan;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jogador montado no próprio titã continua invisível quando a lista de efeitos muda.
 *
 * <p>No vanilla, sempre que um efeito é adicionado/renovado/removido (o Regeneration do Steam Heal é renovado a
 * cada ~3 s) o jogo recalcula a invisibilidade e a zera para "visível" se não houver o efeito Invisibility. Quem
 * escondia o jogador dentro do titã perde o esconderijo por um instante: o corpo humano aparece por cima do titã
 * e some de novo (pisca). Aqui guardamos o estado antes do recálculo e devolvemos depois, só para quem está
 * montado num titã de shifter. Nunca deixa ninguém invisível por conta própria (só preserva o que já estava).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRiderInvisMixin {
    @Unique
    private boolean los_gear_plus$wasInvisible;

    @Inject(method = "updateInvisibilityStatus", at = @At("HEAD"), require = 0)
    private void los_gear_plus$rememberInvisible(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Durante o dismount (B) o DAOT deixa o jogador visível de propósito: não preservar a invisibilidade.
        this.los_gear_plus$wasInvisible = self instanceof Player && self.isInvisible()
                && self.getVehicle() instanceof ShifterTitan titan && !titan.isDismounting();
    }

    @Inject(method = "updateInvisibilityStatus", at = @At("TAIL"), require = 0)
    private void los_gear_plus$keepInvisible(CallbackInfo ci) {
        if (this.los_gear_plus$wasInvisible) ((LivingEntity) (Object) this).setInvisible(true);
    }
}