package com.example.losgearplus.gear;

import com.example.losgearplus.LosGearPlus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Guarda os itens da aba Gear DENTRO do jogador (Data Attachment API do Fabric):
 * salva com o mundo, é mantido ao morrer e é sincronizado com os clientes
 * (já preparado para a fase "visual no corpo").
 */
public final class GearData {
    private GearData() {}

    public static final int SIZE = GearSlotType.values().length;

    public static final AttachmentType<List<ItemStack>> ITEMS = AttachmentRegistry.<List<ItemStack>>create(
            LosGearPlus.id("gear_items"),
            builder -> builder
                    .persistent(ItemStack.OPTIONAL_CODEC.listOf())
                    .copyOnDeath()
                    .syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Força o registro do attachment (chamado no onInitialize). */
    public static void init() {}

    public static NonNullList<ItemStack> read(Player player) {
        NonNullList<ItemStack> out = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        List<ItemStack> saved = player.getAttached(ITEMS);
        if (saved != null) {
            for (int i = 0; i < Math.min(SIZE, saved.size()); i++) {
                out.set(i, saved.get(i).copy());
            }
        }
        return out;
    }

    public static void write(Player player, Container container) {
        List<ItemStack> list = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            list.add(container.getItem(i).copy());
        }
        player.setAttached(ITEMS, list);
    }

    /** Item equipado num tipo de slot (para as próximas fases: visual, modo ODMG...). */
    public static ItemStack get(Player player, GearSlotType type) {
        List<ItemStack> saved = player.getAttached(ITEMS);
        if (saved == null || type.ordinal() >= saved.size()) {
            return ItemStack.EMPTY;
        }
        return saved.get(type.ordinal());
    }
}