package com.example.losgearplus.shifter;

import net.minecraft.server.level.ServerPlayer;

/** Helpers to identify shifters and the Cart Titan (DAOT marks them with player tags). */
public final class ShifterTypes {
    public static final String CART_TAG = "cart_shifter";

    private ShifterTypes() {}

    public static boolean isShifter(ServerPlayer p) {
        for (String tag : p.getTags()) {
            if (ShifterMasterySync.SHIFTER_TAGS.contains(tag)) return true;
        }
        return false;
    }

    public static boolean isCartTitan(ServerPlayer p) {
        return p.getTags().contains(CART_TAG);
    }
}