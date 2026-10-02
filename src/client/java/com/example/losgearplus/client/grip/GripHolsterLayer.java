package com.example.losgearplus.client.grip;

import com.example.losgearplus.grip.GripBlade;
import com.example.losgearplus.grip.HolsterWeapons;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Desenha os dois grips guardados nas laterais do torso (como no blueprint) enquanto o ODMG Mode está desligado.
 *
 * SERVIDOR: o que é desenhado vem do servidor (GripHolsterSyncPayload -> ClientGripHolsters), para o jogador local
 * e para todos os outros jogadores que ele vê. O layer não adivinha nada pelas mãos nem pelo equipamento.
 *
 * ONDE FICA CADA GRIP (o servidor decide a lâmina, ver GripStorage.normalize):
 *  - grip COM lâmina (ODM Gear do DAOT): o grip inteiro (cabo + lâmina) entra na BOCA da caixa do ODM, na
 *    frente de cada caixa, preso à perna como a caixa (parâmetros g*). O cabo fica na boca e a lâmina dentro.
 *  - grip SEM lâmina (New ODM Gear recolhe a lâmina, ou grip vazio): só o cabo, na lateral do peito.
 *  - APG Gun / Automatic Pistol (Anti-Personnel ODM Gear, New ODM Uniform): a arma inteira na lateral do torso,
 *    mais embaixo (cintura/barriga), presa ao torso (parâmetros w*). Cano para baixo, como num coldre.
 *
 * O modelo é o MESMO 3D da mão: usamos o contexto THIRD_PERSON_*_HAND e o quadro de referência do
 * ItemInHandLayer (rotX -90, rotY 180, translate ±1/16, 2/16), mas ancorado no corpo em vez do braço.
 * O ponto (sideX, y, z) é onde fica o CABO do grip.
 *
 * CALIBRAÇÃO AO VIVO (sem recompilar), no jogo:
 *   /gripholster sidex 3.2      /gripholster y 2      /gripholster z -2.8
 *   /gripholster pitch 90       /gripholster yaw 0    /gripholster tilt 8      /gripholster scale 0.6
 *   /gripholster show           (imprime os valores no chat para copiar para os DEFAULTS abaixo)
 *   /gripholster reset
 *   Armas (APG/pistola) na cintura: /gripholster wx 4.8   wy 9.5   wz 0   wpitch 90   wyaw 0   wtilt 0   wscale 0.6
 * Unidades: pixels do modelo (torso: 8 de largura, 12 de altura, 4 de profundidade; y cresce para BAIXO
 * a partir do pescoço; z negativo é a frente; sideX é a distância do centro do torso).
 * pitch: 0 = lâmina para a frente (como na mão), 90 = pendurado para baixo. yaw gira em volta do próprio eixo.
 */
