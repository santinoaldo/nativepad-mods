package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Controller overlay: a small drawing of the pad on the HUD that lights up live as you press buttons,
 * pull triggers and move the sticks (for recordings, streams, or checking your inputs).
 * Display only: it reads the same state the mod already reads and changes nothing.
 */
final class Overlay {

    /** Size of the drawing at scale 1, in GUI pixels. */
    static final int W = 110, H = 62;
    static Overlay instance;

    private static final int IDLE = 0x55FFFFFF, RING = 0x40FFFFFF, BODY = 0x90101010, LIT = 0xFFFFFFFF;
    private static final int A_COL = 0xFF55DD55, B_COL = 0xFFEE5555, X_COL = 0xFF5599FF, Y_COL = 0xFFEEDD44;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ControllerHandler pad;

    Overlay(ControllerHandler pad) {
        this.pad = pad;
        instance = this;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        PadConfig cfg = NativePad.config;
        String pos = cfg.overlayHud;
        if (pos.equals(PadConfig.HUD_OFF) || pad.padIndex() < 0 || mc.gameSettings.showDebugInfo) return;

        float[] at = position(e.resolution, cfg.overlayScale);
        draw(at[0], at[1], cfg.overlayScale, pad.state());
    }

    private final XInput.PadState none = new XInput.PadState();

    /** The live controller reading, or an idle one when no controller is connected. */
    XInput.PadState liveState() {
        return pad.padIndex() >= 0 ? pad.state() : none;
    }

    /** Where the overlay's top-left corner goes on this screen: a corner preset, or where you dragged it. */
    static float[] position(ScaledResolution sr, float scale) {
        PadConfig cfg = NativePad.config;
        String pos = cfg.overlayHud;
        float w = W * scale, h = H * scale, sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        float x, y;
        if (pos.equals(PadConfig.HUD_CUSTOM)) {
            // Saved as a fraction of the free space, so it stays in the same spot at any window size.
            x = cfg.overlayX * (sw - w);
            y = cfg.overlayY * (sh - h);
        } else {
            x = pos.endsWith("left") ? 3 : sw - w - 3;
            y = pos.startsWith("Top") ? 3 : sh - h - 3;
            // Don't sit on top of the battery indicator when both use the same corner.
            if (pos.equals(cfg.batteryHud)) y += pos.startsWith("Top") ? 12 : -12;
        }
        return new float[]{Math.max(0, Math.min(sw - w, x)), Math.max(0, Math.min(sh - h, y))};
    }

    /** Draws the controller with its top-left corner at (x, y). */
    void draw(float x, float y, float scale, XInput.PadState s) {
        PadConfig cfg = NativePad.config;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.disableCull();
        float t = cfg.triggerThreshold;

        // Triggers (fill shows how far they're pulled) and bumpers.
        trigger(6, s.lt, s.lt > t);
        trigger(76, s.rt, s.rt > t);
        Gui.drawRect(6, 8, 34, 12, down(s, XInput.Button.LB) ? LIT : IDLE);
        Gui.drawRect(76, 8, 104, 12, down(s, XInput.Button.RB) ? LIT : IDLE);

        // Body.
        Gui.drawRect(0, 14, W, H, BODY);

        // Sticks: ring, plus a dot where the stick is (lit when clicked in).
        stick(22, 30, s.lx, s.ly, down(s, XInput.Button.L3));
        stick(72, 50, s.rx, s.ry, down(s, XInput.Button.R3));

        // D-pad.
        int dx = 38, dy = 50;
        Gui.drawRect(dx - 2, dy - 2, dx + 2, dy + 2, IDLE);
        Gui.drawRect(dx - 2, dy - 8, dx + 2, dy - 2, down(s, XInput.Button.DPAD_UP) ? LIT : IDLE);
        Gui.drawRect(dx - 2, dy + 2, dx + 2, dy + 8, down(s, XInput.Button.DPAD_DOWN) ? LIT : IDLE);
        Gui.drawRect(dx - 8, dy - 2, dx - 2, dy + 2, down(s, XInput.Button.DPAD_LEFT) ? LIT : IDLE);
        Gui.drawRect(dx + 2, dy - 2, dx + 8, dy + 2, down(s, XInput.Button.DPAD_RIGHT) ? LIT : IDLE);

        // Back / Start.
        Gui.drawRect(45, 27, 50, 30, down(s, XInput.Button.BACK) ? LIT : IDLE);
        Gui.drawRect(60, 27, 65, 30, down(s, XInput.Button.START) ? LIT : IDLE);

        // Face buttons in their usual colors (dim until pressed).
        int fx = 88, fy = 30;
        face(fx, fy + 7, A_COL, down(s, XInput.Button.A), "A");
        face(fx + 7, fy, B_COL, down(s, XInput.Button.B), "B");
        face(fx - 7, fy, X_COL, down(s, XInput.Button.X), "X");
        face(fx, fy - 7, Y_COL, down(s, XInput.Button.Y), "Y");

        GlStateManager.enableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static boolean down(XInput.PadState s, XInput.Button b) {
        return (s.buttons & b.mask) != 0;
    }

    private void trigger(int left, float value, boolean pressed) {
        Gui.drawRect(left, 0, left + 28, 6, IDLE);
        int fill = Math.round(28 * Math.max(0f, Math.min(1f, value)));
        if (fill > 0) Gui.drawRect(left, 0, left + fill, 6, pressed ? LIT : 0xCCBBBBBB);
    }

    private void stick(int cx, int cy, float sx, float sy, boolean clicked) {
        circle(cx, cy, 9, RING);
        circle(cx + sx * 5, cy - sy * 5, 4, clicked ? 0xFFFFDD55 : 0xEEDDDDDD);
    }

    private void face(int cx, int cy, int color, boolean pressed, String label) {
        circle(cx, cy, 3.5, pressed ? color : (color & 0x00FFFFFF) | 0x55000000);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(0.5f, 0.5f, 1);
        int w = mc.fontRendererObj.getStringWidth(label);
        mc.fontRendererObj.drawString(label, -w / 2 + 1, -3, pressed ? 0xFF000000 : 0xFFFFFFFF);
        GlStateManager.popMatrix();
    }

    /** A filled circle. */
    private static void circle(double cx, double cy, double r, int argb) {
        float a = (argb >>> 24) / 255f, red = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(red, g, b, a);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer w = t.getWorldRenderer();
        w.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);
        w.pos(cx, cy, 0).endVertex();
        int n = 24;
        for (int i = 0; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            w.pos(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r, 0).endVertex();
        }
        t.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
