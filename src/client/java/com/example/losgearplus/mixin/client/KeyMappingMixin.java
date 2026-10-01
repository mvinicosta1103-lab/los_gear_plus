package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.mode.OdmgKeyRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Equivalente Fabric do IKeyConflictContext do Forge (que o WoF usa).
 * O vanilla só entrega o clique/estado a UM KeyMapping por tecla; aqui, quando a tecla
 * pertence a um grupo com tecla ODMG, nós mesmos distribuímos conforme o modo.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

	@Inject(method = "click", at = @At("HEAD"), cancellable = true)
	private static void losgearplus$click(InputConstants.Key key, CallbackInfo ci) {
		List<KeyMapping> group = OdmgKeyRegistry.groupWithOdmgKey(key);
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
		List<KeyMapping> group = OdmgKeyRegistry.groupWithOdmgKey(key);
		if (group == null) return;
		for (KeyMapping km : group) {
			km.setDown(held); // o filtro abaixo barra o "pressionado" das teclas suprimidas
		}
		ci.cancel();
	}

	/** Também cobre KeyMapping.setAll() (ao fechar telas / voltar o foco da janela). */
	@ModifyVariable(method = "setDown", at = @At("HEAD"), argsOnly = true)
	private boolean losgearplus$gateDown(boolean down) {
		return down && !OdmgKeyRegistry.isSuppressed((KeyMapping) (Object) this);
	}
}
