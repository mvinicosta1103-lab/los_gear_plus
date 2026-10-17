package com.example.losgearplus.mixin.client;

import com.example.losgearplus.client.codex.CrystalLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Liga a {@link CrystalLayer} em cada renderer dos titãs de shifter do DAOT. Todos estendem {@code GeoEntityRenderer}
 * direto e têm um construtor só, {@code (EntityRendererProvider.Context)}, então a injeção é segura e a camada não
 * entra em dobro. A camada não desenha nada enquanto o titã não estiver com cristal.
 */
@Mixin(targets = {
		"daot.AttackTitanRenderer", "daot.ArmoredTitanRenderer", "daot.FemaleTitanRenderer", "daot.ColossalTitanRenderer",
		"daot.BeastTitanRenderer", "daot.WarhammerTitanRenderer", "daot.CartShifterTitanRenderer",
		"daot.TestShifterTitanRenderer", "daot.OgreShifterTitanRenderer", "daot.FoundingTitanRenderer"}, remap = false)
public abstract class DaotTitanRendererMixin {
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void los_gear_plus$crystalLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
		GeoEntityRenderer renderer = (GeoEntityRenderer) (Object) this;
		renderer.addRenderLayer(new CrystalLayer(renderer));
	}
}
