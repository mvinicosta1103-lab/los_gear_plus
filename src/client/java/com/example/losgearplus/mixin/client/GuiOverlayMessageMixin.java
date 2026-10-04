package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.ui.OdmgHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redireciona toda mensagem da action bar do próprio mod (chave "los_gear_plus.*", inclusive as enviadas pelo
 * servidor) para o banner animado do OdmgHud. Mensagens de outros mods e do vanilla não são afetadas.
 */
@Mixin(Gui.class)
public abstract class GuiOverlayMessageMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void losgearplus$toToast(Component message, boolean animate, CallbackInfo ci) {
		if (message.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("los_gear_plus.")) {
			OdmgHud.toast(message);
			ci.cancel();
		}
	}
}
