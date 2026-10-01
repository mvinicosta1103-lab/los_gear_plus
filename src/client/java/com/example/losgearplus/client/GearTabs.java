package com.example.losgearplus.client;

import com.example.losgearplus.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Faixa de abas desenhada em cima do inventário (estilo abas do criativo).
 * Aba 0 = Inventário, aba 1 = Gear. Para adicionar abas (ex.: Customização),
 * aumente TAB_COUNT e trate o novo índice em icon()/name().
 */
public final class GearTabs {
    private GearTabs() {}

    public static final int INVENTORY = 0;
    public static final int GEAR = 1;
    public static final int TAB_COUNT = 2;

    private static final int WIDTH = 26;
    private static final int HEIGHT = 28;   // parte visível acima do painel
    private static final int STEP = 27;

    private static ResourceLocation sprite(boolean selected, int index) {
        return ResourceLocation.withDefaultNamespace(
                "container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + (index + 1));
    }

    private static ItemStack icon(int index) {
        return index == INVENTORY ? new ItemStack(Items.GRASS_BLOCK) : new ItemStack(ModItems.CHEST_STRAP);
    }

    private static Component name(int index) {
        return Component.translatable(index == INVENTORY ? "tab.los_gear_plus.inventory" : "tab.los_gear_plus.gear");
    }

    /** @return índice da aba sob o mouse, ou -1. */
    public static int hit(int left, int top, double mouseX, double mouseY) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int x = left + STEP * i;
            int y = top - HEIGHT;
            if (mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    public static void render(GuiGraphics g, Font font, int left, int top, int selected, int mouseX, int mouseY) {
        // não-selecionadas primeiro, a selecionada por cima (como no criativo)
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < TAB_COUNT; i++) {
                boolean isSel = i == selected;
                if (isSel != (pass == 1)) {
                    continue;
                }
                int x = left + STEP * i;
                int y = top - HEIGHT;
                g.blitSprite(sprite(isSel, i), x, y, WIDTH, HEIGHT + 4);
                g.renderItem(icon(i), x + 5, y + 9);
            }
        }
        int hovered = hit(left, top, mouseX, mouseY);
        if (hovered >= 0) {
            g.renderTooltip(font, name(hovered), mouseX, mouseY);
        }
    }

    public static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}