public class GripHolsterLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

	/** 0..6: cabo no peito. 14..20: APG Gun / Automatic Pistol na lateral do torso, na altura da cintura
	 * (wx = distância do centro do torso, wy = para baixo a partir do pescoço, wz = profundidade, negativo = frente;
	 * wpitch 90 = cano para baixo; wtilt inclina para fora; wyaw gira em volta do próprio eixo). 7..13: grip com lâmina na boca da caixa do ODM, preso à perna
	 * (gx = distância lateral a partir do quadril, gy = para baixo a partir do quadril, gz = profundidade,
	 * negativo = frente; gyaw 180 = lâmina para dentro da caixa (para trás); gpitch negativo inclina a ponta da lâmina para BAIXO (para dentro da caixa), positivo para cima;
	 * groll gira em torno do próprio comprimento). */
	public static final String[] NAMES = {"sidex", "y", "z", "pitch", "yaw", "tilt", "scale",
			"gx", "gy", "gz", "gpitch", "gyaw", "groll", "gscale",
			"wx", "wy", "wz", "wpitch", "wyaw", "wtilt", "wscale"};
	public static final float[] DEFAULTS = {4.0f, 2.0f, -2.8f, 90f, 0f, 8f, 0.7f,
			// grip com lâmina (calibrado em jogo): gx, gy, gz, gpitch, gyaw, groll, gscale
			4.0f, 2.0f, -10.5f, -23f, 180f, 0f, 0.90f,
			// APG Gun / Automatic Pistol na cintura (valores iniciais, calibre em jogo): wx, wy, wz, wpitch, wyaw, wtilt, wscale
			4.8f, 9.5f, 0.0f, 90f, 0f, 0f, 0.6f};
	/** Valores atuais (mutáveis pelo comando /gripholster). */
	public static final float[] VALUES = DEFAULTS.clone();

	private final ItemRenderer itemRenderer;

	public GripHolsterLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
							ItemRenderer itemRenderer) {
		super(parent);
		this.itemRenderer = itemRenderer;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
					   float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || player.isSpectator()) return;
		ItemStack[] stored = ClientGripHolsters.get(player.getId());
		if (stored == null) return;
		// Índice 0 = mão principal: fica no lado do braço principal do jogador.
		boolean mainIsRight = player.getMainArm() == HumanoidArm.RIGHT;

		float sideX = VALUES[0], y = VALUES[1], z = VALUES[2], pitch = VALUES[3], yaw = VALUES[4], tilt = VALUES[5],
				scale = VALUES[6];

		float gx = VALUES[7], gy = VALUES[8], gz = VALUES[9], gpitch = VALUES[10], gyaw = VALUES[11],
				groll = VALUES[12], gscale = VALUES[13];

		float wx = VALUES[14], wy = VALUES[15], wz = VALUES[16], wpitch = VALUES[17], wyaw = VALUES[18],
				wtilt = VALUES[19], wscale = VALUES[20];

		// DAOT 2.5.0: a caixa do ODM segue 90% o torso e só 10% a perna (100% usando ODM/montado).
		// O grip com lâmina precisa seguir a MESMA mistura, senão fica solto da caixa (ver OdmTankFollow).
		float tankLegWeight = OdmTankFollow.legWeight(player);

		// No modelo, o braço direito do jogador fica em x negativo e o esquerdo em x positivo.
		for (int set = 0; set < 2; set++) // set 0 = storage principal, set 1 = segundo storage (o outro tipo de arma)
		for (int side = -1; side <= 1; side += 2) {
			boolean left = side > 0;
			ItemStack stack = stored[set * 2 + ((left == mainIsRight) ? 1 : 0)];
			if (stack.isEmpty()) continue;
			ItemDisplayContext ctx = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
					: ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

			pose.pushPose();
			ItemStack toDraw;
			if (HolsterWeapons.isGun(stack)) {
				// APG Gun / Automatic Pistol: lateral do torso, na altura da cintura (presa ao torso).
				getParentModel().body.translateAndRotate(pose);
				pose.translate(side * wx / 16f, wy / 16f, wz / 16f);
				pose.mulPose(Axis.ZP.rotationDegrees(side * wtilt));
				pose.mulPose(Axis.XP.rotationDegrees(wpitch));
				pose.mulPose(Axis.YP.rotationDegrees(wyaw));
				pose.scale(wscale, wscale, wscale);
				toDraw = stack;
			} else if (GripBlade.state(stack) > 0) {
				// Grip com lâmina: na boca da caixa do ODM (presa à perna, como a caixa).
				ModelPart leg = left ? getParentModel().leftLeg : getParentModel().rightLeg;
				OdmTankFollow.applyHipFrame(pose, getParentModel().body, leg, tankLegWeight);
				pose.translate(side * gx / 16f, gy / 16f, gz / 16f);
				pose.mulPose(Axis.XP.rotationDegrees(gpitch));
				pose.mulPose(Axis.YP.rotationDegrees(gyaw));
				pose.mulPose(Axis.ZP.rotationDegrees(groll));
				pose.scale(gscale, gscale, gscale);
				toDraw = stack;
			} else {
				// Só o cabo: na lateral do peito.
				getParentModel().body.translateAndRotate(pose);
				pose.translate(side * sideX / 16f, y / 16f, z / 16f);
				pose.mulPose(Axis.ZP.rotationDegrees(side * tilt));
				pose.mulPose(Axis.XP.rotationDegrees(pitch));
				pose.mulPose(Axis.YP.rotationDegrees(yaw));
				pose.scale(scale, scale, scale);
				toDraw = GripBlade.withoutBlade(stack);
			}
			// Quadro de referência da mão (ItemInHandLayer), SEM o deslocamento de 10 px ao longo do braço:
			// a origem fica no ponto onde a mão seguraria o cabo.
			pose.mulPose(Axis.XP.rotationDegrees(-90f));
			pose.mulPose(Axis.YP.rotationDegrees(180f));
			pose.translate((left ? -1 : 1) / 16f, 0.125f, 0f);
			itemRenderer.renderStatic(player, toDraw, ctx, left, pose, buffers, player.level(), light,
					OverlayTexture.NO_OVERLAY, player.getId());
			pose.popPose();
		}
	}
}