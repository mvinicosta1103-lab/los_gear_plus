package com.example.losgearplus.client;

import com.example.losgearplus.gear.GearMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Tela da aba Gear. Desenhada por código (sem textura PNG); troque por uma textura sua quando tiver a arte. */
public class GearScreen extends AbstractContainerScreen<GearMenu> {
    public GearScreen(GearMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        // painel
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, 0xFFFFFFFF);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);

        // caixa do personagem
        g.fill(x + 25, y + 7, x + 76, y + 79, 0xFF373737);
        g.fill(x + 26, y + 8, x + 75, y + 78, 0xFF000000);

        // slots
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
            g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, x + 26, y + 8, x + 75, y + 78, 30, 0.0625F, mouseX, mouseY, this.minecraft.player);
        }
    }

    /** Sem título/rótulos: os slots ocupam o espaço de cima. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // já desenha fundo, slots e tooltip de item

        // nome do slot ao passar o mouse num slot de gear vazio
        if (this.hoveredSlot instanceof GearMenu.GearSlot gearSlot
                && !gearSlot.hasItem()
                && this.menu.getCarried().isEmpty()) {
            g.renderTooltip(this.font, gearSlot.type().label(), mouseX, mouseY);
        }

        GearTabs.render(g, this.font, this.leftPos, this.topPos, GearTabs.GEAR, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = GearTabs.hit(this.leftPos, this.topPos, mouseX, mouseY);
        if (button == 0 && tab >= 0) {
            if (tab == GearTabs.INVENTORY) {
                GearTabs.playClick();
                Minecraft mc = this.minecraft;
                mc.player.closeContainer();
                if (!mc.player.isCreative()) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}