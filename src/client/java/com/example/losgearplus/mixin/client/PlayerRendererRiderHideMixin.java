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
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererRiderHideMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void los_gear_plus$hideRider(AbstractClientPlayer player, float yaw, float partialTick, PoseStack poseStack,
                                         MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (player.getVehicle() instanceof ShifterTitan) ci.cancel();
    }
}