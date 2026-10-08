package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.limb.HumanLimbRender;
import com.example.losgearplus.limb.LimbClientCache;
import com.example.losgearplus.limb.LimbPart;
import com.example.losgearplus.limb.LimbState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Humano: braço/perna perdido SOME do corpo (modelo, manga/calça e armadura); antebraço/canela perdido vira corte limpo
 * no cotovelo/joelho e membro crescendo começa pequeno e cresce de forma uniforme. As duas últimas formas são
 * desenhadas pelo {@code HumanLimbLayer}; aqui só se esconde/escala o membro vanilla (ver {@link HumanLimbRender}).
 * Roda também na mão em primeira pessoa. Os valores são SEMPRE reescritos (inclusive 1.0) porque o modelo é
 * compartilhado entre jogadores.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererLimbMixin {
	@Inject(method = "setModelProperties", at = @At("TAIL"))
	private void los_gear_plus$limbs(AbstractClientPlayer player, CallbackInfo ci) {
		PlayerModel<AbstractClientPlayer> m = ((PlayerRenderer) (Object) this).getModel();
		LimbState s = LimbClientCache.get(player.getUUID());
		HumanLimbRender.applyBody(m.leftArm, m.leftSleeve, HumanLimbRender.view(s, LimbPart.Kind.ARM, true));
		HumanLimbRender.applyBody(m.rightArm, m.rightSleeve, HumanLimbRender.view(s, LimbPart.Kind.ARM, false));
		HumanLimbRender.applyBody(m.leftLeg, m.leftPants, HumanLimbRender.view(s, LimbPart.Kind.LEG, true));
		HumanLimbRender.applyBody(m.rightLeg, m.rightPants, HumanLimbRender.view(s, LimbPart.Kind.LEG, false));
	}
}
