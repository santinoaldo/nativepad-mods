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
    static final int W = 124, H = 84;
    static Overlay instance;

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

    // ---- the drawing (units: PadShape.W x PadShape.H, scaled to fit W x H) ----

    private static final int BODY = 0xFF141414, EDGE = 0xFF4A4A4A, HORN = 0xFF2A2A2A, PART = 0xFF2E2E2E,
            STICK_BASE = 0xFF262626, STICK_CAP = 0xFF3C3C3C, FACE = 0xFF0C0C0C, LIT = 0xFFEEEEEE;
    private static final int A_COL = 0xFF5BC236, B_COL = 0xFFE0453A, X_COL = 0xFF3A86E0, Y_COL = 0xFFF2C230;

    /** Draws the controller with its top-left corner at (x, y). */
    void draw(float x, float y, float scale, XInput.PadState s) {
        float t = NativePad.config.triggerThreshold;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale * W / (PadShape.W + 4), scale * H / (PadShape.H + 2), 1);
        GlStateManager.translate(2, 1, 0);
        GlStateManager.disableCull();

        // Triggers stick up behind the top; they fill from the top down as you pull them.
        trigger(19, s.lt, s.lt > t);
        trigger(84, s.rt, s.rt > t);

        // The body: one solid shape with a slightly larger lighter copy behind it as the edge.
        shape(1.025f, 1.035f, EDGE);
        shape(1f, 1f, BODY);

        // Bumpers along the top edge.
        roundRect(23, 10.5f, 45, 13.5f, 1.5f, down(s, XInput.Button.LB) ? LIT : 0xFF333333);
        roundRect(75, 10.5f, 97, 13.5f, 1.5f, down(s, XInput.Button.RB) ? LIT : 0xFF333333);

        // Sticks: a base, and a cap that moves with the stick (yellow when clicked in).
        stick(36, 27, s.lx, s.ly, down(s, XInput.Button.L3));
        stick(75, 41, s.rx, s.ry, down(s, XInput.Button.R3));

        // D-pad: a plus; the pressed arm lights up.
        float dx = 45, dy = 41;
        roundRect(dx - 2.5f, dy - 7, dx + 2.5f, dy + 7, 1, PART);
        roundRect(dx - 7, dy - 2.5f, dx + 7, dy + 2.5f, 1, PART);
        if (down(s, XInput.Button.DPAD_UP)) roundRect(dx - 2.5f, dy - 7, dx + 2.5f, dy - 2.5f, 1, LIT);
        if (down(s, XInput.Button.DPAD_DOWN)) roundRect(dx - 2.5f, dy + 2.5f, dx + 2.5f, dy + 7, 1, LIT);
        if (down(s, XInput.Button.DPAD_LEFT)) roundRect(dx - 7, dy - 2.5f, dx - 2.5f, dy + 2.5f, 1, LIT);
        if (down(s, XInput.Button.DPAD_RIGHT)) roundRect(dx + 2.5f, dy - 2.5f, dx + 7, dy + 2.5f, 1, LIT);

        // Back / Start.
        circle(53, 27, 2.3, down(s, XInput.Button.BACK) ? LIT : 0xFF333333);
        circle(67, 27, 2.3, down(s, XInput.Button.START) ? LIT : 0xFF333333);

        // Face buttons: colored letters on dark buttons; the whole button fills with its color when pressed.
        float fx = 87, fy = 27, sp = 6.5f;
        face(fx, fy + sp, A_COL, down(s, XInput.Button.A), "A");
        face(fx + sp, fy, B_COL, down(s, XInput.Button.B), "B");
        face(fx - sp, fy, X_COL, down(s, XInput.Button.X), "X");
        face(fx, fy - sp, Y_COL, down(s, XInput.Button.Y), "Y");

        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static boolean down(XInput.PadState s, XInput.Button b) {
        return (s.buttons & b.mask) != 0;
    }

    private void trigger(float left, float value, boolean pressed) {
        roundRect(left, 0, left + 17, 18, 4, HORN);
        float fill = 18 * Math.max(0f, Math.min(1f, value));
        if (fill > 0.5f) roundRect(left, 0, left + 17, Math.max(fill, 8), 4, pressed ? LIT : 0xFF8A8A8A);
    }

    private void stick(float cx, float cy, float sx, float sy, boolean clicked) {
        circle(cx, cy, 8.5, STICK_BASE);
        circle(cx + sx * 2.5, cy - sy * 2.5, 6, clicked ? 0xFFF2C230 : STICK_CAP);
    }

    private void face(float cx, float cy, int color, boolean pressed, String label) {
        circle(cx, cy, 3.6, pressed ? color : FACE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(0.55f, 0.55f, 1);
        int w = mc.fontRendererObj.getStringWidth(label);
        mc.fontRendererObj.drawString(label, -w / 2 + 1, -3, pressed ? 0xFF000000 : color);
        GlStateManager.popMatrix();
    }

    /** The pad outline filled solid, scaled about its middle (scale > 1 makes the edge ring). */
    private static void shape(float sx, float sy, int argb) {
        begin(argb);
        Tessellator tes = Tessellator.getInstance();
        WorldRenderer w = tes.getWorldRenderer();
        w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        float[] p = PadShape.OUTLINE;
        for (int i : PadShape.TRIANGLES) {
            w.pos(60 + (p[i * 2] - 60) * sx, 42 + (p[i * 2 + 1] - 42) * sy, 0).endVertex();
        }
        tes.draw();
        end();
    }

    private static void roundRect(float l, float t, float r, float b, float rad, int argb) {
        rad = Math.min(rad, Math.min((r - l) / 2, (b - t) / 2));
        begin(argb);
        Tessellator tes = Tessellator.getInstance();
        WorldRenderer w = tes.getWorldRenderer();
        w.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION); // convex: one polygon, no overlaps
        float[][] corners = {{r - rad, t + rad, -90}, {r - rad, b - rad, 0}, {l + rad, b - rad, 90}, {l + rad, t + rad, 180}};
        for (float[] c : corners) {
            for (int i = 0; i <= 4; i++) {
                double ang = Math.toRadians(c[2] + i * 22.5);
                w.pos(c[0] + Math.cos(ang) * rad, c[1] + Math.sin(ang) * rad, 0).endVertex();
            }
        }
        tes.draw();
        end();
    }

    /** A filled circle. */
    private static void circle(double cx, double cy, double r, int argb) {
        begin(argb);
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
        end();
    }

    private static void begin(int argb) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private static void end() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
