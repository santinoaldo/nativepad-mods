package com.nativepad;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Drag the controller overlay wherever you want it with the mouse (or the controller cursor).
 * Scroll to resize. Done saves; Esc cancels.
 */
public final class OverlayMoveScreen extends GuiScreen {

    private static final int DONE = 0, RESET = 1, CANCEL = 2, SNAP = 6;

    private final GuiScreen parent;
    private float x, y, scale;
    private boolean dragging;
    private float grabX, grabY;

    public OverlayMoveScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        if (scale == 0) { // first open: start from where it is now
            scale = NativePad.config.overlayScale;
            float[] at = Overlay.position(new ScaledResolution(mc), scale);
            x = at[0];
            y = at[1];
        }
        clamp();
        int cx = width / 2;
        buttonList.clear();
        buttonList.add(new GuiButton(DONE, cx - 154, height / 2 + 10, 100, 20, "Done"));
        buttonList.add(new GuiButton(RESET, cx - 50, height / 2 + 10, 100, 20, "Reset"));
        buttonList.add(new GuiButton(CANCEL, cx + 54, height / 2 + 10, 100, 20, "Cancel"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.theWorld == null) drawDefaultBackground();
        else drawGradientRect(0, 0, width, height, 0x60101010, 0x80101010); // see your game behind it
        drawCenteredString(fontRendererObj, "Drag the controller to move it", width / 2, height / 2 - 30, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Scroll to resize (" + String.format("%.1f", scale) + "x)",
                width / 2, height / 2 - 18, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);

        float w = Overlay.W * scale, h = Overlay.H * scale;
        boolean hover = dragging || inside(mouseX, mouseY);
        int border = hover ? 0xFFFFFF55 : 0x80FFFFFF;
        int l = (int) x - 1, t = (int) y - 1, r = (int) (x + w) + 1, b = (int) (y + h) + 1;
        Gui.drawRect(l, t, r, t + 1, border);
        Gui.drawRect(l, b - 1, r, b, border);
        Gui.drawRect(l, t, l + 1, b, border);
        Gui.drawRect(r - 1, t, r, b, border);
        if (Overlay.instance != null) Overlay.instance.draw(x, y, scale, Overlay.instance.liveState());
    }

    private boolean inside(int mx, int my) {
        return mx >= x && mx <= x + Overlay.W * scale && my >= y && my <= y + Overlay.H * scale;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 0 && inside(mouseX, mouseY)) {
            dragging = true;
            grabX = mouseX - x;
            grabY = mouseY - y;
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (!dragging) return;
        x = mouseX - grabX;
        y = mouseY - grabY;
        // Snap to the screen edges and to the center lines when close.
        float w = Overlay.W * scale, h = Overlay.H * scale;
        if (Math.abs(x) < SNAP) x = 0;
        if (Math.abs(x + w - width) < SNAP) x = width - w;
        if (Math.abs(x + w / 2 - width / 2f) < SNAP) x = (width - w) / 2;
        if (Math.abs(y) < SNAP) y = 0;
        if (Math.abs(y + h - height) < SNAP) y = height - h;
        clamp();
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            // Resize around the overlay's center so it doesn't jump away.
            float cx = x + Overlay.W * scale / 2, cy = y + Overlay.H * scale / 2;
            scale = Math.max(0.5f, Math.min(2.5f, Math.round((scale + (wheel > 0 ? 0.1f : -0.1f)) * 10) / 10f));
            x = cx - Overlay.W * scale / 2;
            y = cy - Overlay.H * scale / 2;
            clamp();
        }
    }

    private void clamp() {
        x = Math.max(0, Math.min(width - Overlay.W * scale, x));
        y = Math.max(0, Math.min(height - Overlay.H * scale, y));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == DONE) {
            float freeW = width - Overlay.W * scale, freeH = height - Overlay.H * scale;
            NativePad.config.setOverlayPosition(freeW > 0 ? x / freeW : 0, freeH > 0 ? y / freeH : 0, scale);
            // Make the settings screen show the new position and size instead of the old values.
            if (parent instanceof net.minecraftforge.fml.client.config.GuiConfig) {
                ((net.minecraftforge.fml.client.config.GuiConfig) parent).needsRefresh = true;
            }
            mc.displayGuiScreen(parent);
        } else if (b.id == RESET) {
            scale = 1.0f;
            x = width - Overlay.W - 3; // bottom right, the default
            y = height - Overlay.H - 3;
        } else if (b.id == CANCEL) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == org.lwjgl.input.Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent); // back to settings, unsaved
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
