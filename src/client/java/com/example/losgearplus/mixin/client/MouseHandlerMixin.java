package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.mode.OdmgHookAngleClient;
import com.example.losgearplus.client.mode.OdmgSpeedLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O Fabric não tem evento de scroll: seguramos o scroll aqui enquanto a tecla de ângulo ou de nível de velocidade está pressionada. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void losgearplus$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (window == Minecraft.getInstance().getWindow().getWindow() && (OdmgHookAngleClient.onScroll(vertical) || OdmgSpeedLevel.onScroll(vertical))) {
			ci.cancel();
		}
	}
}