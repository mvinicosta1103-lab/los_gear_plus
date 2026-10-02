package com.example.losgearplus.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.example.losgearplus.client.mode.OdmgKeyRegistry;

/**
 * Equivalente Fabric do IKeyConflictContext do Forge (que o WoF usa): barra as teclas
 * conforme o ODMG Mode (ver OdmgKeyRegistry para os papéis).
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

	@Shadow
	private int clickCount;

	/** Tecla suprimida não entrega "cliques" (e descarta os que já tinha acumulado). */
	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void losgearplus$gateClick(CallbackInfoReturnable<Boolean> cir) {
		if (OdmgKeyRegistry.isSuppressed((KeyMapping) (Object) this)) {
			this.clickCount = 0;
			cir.setReturnValue(false);
		}
	}

	/** Barra o estado "pressionado"; também cobre KeyMapping.setAll() (fechar tela / voltar o foco da janela). */
	@ModifyVariable(method = "setDown", at = @At("HEAD"), argsOnly = true)
	private boolean losgearplus$gateDown(boolean down) {
		return down && !OdmgKeyRegistry.isSuppressed((KeyMapping) (Object) this);
	}

	/** Só quando há conflito de tecla física: o vanilla entrega o clique a um único KeyMapping. */
	@Inject(method = "click", at = @At("HEAD"), cancellable = true)
	private static void losgearplus$click(InputConstants.Key key, CallbackInfo ci) {
		List<KeyMapping> group = OdmgKeyRegistry.groupToRoute(key);
		if (group == null) return;
		for (KeyMapping km : group) {
			if (!OdmgKeyRegistry.isSuppressed(km)) {
				KeyMappingAccessor acc = (KeyMappingAccessor) km;
				acc.losgearplus$setClickCount(acc.losgearplus$getClickCount() + 1);
			}
		}
		ci.cancel();
	}

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private static void losgearplus$set(InputConstants.Key key, boolean held, CallbackInfo ci) {
		List<KeyMapping> group = OdmgKeyRegistry.groupToRoute(key);
		if (group == null) return;
		for (KeyMapping km : group) {
			km.setDown(held); // o filtro acima barra o "pressionado" das teclas suprimidas
		}
		ci.cancel();
	}
}
