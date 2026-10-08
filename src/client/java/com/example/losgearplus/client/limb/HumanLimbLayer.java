package com.example.losgearplus.client.limb;

import com.example.losgearplus.limb.LimbClientCache;
import com.example.losgearplus.limb.LimbPart;
import com.example.losgearplus.limb.LimbState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * Desenha o que sobrou de um braço/perna HUMANO cortado ou crescendo (ver {@link HumanLimbRender}): o membro vanilla
 * é escondido pelo {@code PlayerRendererLimbMixin} e este layer o substitui por um corte limpo no cotovelo/joelho,
 * ou por um membro pequeno que vai crescendo. Usa a pose (posição/rotação/escala) do membro vanilla, então segue
 * as animações, o agachar e o nadar. Na mão em primeira pessoa o membro cortado simplesmente não aparece.
 */
public class HumanLimbLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public HumanLimbLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
					   float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || player.isSpectator()) return;
		LimbState state = LimbClientCache.get(player.getUUID());
		if (state.isPristine()) return;

		PlayerSkin skin = player.getSkin();
		boolean slim = skin.model() == PlayerSkin.Model.SLIM;
		VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture()));
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
		PlayerModel<AbstractClientPlayer> m = getParentModel();

		limb(pose, consumer, light, overlay, state, slim, LimbPart.Kind.ARM, true, m.leftArm,
				player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		limb(pose, consumer, light, overlay, state, slim, LimbPart.Kind.ARM, false, m.rightArm,
				player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		limb(pose, consumer, light, overlay, state, slim, LimbPart.Kind.LEG, true, m.leftLeg,
				player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG));
		limb(pose, consumer, light, overlay, state, slim, LimbPart.Kind.LEG, false, m.rightLeg,
				player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG));
	}

	private static void limb(PoseStack pose, VertexConsumer consumer, int light, int overlay, LimbState state, boolean slim,
							 LimbPart.Kind kind, boolean left, ModelPart pivot, boolean overlayShown) {
		HumanLimbRender.View view = HumanLimbRender.view(state, kind, left);
		if (!view.partial()) return;
		ModelPart skin = HumanLimbRender.cut(kind, left, slim, false, view.height());
		skin.copyFrom(pivot);
		skin.render(pose, consumer, light, overlay);
		if (overlayShown) {
			ModelPart sleeve = HumanLimbRender.cut(kind, left, slim, true, view.height());
			sleeve.copyFrom(pivot);
			sleeve.render(pose, consumer, light, overlay);
		}
	}
}
