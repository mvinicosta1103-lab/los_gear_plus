package com.example.losgearplus.mixin.client;

import com.example.losgearplus.partial.PartialColossalTitanEntity;
import com.example.losgearplus.partial.PartialShiftConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Câmera em 3ª pessoa mais afastada (e um pouco acima) enquanto o jogador está no Colossal parcial: o modelo tem ~27
 * blocos, então os 4 blocos do vanilla deixavam a câmera colada no peito do titã. Mesma técnica do DAOT
 * (move a câmera para trás no fim do {@code setup}); {@code getMaxZoom} impede de atravessar blocos.
 */
@Mixin(Camera.class)
public abstract class PartialColossalCameraMixin {
    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Shadow
    protected abstract float getMaxZoom(float maxZoom);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void los_gear_plus$colossalCamera(BlockGetter level, Entity focused, boolean thirdPerson, boolean mirrored,
                                              float partialTick, CallbackInfo ci) {
        if (!thirdPerson) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof PartialColossalTitanEntity titan)
                || titan.isDismounting()) {
            return;
        }
        float extra = getMaxZoom(PartialShiftConfig.COLOSSAL_CAMERA_DISTANCE - 4.0f);
        move(-extra, PartialShiftConfig.COLOSSAL_CAMERA_UP, 0.0f);
    }
}
