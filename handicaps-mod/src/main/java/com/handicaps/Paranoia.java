package com.handicaps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Paranoia: fake sounds from behind you, at a real position in the world so your headphones place them.
 * Client-side only: nobody else hears them, nothing is sent to the server.
 */
final class Paranoia {

    static Paranoia instance;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rnd = new Random();
    private long nextEventAt;

    /** A sound waiting to play: when, what, where (distance and angle relative to where you face). */
    private static final class Pending {
        final long at;
        final String sound;
        final float volume, pitch, distance, angle;

        Pending(long at, String sound, float volume, float pitch, float distance, float angle) {
            this.at = at;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.distance = distance;
            this.angle = angle;
        }
    }

    private final List<Pending> queue = new ArrayList<Pending>();
    /** Sounds from Test buttons; they play even while Paranoia is off. */
    private final List<Pending> testQueue = new ArrayList<Pending>();

    Paranoia() {
        instance = this;
    }

    /** Test button: play this sound exactly as Paranoia would, right now. */
    void test(ParanoiaSound s) {
        if (mc.thePlayer == null) {
            // Not in a world (title screen): just play it straight through your speakers.
            final float v = Math.max(0f, Math.min(1f, Handicaps.config.paranoiaVolume));
            mc.getSoundHandler().playSound(new FlatSound(new ResourceLocation(s.sound), v));
            return;
        }
        testQueue.addAll(build(s, System.currentTimeMillis()));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = mc.thePlayer;
        long now = System.currentTimeMillis();
        if (p == null) {
            queue.clear();
            testQueue.clear();
            return;
        }
        playDue(testQueue, p, now);

        HConfig c = Handicaps.config;
        if (mc.theWorld == null || !c.active() || !c.paranoia) {
            queue.clear();
            nextEventAt = 0;
            return;
        }
        if (nextEventAt == 0) nextEventAt = now + randomGap(c);
        playDue(queue, p, now);
        if (now >= nextEventAt && queue.isEmpty()) {
            List<ParanoiaSound> on = new ArrayList<ParanoiaSound>();
            for (ParanoiaSound s : ParanoiaSound.values()) if (c.paranoiaOn(s)) on.add(s);
            if (!on.isEmpty()) queue.addAll(build(on.get(rnd.nextInt(on.size())), now));
            nextEventAt = now + randomGap(c);
        }
    }

    private void playDue(List<Pending> list, EntityPlayerSP p, long now) {
        for (int i = 0; i < list.size(); i++) {
            Pending s = list.get(i);
            if (now >= s.at) {
                play(p, s);
                list.remove(i--);
            }
        }
    }

    /** Average gap = the frequency setting, randomized between half and 1.5x so it's not predictable. */
    private long randomGap(HConfig c) {
        return (long) (c.paranoiaEvery * 1000 * (0.5 + rnd.nextDouble()));
    }

    /** The sequence of sounds for one event. */
    private List<Pending> build(ParanoiaSound s, long now) {
        List<Pending> out = new ArrayList<Pending>();
        // Behind you: within 70 degrees either side of straight back.
        float angle = 180f + (rnd.nextFloat() * 140f - 70f);
        switch (s) {
            case STEPS_WOOL:
            case STEPS_STONE:
            case STEPS_WOOD: { // Someone walking up behind you
                int steps = 4 + rnd.nextInt(6);
                float from = 10f + rnd.nextFloat() * 4f, to = 2.5f + rnd.nextFloat() * 2f;
                long interval = 300 + rnd.nextInt(120);
                for (int i = 0; i < steps; i++) {
                    float d = from + (to - from) * i / (steps - 1f);
                    out.add(new Pending(now + i * interval, s.sound, 0.25f, 0.9f + rnd.nextFloat() * 0.2f, d,
                            angle + rnd.nextFloat() * 6f - 3f));
                }
                break;
            }
            case BRIDGING: { // Wool being placed, getting closer
                int blocks = 4 + rnd.nextInt(6);
                float from = 12f, to = 4f + rnd.nextFloat() * 2f;
                long interval = 220 + rnd.nextInt(100);
                for (int i = 0; i < blocks; i++) {
                    float d = from + (to - from) * i / (blocks - 1f);
                    out.add(new Pending(now + i * interval, s.sound, 1.0f, 0.8f, d, angle));
                }
                break;
            }
            case WOOL_BREAK: { // Someone breaking 1-2 wool blocks by hand (no shears), like at your bed
                // Vanilla: wool by hand takes ~1.2 s. While mining, a soft low "hit" (step.cloth, quiet,
                // half pitch) plays every 4 ticks, then the break sound (dig.cloth, pitch 0.8).
                int blocks = 1 + rnd.nextInt(2);
                float d = 5f + rnd.nextFloat() * 6f;
                long t = now;
                for (int b = 0; b < blocks; b++) {
                    for (int hit = 0; hit < 6; hit++) {
                        out.add(new Pending(t + hit * 200, "step.cloth", 0.25f, 0.5f, d, angle));
                    }
                    out.add(new Pending(t + 1200, s.sound, 1.0f, 0.8f, d, angle));
                    t += 1200 + 250 + rnd.nextInt(200); // move to the next block
                }
                break;
            }
            case DRINKING: { // A potion: vanilla plays the gulp every 4 ticks for the last ~25 ticks of the drink
                float d = 3f + rnd.nextFloat() * 4f;
                for (int i = 0; i < 7; i++) {
                    out.add(new Pending(now + 350 + i * 200, s.sound, 0.5f, 0.9f + rnd.nextFloat() * 0.1f, d, angle));
                }
                break;
            }
            case EATING: { // A golden apple: several bites
                int bites = 5 + rnd.nextInt(3);
                float d = 3f + rnd.nextFloat() * 4f;
                for (int i = 0; i < bites; i++) {
                    out.add(new Pending(now + i * 200, s.sound, 0.5f, 0.9f + rnd.nextFloat() * 0.2f, d, angle));
                }
                break;
            }
            case BOW:
                out.add(new Pending(now, s.sound, 1.0f, 0.9f + rnd.nextFloat() * 0.3f, 4f + rnd.nextFloat() * 8f, angle));
                break;
            case FALL_SMALL:
            case FALL_BIG:
                out.add(new Pending(now, s.sound, 1.0f, 0.9f + rnd.nextFloat() * 0.2f, 3f + rnd.nextFloat() * 5f, angle));
                break;
            default: // TNT, fireball, pearl: one sound
                out.add(new Pending(now, s.sound, 1.0f, 1.0f,
                        4f + rnd.nextFloat() * 6f, angle));
        }
        return out;
    }

    private void play(EntityPlayerSP p, Pending s) {
        double yaw = Math.toRadians(p.rotationYaw + s.angle);
        double x = p.posX - Math.sin(yaw) * s.distance;
        double z = p.posZ + Math.cos(yaw) * s.distance;
        double y = p.posY + (rnd.nextFloat() - 0.5f);
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(s.sound),
                s.volume * Math.max(0f, Math.min(1f, Handicaps.config.paranoiaVolume)), s.pitch, (float) x, (float) y, (float) z));
    }

    /** A non-positional sound at a chosen volume (for Test buttons outside a world). */
    private static final class FlatSound extends net.minecraft.client.audio.PositionedSound {
        FlatSound(ResourceLocation sound, float volume) {
            super(sound);
            this.volume = volume;
            this.pitch = 1.0f;
            this.attenuationType = net.minecraft.client.audio.ISound.AttenuationType.NONE;
        }
    }
}
