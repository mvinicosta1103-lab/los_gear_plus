package com.example.losgearplus.mixin.client;

import daot.ColossalTitanEntity;
import com.example.losgearplus.client.colossal.ColossalV2Client;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Named-namespace counterpart; DAOT still supplies the original Colossal animation resource. */
@Mixin(targets = "daot.ColossalTitanModel", remap = false)
public abstract class DaotColossalV2ModelNamedMixin {
	@Inject(method = "getModelResource(Ldaot/ColossalTitanEntity;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void losgearplus$useV2Geometry(ColossalTitanEntity titan,
			CallbackInfoReturnable<ResourceLocation> cir) {
		if (losgearplus$isV2(titan)) {
			cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("los_gear_plus", "geo/colossal_v2.geo.json"));
		}
	}

	@Inject(method = "getTextureResource(Ldaot/ColossalTitanEntity;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void losgearplus$useV2Texture(ColossalTitanEntity titan,
			CallbackInfoReturnable<ResourceLocation> cir) {
		if (losgearplus$isV2(titan)) {
			cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("los_gear_plus", "textures/entity/colossal_v2.png"));
		}
	}

	private static boolean losgearplus$isV2(ColossalTitanEntity titan) {
		UUID shifterId = titan.getShifterUUID();
		return shifterId != null && ColossalV2Client.isV2(shifterId);
	}
}
