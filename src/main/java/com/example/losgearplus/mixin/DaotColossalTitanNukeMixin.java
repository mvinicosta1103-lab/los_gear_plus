package com.example.losgearplus.mixin;

import com.example.losgearplus.colossal.ColossalNukeMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The real nuke of the Colossal lives in {@code daot.ColossalTitanEntity}:
 * <ul>
 *   <li>{@code onPlayerShift(Player)} (runs when the player mounts the new titan) copies the player's
 *       {@code titan_stealth} tag into the private field {@code crouchShifted}. When it is false the DAOT does the
 *       400-block knockback, the giant smoke cloud ({@code emitNukeCloud}) and the crater / terrain conversion
 *       ({@code processExplosionPhase}, 20 ticks). When true it does the small 45-block blast, no cloud, no crater.</li>
 *   <li>{@code crouchShifted} is ALSO used on dismount (instant despawn + stealth invisibility), which a normal
 *       transformation must not do. So after the explosion phases end we put the field back to false.</li>
 * </ul>
 * The tag is lent to onPlayerShift by {@link ColossalNukeMode}. The boss bar name is restored (stealth hides it).
 */
@Mixin(targets = "daot.ColossalTitanEntity", remap = false)
public abstract class DaotColossalTitanNukeMixin {

	@Shadow
	private boolean crouchShifted;

	@Shadow
	private int explosionPhase;

	/** This titan was born in "no nuke" mode: crouchShifted must be reset when the blast phases finish. */
	@Unique
	private boolean losgearplus$normalMode;

	@Inject(method = "onPlayerShift", at = @At("HEAD"), remap = false)
	private void losgearplus$beginMount(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp) ColossalNukeMode.begin(sp);
	}

	@Inject(method = "onPlayerShift", at = @At("RETURN"), remap = false)
	private void losgearplus$endMount(Player player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp)) return;
		if (ColossalNukeMode.isBorrowing(sp)) {
			losgearplus$normalMode = true;
		}
		ColossalNukeMode.end(sp);
	}

	/** With stealth the DAOT names the boss bar just "Colossal Titan"; in normal mode keep the player's name. */
	@ModifyVariable(method = "onPlayerShift", at = @At("STORE"), remap = false, require = 0)
	private String losgearplus$bossBarName(String name) {
		String player = ColossalNukeMode.noNukePlayerName();
		return player != null && "Colossal Titan".equals(name) ? "Colossal Titan - " + player : name;
	}

	@Inject(method = "processExplosionPhase", at = @At("RETURN"), remap = false)
	private void losgearplus$restoreAfterBlast(CallbackInfo ci) {
		if (losgearplus$normalMode && explosionPhase == 0) {
			crouchShifted = false; // dismount behaves like a normal (non-stealth) Colossal
			losgearplus$normalMode = false;
		}
	}
}
