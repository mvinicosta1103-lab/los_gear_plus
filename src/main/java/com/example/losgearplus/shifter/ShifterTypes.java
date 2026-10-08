package com.example.losgearplus.shifter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Helpers to identify shifters and the Cart Titan (DAOT marks them with player tags).
 *
 * <p>A lista de tags e ABERTA: um addon com um shifter novo (tag propria) chama {@link #registerTag(String)} no
 * onInitialize dele e passa a ter regeneracao de membros, Steam Heal e maestria, sem editar este mod.
 */
public final class ShifterTypes {
    public static final String CART_TAG = "cart_shifter";

    /** Tags de shifter do DAOT + as registradas por addons. Compartilhada por Steam Heal, maestria e membros. */
    public static final Set<String> TAGS = ConcurrentHashMap.newKeySet();

    static {
        TAGS.addAll(Set.of(
                "attack", "colossal", "armored", "beast", "female", "warhammer",
                "founder", "triple_t", "ogre_shifter", "jaw", "cart_shifter"));
    }

    private ShifterTypes() {}

    /** API para addons: reconhece {@code tag} (tag de jogador) como um shifter. */
    public static void registerTag(String tag) {
        if (tag != null && !tag.isBlank()) TAGS.add(tag);
    }

    public static boolean isShifter(ServerPlayer p) {
        for (String tag : p.getTags()) {
            if (TAGS.contains(tag)) return true;
        }
        return false;
    }

    public static boolean isCartTitan(ServerPlayer p) {
        return p.getTags().contains(CART_TAG);
    }
}
