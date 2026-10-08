package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.limb.ArmorLimbContext;
import com.example.losgearplus.client.limb.HumanLimbRender;
import com.example.losgearplus.limb.LimbClientCache;
import com.example.losgearplus.limb.LimbPart;
import com.example.losgearplus.limb.LimbState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armadura num humano com membro perdido, cortado ou crescendo: o modelo da armadura copia a pose do jogador, mas o
 * membro vanilla fica escondido (o corte é desenhado pelo {@code HumanLimbLayer}), então aqui a armadura é ajustada
 * para acompanhar (some, encolhe até o corte ou cresce junto). {@code require = 0}: se o jogo mudar esses métodos, só
 * a armadura deixa de acompanhar o corte; o resto continua funcionando.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLimbMixin {
	@Inject(method = "renderArmorPiece", at = @At("HEAD"), require = 0)
	private void los_gear_plus$armorBegin(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity,
										  EquipmentSlot slot, int light, HumanoidModel<?> model, CallbackInfo ci) {
		ArmorLimbContext.ENTITY.set(entity);
	}

	@Inject(method = "renderArmorPiece", at = @At("RETURN"), require = 0)
	private void los_gear_plus$armorEnd(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity,
										EquipmentSlot slot, int light, HumanoidModel<?> model, CallbackInfo ci) {
		ArmorLimbContext.ENTITY.remove();
	}

	/** Depois de copiar a pose do jogador e de decidir quais partes o slot mostra. */
	@Inject(method = "setPartVisibility", at = @At("TAIL"), require = 0)
	private void los_gear_plus$armorLimbs(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo ci) {
		if (!(ArmorLimbContext.ENTITY.get() instanceof AbstractClientPlayer player)) return;
		LimbState s = LimbClientCache.get(player.getUUID());
		if (s.isPristine()) return;
		boolean feet = slot == EquipmentSlot.FEET;
		HumanLimbRender.applyArmor(model.leftArm, HumanLimbRender.view(s, LimbPart.Kind.ARM, true), false);
		HumanLimbRender.applyArmor(model.rightArm, HumanLimbRender.view(s, LimbPart.Kind.ARM, false), false);
		HumanLimbRender.applyArmor(model.leftLeg, HumanLimbRender.view(s, LimbPart.Kind.LEG, true), feet);
		HumanLimbRender.applyArmor(model.rightLeg, HumanLimbRender.view(s, LimbPart.Kind.LEG, false), feet);
	}
}
