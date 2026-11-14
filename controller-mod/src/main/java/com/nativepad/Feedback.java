package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

/** Rumble on game events and the controller battery indicator. */
final class Feedback {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ControllerHandler pad;

    // ---- rumble state ----
    private float strong, light;      // current motor levels
    private long rumbleUntil;
    private float sentStrong = -1, sentLight = -1;
    private float lastHealth = -1;
    private long nextHeartbeat;
    private int heartbeatPhase;

    // ---- battery state ----
    private int[] battery;            // {type, level}
    private long nextBatteryPoll;
    private int lastWarnedLevel = 99;

    Feedback(ControllerHandler pad) {
        this.pad = pad;
        // Never leave the motors running if the game closes mid-rumble.
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                Feedback.this.pad.xinput().vibrate(Feedback.this.pad.padIndex(), 0, 0);
            }
        }, "NativePad rumble off"));
    }

    /** Starts (or strengthens) a rumble pulse. Levels 0..1, scaled by the strength setting. */
    void pulse(float strongMotor, float lightMotor, int millis) {
        PadConfig cfg = NativePad.config;
        if (!cfg.rumble) return;
        long now = System.currentTimeMillis();
        float s = cfg.rumbleStrength;
        if (now >= rumbleUntil) {
            strong = 0;
            light = 0;
        }
        strong = Math.max(strong, strongMotor * s);
        light = Math.max(light, lightMotor * s);
        rumbleUntil = Math.max(rumbleUntil, now + millis);
        apply();
    }

    private void apply() {
        boolean on = System.currentTimeMillis() < rumbleUntil && NativePad.config.rumble && Display.isActive();
        float s = on ? strong : 0, l = on ? light : 0;
        if (s != sentStrong || l != sentLight) {
            pad.xinput().vibrate(pad.padIndex(), s, l);
            sentStrong = s;
            sentLight = l;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) apply(); // ends pulses on time, stops rumble when tabbed out
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        PadConfig cfg = NativePad.config;
        while (NativePad.rumbleKey.isPressed()) {
            cfg.toggleRumble();
            mc.ingameGUI.setRecordPlaying("Rumble: " + (cfg.rumble ? EnumChatFormatting.GREEN + "on" : EnumChatFormatting.RED + "off"), false);
            if (cfg.rumble) pulse(0.5f, 0.5f, 150); // a little buzz so you feel it's on
        }
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || pad.padIndex() < 0) {
            lastHealth = -1;
            return;
        }
        long now = System.currentTimeMillis();
        float health = p.getHealth();

        // Took damage: a thump that scales with how much you lost.
        if (cfg.rumbleOnDamage && lastHealth > 0 && health < lastHealth && !p.isDead) {
            float lost = lastHealth - health;
            pulse(Math.min(1f, 0.35f + lost * 0.08f), Math.min(1f, 0.25f + lost * 0.05f),
                    Math.min(320, 130 + (int) (lost * 18)));
        }
        lastHealth = p.isDead ? -1 : health;

        // Low health: a heartbeat (two quick beats) every second.
        if (cfg.rumbleLowHealth && health > 0 && health <= cfg.lowHealthHearts * 2 && now >= nextHeartbeat) {
            pulse(0.45f, 0.1f, 80);
            heartbeatPhase = (heartbeatPhase + 1) % 2;
            nextHeartbeat = now + (heartbeatPhase == 1 ? 180 : 820);
        }

        pollBattery(now);
    }

    /** Chat alerts (bed destroyed, trap triggered, ...): a long strong rumble. */
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        PadConfig cfg = NativePad.config;
        if (!cfg.rumbleOnChatAlerts || e.message == null || e.type == 2) return; // type 2 = action bar
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(e.message.getUnformattedText()).toLowerCase();
        for (String phrase : cfg.rumbleChatPhrases) {
            if (!phrase.isEmpty() && text.contains(phrase.toLowerCase())) {
                pulse(1f, 1f, 600);
                return;
            }
        }
    }

    // ---- battery ----

    private void pollBattery(long now) {
        if (now < nextBatteryPoll) return;
        nextBatteryPoll = now + 5000; // battery changes slowly; don't ask Windows every frame
        battery = pad.xinput().battery(pad.padIndex());
        if (battery == null || battery[0] == XInput.BATTERY_WIRED || battery[0] == XInput.BATTERY_UNKNOWN) return;
        int level = battery[1];
        if (level <= 1 && level < lastWarnedLevel && NativePad.config.batteryWarning) {
            pad.chat(EnumChatFormatting.RED + (level == 0 ? "Controller battery is empty!" : "Controller battery is low."));
            lastWarnedLevel = level;
        } else if (level > 1) {
            lastWarnedLevel = 99;
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        String pos = NativePad.config.batteryHud;
        if (pos.equals(PadConfig.HUD_OFF) || pad.padIndex() < 0 || mc.gameSettings.showDebugInfo) return;

        // Only shown when Windows reports a real battery level. Many wireless pads with a USB dongle
        // (e.g. EasySMX) report as "wired", so there is nothing meaningful to show for them.
        if (battery == null || battery[0] == XInput.BATTERY_UNKNOWN || battery[0] == XInput.BATTERY_WIRED
                || battery[0] == XInput.BATTERY_DISCONNECTED) return;
        String label = "Pad";
        int bars = battery[1] + 1; // level 0..3 -> 1..4 bars
        int color = battery[1] <= 1 ? 0xFFFF5555 : battery[1] == 2 ? 0xFFFFDD55 : 0xFF55FF55;

        ScaledResolution sr = e.resolution;
        int textW = mc.fontRendererObj.getStringWidth(label);
        int width = textW + (bars >= 0 ? 4 + 4 * 4 : 0);
        int x = pos.endsWith("left") ? 3 : sr.getScaledWidth() - width - 3;
        int y = pos.startsWith("Top") ? 3 : sr.getScaledHeight() - 12;
        mc.fontRendererObj.drawStringWithShadow(label, x, y, 0xFFDDDDDD);
        if (bars >= 0) {
            int bx = x + textW + 4;
            for (int i = 0; i < 4; i++) {
                int left = bx + i * 4;
                Gui.drawRect(left, y + 1, left + 3, y + 8, i < bars ? color : 0x66000000);
            }
        }
    }
}
