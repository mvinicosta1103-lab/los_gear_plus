package com.example.losgearplus.shifter;

import com.example.losgearplus.LosGearPlusSounds;
import com.example.losgearplus.partial.PartialShifterTitanEntity;
import daot.ShifterTitan;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Toca o SFX de transformação quando um shifter vira titã.
 *
 * Detecta o momento em que o jogador passa a montar um ShifterTitan (é assim que o DAOT
 * representa o shifter transformado), então não depende dos detalhes internos da
 * sequência de mordida/raio do DAOT.
 *
 * Regras (pelas tags do jogador):
 *  - tag contendo "colossal"        -> SFX do Colossal
 *  - tag contendo "jaw" ou "cart"   -> sem SFX
 *  - qualquer outro shifter         -> SFX 15-17m
 */
public final class ShifterTransformSfx {

    /** Se true, a transformação PARCIAL (PartialShifterTitanEntity) também toca o SFX. */
    private static final boolean PLAY_ON_PARTIAL = false;

    private static final Set<UUID> SHIFTED = new HashSet<>();

    private ShifterTransformSfx() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ShifterTransformSfx::onEndTick);
        // quem entra já transformado não dispara o som; quem sai é esquecido
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            if (shiftedTitan(p) != null) SHIFTED.add(p.getUUID());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SHIFTED.remove(handler.getPlayer().getUUID()));
    }

    private static void onEndTick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            Entity titan = shiftedTitan(p);
            boolean now = titan != null;
            boolean was = SHIFTED.contains(id);
            if (now && !was) {
                SHIFTED.add(id);
                play(p, titan);
            } else if (!now && was) {
                SHIFTED.remove(id);
            }
        }
    }

    /** O titã que o jogador está "vestindo", ou null se não está transformado. */
    private static Entity shiftedTitan(ServerPlayer p) {
        if (p.isSpectator()) return null;
        Entity v = p.getVehicle();
        if (!(v instanceof ShifterTitan)) return null;
        if (!PLAY_ON_PARTIAL && v instanceof PartialShifterTitanEntity) return null;
        return v;
    }

    private static void play(ServerPlayer p, Entity titan) {
        SoundEvent sound = pickSound(p);
        if (sound == null) return;
        titan.level().playSound(null, titan.getX(), titan.getY(), titan.getZ(),
                sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static SoundEvent pickSound(ServerPlayer p) {
        Set<String> tags = p.getTags();
        if (hasTagContaining(tags, "colossal")) return LosGearPlusSounds.TRANSFORMATION_COLOSSAL;
        if (hasTagContaining(tags, "jaw") || hasTagContaining(tags, "cart")) return null;
        if (ShifterTypes.isShifter(p)) return LosGearPlusSounds.TRANSFORMATION_STANDARD;
        return null;
    }

    private static boolean hasTagContaining(Set<String> tags, String part) {
        for (String t : tags) {
            if (t.toLowerCase(Locale.ROOT).contains(part)) return true;
        }
        return false;
    }
}
