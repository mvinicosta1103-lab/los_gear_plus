package com.example.losgearplus.mixin.client;

import com.example.losgearplus.limb.LimbClientCache;
import com.example.losgearplus.limb.LimbState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Humano: encurta/esconde braços e pernas conforme o estado. O pivô do braço é o ombro e o da perna é o quadril,
 * então escalar Y encolhe o membro em direção ao corpo (cotovelo/joelho em 0.5). Roda também na mão em primeira
 * pessoa. Os valores são SEMPRE reescritos (inclusive 1.0) porque o modelo é compartilhado entre jogadores.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererLimbMixin {
	@Inject(method = "setModelProperties", at = @At("TAIL"))
	private void los_gear_plus$limbs(AbstractClientPlayer player, CallbackInfo ci) {
		PlayerModel<AbstractClientPlayer> m = ((PlayerRenderer) (Object) this).getModel();
		LimbState s = LimbClientCache.get(player.getUUID());
		apply(m.leftArm, m.leftSleeve, s.armLength(true));
		apply(m.rightArm, m.rightSleeve, s.armLength(false));
		apply(m.leftLeg, m.leftPants, s.legLength(true));
		apply(m.rightLeg, m.rightPants, s.legLength(false));
	}

	private static void apply(ModelPart part, ModelPart overlay, float length) {
		float scale = length <= 0f ? 0.001f : Math.min(1f, length);
		part.yScale = scale;
		overlay.yScale = scale;
		if (length <= 0f) {
			part.visible = false;
			overlay.visible = false;
		}
	}
}
