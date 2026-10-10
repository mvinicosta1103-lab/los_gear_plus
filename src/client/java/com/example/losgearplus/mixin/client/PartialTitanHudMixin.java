package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.partial.PartialColossalHud;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Esconde a hotbar, a barra de XP, a vida/fome/armadura e a vida da montaria (as fileiras de corações) enquanto o
 * jogador está dentro do Colossal parcial, como o DAOT faz nos titãs dele: no lugar entra a barra de habilidades
 * ({@link PartialColossalHud}). Todos com {@code require = 0}: se algum nome mudar numa versão do jogo, só aquele
 * elemento deixa de ser escondido (nada quebra).
 */
@Mixin(Gui.class)
public abstract class PartialTitanHudMixin {
    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hideHotbar(CallbackInfo ci) {
        if (PartialColossalHud.isActive()) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hideXpBar(CallbackInfo ci) {
        if (PartialColossalHud.isActive()) ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hideXpLevel(CallbackInfo ci) {
        if (PartialColossalHud.isActive()) ci.cancel();
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hideItemName(CallbackInfo ci) {
        if (PartialColossalHud.isActive()) ci.cancel();
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hidePlayerHealth(CallbackInfo ci) {
        if (PartialColossalHud.isActive()) ci.cancel();
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hideVehicleHealth(CallbackInfo ci) {
        if (PartialColossalHud.isActive()) ci.cancel();
    }
}
