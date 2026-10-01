package com.example.losgearplus.gear;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Container (só no servidor) ligado ao attachment do jogador: toda mudança é salva na hora. */
public class GearContainer extends SimpleContainer {
    public GearContainer(Player owner) {
        super(GearData.SIZE);
        NonNullList<ItemStack> saved = GearData.read(owner);
        for (int i = 0; i < GearData.SIZE; i++) {
            setItem(i, saved.get(i));
        }
        // Só depois de carregar: assim carregar não conta como "mudança".
        addListener(container -> GearData.write(owner, this));
    }
}