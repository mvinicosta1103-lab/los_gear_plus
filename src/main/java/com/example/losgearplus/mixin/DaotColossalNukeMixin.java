package com.example.losgearplus.mixin;

import com.example.losgearplus.colossal.ColossalNukeMode;
import com.example.losgearplus.colossal.ColossalV2Support;
import daot.network.ModNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Liga o modo "sem nuke" do Colossal nos pontos do DAOT onde o nuke é decidido.
 * Ver ColossalNukeMode para a explicação completa.
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class DaotColossalNukeMixin {

    // ---- Transformação completa: início (mordida) --------------------------------------------
    @Inject(method = "handleTitanShift", at = @At("HEAD"), remap = false)
    private static void losgearplus$nukeBeginShift(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		ColossalV2Support.ensureDaotColossalTag(player);
        ColossalNukeMode.begin(player);
    }

    @Inject(method = "handleTitanShift", at = @At("RETURN"), remap = false)
    private static void losgearplus$nukeEndShift(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
        ColossalNukeMode.end(player);
    }

    // ---- Efeito de pré-transformação (completa e Tease Shift) ---------------------------------
    @Inject(method = {"firePreshiftAndQueueSpawn", "firePreshiftOnly"}, at = @At("HEAD"), remap = false)
    private static void losgearplus$nukeBeginPreshift(ServerPlayer player, ModNetworking.ShifterType type, CallbackInfo ci) {
		ColossalV2Support.ensureDaotColossalTag(player);
        ColossalNukeMode.begin(player);
    }

    @Inject(method = {"firePreshiftAndQueueSpawn", "firePreshiftOnly"}, at = @At("RETURN"), remap = false)
    private static void losgearplus$nukeEndPreshift(ServerPlayer player, ModNetworking.ShifterType type, CallbackInfo ci) {
        ColossalNukeMode.end(player);
    }

    // ---- Nascimento do Colossal (tipo de spawn: nuke x normal) --------------------------------
    @Inject(method = "spawnColossalTitan", at = @At("HEAD"), remap = false)
    private static void losgearplus$nukeBeginSpawn(ServerPlayer player, ServerLevel level,
                                                    double x, double y, double z, float yaw, CallbackInfo ci) {
		ColossalV2Support.ensureDaotColossalTag(player);
        ColossalNukeMode.begin(player);
    }

    @Inject(method = "spawnColossalTitan", at = @At("RETURN"), remap = false)
    private static void losgearplus$nukeEndSpawn(ServerPlayer player, ServerLevel level,
                                                  double x, double y, double z, float yaw, CallbackInfo ci) {
        ColossalNukeMode.end(player);
    }

    // ---- Explosão de dano da transformação: no modo normal fica igual à dos outros titãs ------
    // Colossal: raio 20, dano 30, empurrão 3.  Outros (ex.: Attack): raio 10, dano 20, empurrão 2.
    @ModifyConstant(method = "spawnColossalTitan", constant = @Constant(doubleValue = 20.0), remap = false, require = 0)
    private static double losgearplus$normalBlastRadius(double original) {
        return ColossalNukeMode.isNoNukeCall() ? 10.0 : original;
    }

    @ModifyConstant(method = "spawnColossalTitan", constant = @Constant(floatValue = 30.0F), remap = false, require = 0)
    private static float losgearplus$normalBlastDamage(float original) {
        return ColossalNukeMode.isNoNukeCall() ? 20.0F : original;
    }

    @ModifyConstant(method = "spawnColossalTitan", constant = @Constant(doubleValue = 3.0), remap = false, require = 0)
    private static double losgearplus$normalBlastKnockback(double original) {
        return ColossalNukeMode.isNoNukeCall() ? 2.0 : original;
    }
}
