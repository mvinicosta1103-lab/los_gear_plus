package com.example.losgearplus.weaker;

import com.example.losgearplus.partial.PartialTitanVariant;
import com.example.losgearplus.shifter.ShifterTypes;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Armored Weaker, lado servidor: guarda a escolha Normal/Weaker (tag do jogador, persiste na morte e no relogin;
 * também dá para usar {@code /tag}) e ajuda os mixins (forma pendente no spawn, quem está na forma Weaker agora).
 */
public final class WeakerArmoredManager {
    private WeakerArmoredManager() {}

    /** Donos (UUID do jogador) que estão agora dentro de um titã Weaker. Mantido pela própria entidade. */
    public static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    /** Ligado só durante o {@code spawnArmoredTitan} do DAOT: o jogador escolheu Weaker? */
    public static final ThreadLocal<Boolean> PENDING = new ThreadLocal<>();

    public static boolean isWeakerSelected(ServerPlayer p) {
        return p.getTags().contains(WeakerArmoredConfig.ARMORED_TAG)
                && p.getTags().contains(WeakerArmoredConfig.WEAKER_TAG);
    }

    public static ShifterForm formOf(ServerPlayer p) {
        return isWeakerSelected(p) ? ShifterForm.WEAKER : ShifterForm.NORMAL;
    }

    /** Usado pelos mixins de stamina: devolve a parte "poupada" do gasto que aconteceu entre before e agora. */
    public static void keep(Map<UUID, Float> map, UUID id, Float before, float chargedFactor) {
        if (before == null) return;
        Float now = map.get(id);
        if (now == null || now >= before) return;
        map.put(id, before - (before - now) * chargedFactor);
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(ShifterFormPayload.TYPE, ShifterFormPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShifterFormSyncPayload.TYPE, ShifterFormSyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ShifterFormPayload.TYPE, (payload, context) -> {
            ServerPlayer p = context.player();
            if (payload.form() < 0) { // pedido de abrir o menu: responde com o titã atual do portador
                sync(p);
                return;
            }
            ShifterForm form = ShifterForm.byId(payload.form());
            if (form == ShifterForm.PARTIAL) return; // o Partial tem tecla própria (PartialShiftPayload)
            if (!ShifterTypes.isShifter(p)) {
                p.displayClientMessage(Component.translatable("los_gear_plus.shifter.not_shifter"), true);
                return;
            }
            boolean armored = p.getTags().contains(WeakerArmoredConfig.ARMORED_TAG);
            if (form == ShifterForm.WEAKER && !armored) {
                p.displayClientMessage(Component.translatable("los_gear_plus.armored.not_armored"), true);
                return;
            }
            if (armored) { // só o Armored tem forma gravada (Normal x Weaker); os outros shifters têm só a Normal
                if (form == ShifterForm.WEAKER) p.addTag(WeakerArmoredConfig.WEAKER_TAG);
                else p.removeTag(WeakerArmoredConfig.WEAKER_TAG);
            }
            p.displayClientMessage(Component.translatable("los_gear_plus.armored.selected",
                    Component.translatable("los_gear_plus.armored." + (form == ShifterForm.WEAKER ? "weaker" : "normal"))), true);
            sync(p);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.getPlayer().getUUID()));
    }

    private static void sync(ServerPlayer p) {
        if (ServerPlayNetworking.canSend(p, ShifterFormSyncPayload.TYPE)) {
            boolean shifter = ShifterTypes.isShifter(p);
            String titan = shifter ? PartialTitanVariant.of(p).id() : "";
            boolean canWeaker = shifter && p.getTags().contains(WeakerArmoredConfig.ARMORED_TAG);
            ServerPlayNetworking.send(p, new ShifterFormSyncPayload(titan, canWeaker, formOf(p).ordinal()));
        }
    }
}
