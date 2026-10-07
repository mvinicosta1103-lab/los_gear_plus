package com.example.losgearplus.mixin;

import com.example.losgearplus.limb.LimbData;
import com.example.losgearplus.limb.LimbRules;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sem as duas pernas o jogador só rasteja (pose de nado/engatinhar). Roda nos dois lados. */
@Mixin(Player.class)
public abstract class PlayerPoseLimbMixin {
	@Inject(method = "updatePlayerPose", at = @At("TAIL"))
	private void los_gear_plus$crawl(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self.isPassenger() || self.isSpectator()) return;
		Pose pose = self.getPose();
		if ((pose == Pose.STANDING || pose == Pose.CROUCHING) && LimbRules.mustCrawl(LimbData.of(self))) {
			self.setPose(Pose.SWIMMING);
		}
	}
}
