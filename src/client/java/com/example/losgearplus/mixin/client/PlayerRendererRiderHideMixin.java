package com.example.losgearplus.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import daot.ShifterTitan;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cinto de segurança no cliente: quem está montado num titã de shifter (ou seja, dentro dele) nunca é desenhado.
 * Evita o corpo humano aparecer flutuando por cima do titã (principalmente com o titã ajoelhado, quando o ponto
 * de montaria fica bem acima do corpo desenhado) em qualquer momento em que o esconderijo do DAOT falhe.
 * Não vale durante o dismount (B): {@code ShifterTitan.isDismounting()} deixa o jogador aparecer.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererRiderHideMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hideRider(AbstractClientPlayer player, float yaw, float partialTick, PoseStack poseStack,
                                         MultiBufferSource buffer, int light, CallbackInfo ci) {
        // Exceto durante o "dismount" (tecla B do DAOT): aí o jogador sai pela nuca e DEVE ser visto.
        if (player.getVehicle() instanceof ShifterTitan titan && !titan.isDismounting()) ci.cancel();
    }
}