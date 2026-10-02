package com.example.losgearplus.client.grip;

import com.example.losgearplus.compat.DaotBridge;
import com.example.losgearplus.grip.GripItems;
import com.example.losgearplus.grip.GripMarker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Desenha os dois grips guardados nas laterais do torso (como no blueprint) enquanto o ODMG Mode está desligado.
 *
 * Não precisa de pacote de sincronização: com o modo ligado os grips estão nas mãos (o vanilla já sincroniza
 * as mãos de todos os jogadores), então "ODM equipado e nenhum grip na mão" = grips guardados.
 *
 * O modelo do blade é "builtin/entity" (renderer GeckoLib do DAOT) e o contexto GROUND dele quase não tem
 * rotação, então o grip sai em pé. Se o DAOT mudar isso, troque o contexto abaixo.
 *
 * AJUSTE OS NÚMEROS ABAIXO para calibrar a posição (unidades de pixel do modelo: o torso tem 8 de largura,
 * 12 de altura e 4 de profundidade; y cresce para BAIXO a partir do pescoço; z negativo é a frente).
 */
public class GripHolsterLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

	/** Distância do centro do torso até cada grip (o torso vai até 4; as armaduras engordam um pouco). */
	private static final float SIDE_X = 4.6f;
	/** Altura a partir do topo do torso (chest ~ 3 a 5). */
	private static final float Y = 4.5f;
	/** Profundidade: negativo = para a frente do torso (fora do braço, que fica ao lado). */
	private static final float Z = -1.2f;
	/** Tamanho do grip desenhado. */
	private static final float SCALE = 0.45f;
	/** Inclinação para fora (graus) para o grip "repousar" junto ao corpo em vez de ficar rígido. */
	private static final float TILT_OUT_DEGREES = 8f;

	private final ItemRenderer itemRenderer;
	private ItemStack display;

	public GripHolsterLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
			ItemRenderer itemRenderer) {
		super(parent);
		this.itemRenderer = itemRenderer;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || player.isSpectator()) return;
		Item grip = GripItems.get();
		if (grip == null) return;
		// Grips nas mãos = ODMG Mode ligado: nada guardado para desenhar.
		if (GripMarker.isBound(player.getMainHandItem()) || GripMarker.isBound(player.getOffhandItem())) return;
		if (!DaotBridge.wearsOdmGear(player)) return;

		if (display == null || display.getItem() != grip) display = new ItemStack(grip);

		// No modelo, o braço direito do jogador fica em x negativo e o esquerdo em x positivo.
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			pose.translate(side * SIDE_X / 16f, Y / 16f, Z / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees(180f + side * TILT_OUT_DEGREES)); // y do modelo é para baixo: desvira o item
			pose.scale(SCALE, SCALE, SCALE);
			itemRenderer.renderStatic(display, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers,
					player.level(), player.getId());
			pose.popPose();
		}
	}
}
