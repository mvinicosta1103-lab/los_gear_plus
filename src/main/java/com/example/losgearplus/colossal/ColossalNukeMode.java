package com.example.losgearplus.colossal;

import daot.PowerAuthority;
import daot.TitanPowerType;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Colossal shifter transformation mode: WITH nuke (DAOT default) or NORMAL (no nuke).
 *
 * DAOT decides the nuke in four places (ModNetworking), all of them checking the "titan_stealth" tag:
 *   handleTitanShift (freezes the player), firePreshiftAndQueueSpawn / firePreshiftOnly (client nuke effect)
 *   and spawnColossalTitan (spawn type 6 = nuke, 0 = normal).
 * For a player who chose NORMAL, DaotColossalNukeMixin lends "titan_stealth" only while those calls run and
 * takes it back right after, so real Stealth (DAOT's N key) is never toggled and its other effects stay intact.
 *
 * The choice is stored in the player's "los_colossal_nuke_off" tag (survives relog/death).
 *
 * Also covered: ColossalTitanEntity.onPlayerShift (runs a few ticks AFTER the spawn, when the player mounts the
 * titan). THAT is where DAOT really decides the giant blast/smoke/crater (field crouchShifted), see
 * DaotColossalTitanNukeMixin.
 *
 * The loan is reference-counted: the tag is only removed when the OUTERMOST call returns.
 */
public final class ColossalNukeMode {

    public static final String OFF_TAG = "los_colossal_nuke_off";
    private static final String STEALTH_TAG = "titan_stealth";

    /** Players currently holding the borrowed titan_stealth tag -> nesting depth. */
    private static final Map<UUID, Integer> DEPTH = new HashMap<>();
    private static boolean noNukeCall;
    private static String noNukeName;

    private ColossalNukeMode() {}

    // ----------------------------------------------------------------- state

    /** Colossal shifter (or variation: any tag containing "colossal"). */
    public static boolean isColossal(ServerPlayer p) {
        if (PowerAuthority.hasShifter(p, TitanPowerType.COLOSSAL)) return true;
        for (String t : p.getTags()) {
            if (t.toLowerCase(Locale.ROOT).contains("colossal")) return true;
        }
        return false;
    }

    /** true = the player chose to transform with the nuke (default). */
    public static boolean isNukeOn(ServerPlayer p) {
        return !p.getTags().contains(OFF_TAG);
    }

    // ------------------------------------------------- used by the DAOT mixin

    /** Called at the start of each DAOT method that decides the nuke. */
    public static void begin(ServerPlayer p) {
        if (isNukeOn(p) || !isColossal(p)) return;
        UUID id = p.getUUID();
        Integer depth = DEPTH.get(id);
        if (depth == null) {
            if (p.getTags().contains(STEALTH_TAG)) return; // real stealth already active: leave it alone
            p.addTag(STEALTH_TAG);
            depth = 0;
        }
        DEPTH.put(id, depth + 1);
        noNukeCall = true;
        noNukeName = p.getName().getString();
    }

    /** Called at the end of the same method. */
    public static void end(ServerPlayer p) {
        UUID id = p.getUUID();
        Integer depth = DEPTH.get(id);
        if (depth != null) {
            if (depth <= 1) {
                DEPTH.remove(id);
                p.removeTag(STEALTH_TAG);
            } else {
                DEPTH.put(id, depth - 1);
            }
        }
        noNukeCall = !DEPTH.isEmpty();
        if (!noNukeCall) noNukeName = null;
    }

    /** true if this player is currently holding the borrowed titan_stealth tag (a "no nuke" call is running). */
    public static boolean isBorrowing(ServerPlayer p) {
        return DEPTH.containsKey(p.getUUID());
    }

    /** Name of the player whose DAOT call is running in "no nuke" mode (null otherwise). */
    public static String noNukePlayerName() {
        return noNukeCall ? noNukeName : null;
    }

    /** true while a DAOT call is running in "no nuke" mode. */
    public static boolean isNoNukeCall() {
        return noNukeCall;
    }

    // --------------------------------------------------------------- network

    public static void init() {
        PayloadTypeRegistry.playC2S().register(ColossalNukePayload.TYPE, ColossalNukePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ColossalNukeSyncPayload.TYPE, ColossalNukeSyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ColossalNukePayload.TYPE, (payload, context) -> {
            ServerPlayer p = context.player();
            context.server().execute(() -> handle(p, payload.mode()));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DEPTH.remove(handler.getPlayer().getUUID()));

        // safety net: if a DAOT method throws midway, give the tag back at the end of the tick
        ServerTickEvents.END_SERVER_TICK.register(ColossalNukeMode::cleanup);
    }

    private static void handle(ServerPlayer p, int mode) {
        if (!isColossal(p)) {
            sync(p);
            return;
        }
        if (mode == 0) {
            p.addTag(OFF_TAG);
        } else if (mode == 1) {
            p.removeTag(OFF_TAG);
        }
        sync(p);
        if (mode == 0 || mode == 1) {
            p.displayClientMessage(Component.translatable(
                    mode == 1 ? "los_gear_plus.colossal_nuke.set_on" : "los_gear_plus.colossal_nuke.set_off"), true);
        }
    }

    private static void sync(ServerPlayer p) {
        if (ServerPlayNetworking.canSend(p, ColossalNukeSyncPayload.TYPE)) {
            ServerPlayNetworking.send(p, new ColossalNukeSyncPayload(isColossal(p), isNukeOn(p)));
        }
    }

    private static void cleanup(MinecraftServer server) {
        if (DEPTH.isEmpty()) return;
        for (UUID id : DEPTH.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.removeTag(STEALTH_TAG);
        }
        DEPTH.clear();
        noNukeCall = false;
        noNukeName = null;
    }
}
