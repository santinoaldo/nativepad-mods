package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Makes this mod's settings screens see-through while you're in a world, so you can watch the game while
 * changing settings (the same dim tint as the pause menu instead of the dirt background).
 */
final class SeeThroughSettings {

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.gui instanceof GuiConfig)) return;
        GuiConfig g = (GuiConfig) e.gui;
        if (!"nativepad".equals(g.modID) || g.entryList instanceof ClearList) return;
        // Only swapped right after the list was (re)built, so no unsaved edits are lost.
        ClearList list = new ClearList(g, Minecraft.getMinecraft());
        g.entryList = list;
        list.layout();
    }

    /** Forge's settings list without the dirt background (only while a world is showing behind it). */
    static final class ClearList extends GuiConfigEntries {
        ClearList(GuiConfig parent, Minecraft mc) {
            super(parent, mc);
        }

        void layout() {
            initGui(); // sizes and positions the rows, like GuiConfig does for its own list
        }

        @Override
        protected void drawContainerBackground(Tessellator t) {
            if (Minecraft.getMinecraft().theWorld == null) super.drawContainerBackground(t);
        }

        @Override
        protected void overlayBackground(int startY, int endY, int startAlpha, int endAlpha) {
            if (Minecraft.getMinecraft().theWorld == null) super.overlayBackground(startY, endY, startAlpha, endAlpha);
        }
    }
}
