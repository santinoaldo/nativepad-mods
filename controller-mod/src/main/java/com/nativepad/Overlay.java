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
    static final int W = 120, H = 76;
    static Overlay instance;

    private static final int IDLE = 0x55FFFFFF, RING = 0x40FFFFFF, BODY = 0xFF1E1E1E, OUTLINE = 0xFF5A5A5A,
            LIT = 0xFFFFFFFF;
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

        // Triggers on top (fill shows how far they're pulled), then the bumpers peeking out above the body.
        trigger(15, s.lt, s.lt > t);
        trigger(79, s.rt, s.rt > t);
        Gui.drawRect(13, 9, 43, 15, down(s, XInput.Button.LB) ? LIT : 0xFF6A6A6A);
        Gui.drawRect(77, 9, 107, 15, down(s, XInput.Button.RB) ? LIT : 0xFF6A6A6A);

        // The controller itself: an Xbox-style silhouette with two grips. Solid, so the overlapping pieces
        // blend into one shape; a lighter outline first, then the body on top.
        silhouette(1, OUTLINE);
        silhouette(0, BODY);

        // Sticks: ring, plus a dot where the stick is (lit when clicked in). Left stick up top, right one low.
        stick(32, 30, s.lx, s.ly, down(s, XInput.Button.L3));
        stick(76, 42, s.rx, s.ry, down(s, XInput.Button.R3));

        // D-pad, low on the left.
        int dx = 44, dy = 42;
        Gui.drawRect(dx - 2, dy - 2, dx + 2, dy + 2, IDLE);
        Gui.drawRect(dx - 2, dy - 7, dx + 2, dy - 2, down(s, XInput.Button.DPAD_UP) ? LIT : IDLE);
        Gui.drawRect(dx - 2, dy + 2, dx + 2, dy + 7, down(s, XInput.Button.DPAD_DOWN) ? LIT : IDLE);
        Gui.drawRect(dx - 7, dy - 2, dx - 2, dy + 2, down(s, XInput.Button.DPAD_LEFT) ? LIT : IDLE);
        Gui.drawRect(dx + 2, dy - 2, dx + 7, dy + 2, down(s, XInput.Button.DPAD_RIGHT) ? LIT : IDLE);

        // Back / Start in the middle.
        Gui.drawRect(51, 27, 56, 30, down(s, XInput.Button.BACK) ? LIT : IDLE);
        Gui.drawRect(64, 27, 69, 30, down(s, XInput.Button.START) ? LIT : IDLE);

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
        Gui.drawRect(left, 0, left + 26, 6, 0xFF4A4A4A);
        int fill = Math.round(26 * Math.max(0f, Math.min(1f, value)));
        if (fill > 0) Gui.drawRect(left, 0, left + fill, 6, pressed ? LIT : 0xFFBBBBBB);
    }

    /** The pad's outline: a rounded top body plus a grip on each side, grown by {@code e} pixels. */
    private void silhouette(int e, int color) {
        // Rounded top body.
        int r = 10 + e;
        Gui.drawRect(14 - e + 10, 14 - e, 106 + e - 10, 50 + e, color);
        Gui.drawRect(14 - e, 14 - e + 10, 106 + e, 50 + e - 10, color);
        circle(24, 24, r, color);
        circle(96, 24, r, color);
        circle(24, 40, r, color);
        circle(96, 40, r, color);
        // Grips: a wide upper part and a narrower lower tip, angled outward like a real controller.
        circle(28, 48, 16 + e, color);
        circle(25, 55, 14 + e, color); // smooths the grip between the two parts
        circle(22, 61, 12 + e, color);
        circle(92, 48, 16 + e, color);
        circle(95, 55, 14 + e, color);
        circle(98, 61, 12 + e, color);
    }

    private void stick(int cx, int cy, float sx, float sy, boolean clicked) {
        circle(cx, cy, 8, RING);
        circle(cx + sx * 4, cy - sy * 4, 4, clicked ? 0xFFFFDD55 : 0xFFDDDDDD);
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
