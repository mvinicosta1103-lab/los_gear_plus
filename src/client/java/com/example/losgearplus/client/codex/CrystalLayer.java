package com.example.losgearplus.client.codex;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.codex.CodexPayloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Camada de cristal sobre o modelo do titã (mesmo modelo, textura de cristal translúcida e brilhante), ligada nos
 * renderers dos titãs do DAOT pelo {@code DaotTitanRendererMixin}.
 *
 * <ul>
 *   <li>{@code FULL}: o corpo todo; a opacidade sobe em 4 s (a cristalização "pegando");</li>
 *   <li>{@code ARMS}: só punhos, mãos e antebraços (os punhos endurecidos dos titãs que não têm braço endurecido no
 *       DAOT); aparece e some suavemente.</li>
 * </ul>
 * Para o ARMS escondemos os ossos que não são braço só durante este desenho e restauramos em seguida. No GeckoLib
 * 4.9 {@code setHidden} também esconde os filhos, por isso o {@code setChildrenHidden(false)} logo depois.
 */
public final class CrystalLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {
	private static final ResourceLocation TEXTURE = LosGearPlus.id("textures/entity/crystal_overlay.png");
	private static final Pattern ARMS = Pattern.compile("(?i).*(hand|fist|forearm|finger).*");
	private static final float FULL_TICKS = 80.0f;
	private static final int TINT_RGB = 0xC8E6FF;
	private static final int FULL_BRIGHT = 0xF000F0;

	private record BoneState(GeoBone bone, boolean hidden, boolean childrenHidden) {}

	public CrystalLayer(GeoRenderer<T> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
			VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		CrystalClient.Fx fx = CrystalClient.get(animatable.getId());
		Minecraft mc = Minecraft.getInstance();
		if (fx == null || mc.level == null) return;

		float now = mc.level.getGameTime() + partialTick;
		boolean armsOnly = fx.mode() == CodexPayloads.CrystalFx.ARMS;
		float alpha;
		if (armsOnly) {
			if (now > fx.end()) return;
			alpha = 0.85f * Mth.clamp(Math.min((now - fx.start()) / 10.0f, (fx.end() - now) / 20.0f), 0.0f, 1.0f);
		} else {
			alpha = 0.9f * Mth.clamp((now - fx.start()) / FULL_TICKS, 0.0f, 1.0f);
		}
		if (alpha <= 0.01f) return;

		int color = ((int) (alpha * 255.0f) << 24) | TINT_RGB;
		RenderType type = RenderType.entityTranslucentEmissive(TEXTURE);
		List<BoneState> saved = armsOnly ? showOnlyArms(bakedModel) : null;
		try {
			getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
					partialTick, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
		} finally {
			if (saved != null) restore(saved);
		}
	}

	private static List<BoneState> showOnlyArms(BakedGeoModel model) {
		List<BoneState> saved = new ArrayList<>();
		for (GeoBone bone : model.topLevelBones()) walk(bone, saved);
		return saved;
	}

	private static void walk(GeoBone bone, List<BoneState> saved) {
		saved.add(new BoneState(bone, bone.isHidden(), bone.isHidingChildren()));
		bone.setHidden(!ARMS.matcher(bone.getName()).matches()); // só mãos, punhos e antebraços ficam
		bone.setChildrenHidden(false); // setHidden esconde os filhos também: desfaz, os braços são filhos de ossos escondidos
		for (GeoBone child : bone.getChildBones()) walk(child, saved);
	}

	private static void restore(List<BoneState> saved) {
		for (BoneState s : saved) {
			s.bone().setHidden(s.hidden());
			s.bone().setChildrenHidden(s.childrenHidden());
		}
	}
}
