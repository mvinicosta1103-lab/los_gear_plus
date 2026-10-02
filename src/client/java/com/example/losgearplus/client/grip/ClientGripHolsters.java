package com.example.losgearplus.client.grip;

import com.example.losgearplus.grip.GripHolsterSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * Estado dos grips guardados de TODOS os jogadores que este cliente está vendo, vindo do servidor
 * (GripHolsterSyncPayload). A chave é o id de entidade; [0] = mão principal, [1] = mão secundária, [2]/[3] = o mesmo para o segundo storage.
 */
public final class ClientGripHolsters {
    private ClientGripHolsters() {}

    private static final Map<Integer, ItemStack[]> STATE = new HashMap<>();

    public static void accept(GripHolsterSyncPayload payload) {
        if (payload.main().isEmpty() && payload.off().isEmpty() && payload.altMain().isEmpty() && payload.altOff().isEmpty()) {
            STATE.remove(payload.entityId());
        } else {
            STATE.put(payload.entityId(), new ItemStack[] {payload.main(), payload.off(), payload.altMain(), payload.altOff()});
        }
    }

    /** Grips guardados do jogador, ou null se não houver nenhum. */
    public static ItemStack[] get(int entityId) {
        return STATE.get(entityId);
    }

    public static void clear() {
        STATE.clear();
    }
}