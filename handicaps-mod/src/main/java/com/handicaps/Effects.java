package com.handicaps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Hooks each handicap into the right point of Minecraft's frame. */
final class Effects {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final PostFx fx = new PostFx();
    private final long startNanos = System.nanoTime();
    private boolean warnedShaders;

    private float time() {
        return (System.nanoTime() - startNanos) / 1e9f;
    }

    private static HConfig cfg() {
        return Handicaps.config;
    }

    // ---- world pass: short sight fog, near-sighted blur, drunk wobble ----

    /** Runs after the world is drawn but before your hand and the HUD, so those stay clear. */
    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        HConfig c = cfg();
        if (!c.active()) return;
        boolean fog = c.shortSight, blur = c.nearSighted, drunk = c.drunk;
        if (!fog && !blur && !drunk) return;
        if (!fx.available()) {
            warnShaders();
            return;
        }
        float fogEnd = c.sightDistance;
        float fogStart = Math.max(0f, fogEnd - c.fogSoftness);
        fx.worldPass(fog, fogStart, fogEnd, c.fogRgb(),
                blur, c.clearDistance, c.blurRamp, c.blurStrength,
                drunk, c.drunkStrength, time());
    }

    // ---- tunnel vision: before the HUD is drawn, so the hotbar stays readable ----

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        HConfig c = cfg();
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !c.active() || !c.tunnel) return;
        if (!fx.available()) {
            warnShaders();
            return;
        }
        // tunnelSize = how much of the view stays visible (radius as a share of screen height).
        float inner = c.tunnelSize * 0.5f;
        fx.tunnelPass(inner, inner + Math.max(0.02f, c.tunnelSoftness * 0.5f));
    }

    // ---- grayscale: last thing in the frame, so HUD, tab list and menus lose their color too ----

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        HConfig c = cfg();
        if (e.phase == TickEvent.Phase.START) {
            drunkAim();
            return;
        }
        if (!c.active() || !c.grayscale) return;
        if (!fx.available()) {
            warnShaders();
            return;
        }
        fx.grayscalePass(c.grayscaleAmount);
    }

    // ---- short sight: hide nametags of anyone beyond the fog (they're normally drawn through walls) ----

    @SubscribeEvent
    public void onNametag(RenderLivingEvent.Specials.Pre<EntityLivingBase> e) {
        HConfig c = cfg();
        if (!c.active() || !c.shortSight || mc.getRenderViewEntity() == null) return;
        if (e.entity.getDistanceToEntity(mc.getRenderViewEntity()) > c.sightDistance) e.setCanceled(true);
    }

    // ---- deaf ----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSound(PlaySoundEvent e) {
        HConfig c = cfg();
        if (!c.active() || !c.deaf || e.result == null) return;
        // Keep menu clicks so menus don't feel broken; everything in the world goes silent.
        if (e.name != null && e.name.startsWith("gui.")) return;
        e.result = null;
    }

    // ---- drunk: swaying camera (visual) and drifting aim (real) ----

    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup e) {
        HConfig c = cfg();
        if (!c.active() || !c.drunk) return;
        float t = time(), s = c.drunkStrength;
        e.roll += (float) (Math.sin(t * 0.8) * 7.0 * s);
        e.yaw += (float) (Math.sin(t * 0.55) * 3.0 * s);
        e.pitch += (float) (Math.cos(t * 0.7) * 2.0 * s);
    }

    private double lastDriftYaw, lastDriftPitch, driftAccX, driftAccY;

    /** Slowly pulls your real aim around, in whole mouse steps like a real mouse. */
    private void drunkAim() {
        HConfig c = cfg();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.currentScreen != null || !c.active() || !c.drunk) {
            lastDriftYaw = lastDriftPitch = driftAccX = driftAccY = 0;
            return;
        }
        float t = time(), s = c.drunkStrength;
        double yaw = (Math.sin(t * 0.43) + 0.5 * Math.sin(t * 1.7)) * 4.0 * s;   // degrees of drift
        double pitch = (Math.cos(t * 0.37) + 0.4 * Math.sin(t * 1.3)) * 2.0 * s;
        double dYaw = yaw - lastDriftYaw, dPitch = pitch - lastDriftPitch;
        lastDriftYaw = yaw;
        lastDriftPitch = pitch;

        float f = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float mult = f * f * f * 8.0f;
        double degPerCount = mult * 0.15;
        driftAccX += dYaw / degPerCount;
        driftAccY -= dPitch / degPerCount;
        int cx = (int) driftAccX, cy = (int) driftAccY;
        driftAccX -= cx;
        driftAccY -= cy;
        if (cx != 0 || cy != 0) p.setAngles(cx * mult, cy * mult);
    }

    private void warnShaders() {
        if (warnedShaders || mc.thePlayer == null) return;
        warnedShaders = true;
        mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED
                + "[Handicaps] Screen effects need shader support"
                + (fx.error != null ? " (" + fx.error.split("\n")[0] + ")" : "") + ". Deaf still works."));
    }
}
