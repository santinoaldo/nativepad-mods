package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * D-pad navigation in menus: jumps the cursor to the nearest slot (inventories, chests, shops)
 * or button (other screens) in the pressed direction.
 */
final class MenuSnap {

    private static final Field GUI_LEFT = ReflectionHelper.findField(GuiContainer.class, "guiLeft", "field_147003_i");
    private static final Field GUI_TOP = ReflectionHelper.findField(GuiContainer.class, "guiTop", "field_147009_r");
    private static final Field BUTTON_LIST = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
    private static final Field BUTTON_HEIGHT = ReflectionHelper.findField(GuiButton.class, "height", "field_146121_g");

    private MenuSnap() {
    }

    /** Moves the cursor one step in direction (dx, dy), screen coordinates (dy > 0 = down). */
    static void step(Minecraft mc, GuiScreen screen, double dx, double dy) {
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        dx /= len; // any direction works (D-pad or stick), as a unit vector
        dy /= len;
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        double mx = Mouse.getX() * (double) w / mc.displayWidth;
        double my = h - Mouse.getY() * (double) h / mc.displayHeight - 1;

        double bestScore = Double.MAX_VALUE;
        double[] best = null;
        for (double[] t : targets(screen)) {
            double ox = t[0] - mx, oy = t[1] - my;
            double along = ox * dx + oy * dy;        // distance in the pressed direction
            if (along < 3) continue;                 // must actually be in that direction
            double across = Math.abs(ox * dy - oy * dx); // how far off to the side
            double score = along + across * 2.5;     // prefer targets straight ahead
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        if (best == null) return;
        int px = (int) Math.round(best[0] * mc.displayWidth / w);
        int pyTop = (int) Math.round(best[1] * mc.displayHeight / h);
        Mouse.setCursorPosition(px, mc.displayHeight - pyTop - 1);
    }

    /** Centers of everything the cursor can snap to, in GUI (scaled) coordinates. */
    @SuppressWarnings("unchecked")
    private static List<double[]> targets(GuiScreen screen) {
        List<double[]> out = new ArrayList<double[]>();
        try {
            if (screen instanceof GuiContainer) {
                GuiContainer gc = (GuiContainer) screen;
                int left = GUI_LEFT.getInt(gc), top = GUI_TOP.getInt(gc);
                for (Slot s : (List<Slot>) gc.inventorySlots.inventorySlots) {
                    out.add(new double[]{left + s.xDisplayPosition + 8, top + s.yDisplayPosition + 8});
                }
            }
            for (GuiButton b : (List<GuiButton>) BUTTON_LIST.get(screen)) {
                if (!b.visible || !b.enabled) continue;
                int bh = BUTTON_HEIGHT.getInt(b);
                out.add(new double[]{b.xPosition + b.getButtonWidth() / 2.0, b.yPosition + bh / 2.0});
            }
        } catch (IllegalAccessException ignored) {
        }
        return out;
    }
}
