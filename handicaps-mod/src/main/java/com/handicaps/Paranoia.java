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
 * Only sounds you'd actually hear in Bedwars (the "Bedwars" sound set); other modes can get their own sets.
 * Client-side only: nobody else hears them, nothing is sent to the server.
 */
final class Paranoia {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rnd = new Random();
    private long nextEventAt;

    /** A sound waiting to play: when, what, where (as distance/angle behind the player). */
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

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        HConfig c = Handicaps.config;
        EntityPlayerSP p = mc.thePlayer;
        long now = System.currentTimeMillis();
        if (p == null || mc.theWorld == null || !c.active() || !c.paranoia) {
            queue.clear();
            nextEventAt = 0;
            return;
        }
        if (nextEventAt == 0) nextEventAt = now + randomGap(c);

        // Play anything that's due.
        for (int i = 0; i < queue.size(); i++) {
            Pending s = queue.get(i);
            if (now >= s.at) {
                play(p, s);
                queue.remove(i--);
            }
        }

        if (now >= nextEventAt && queue.isEmpty()) {
            startEvent(c, now);
            nextEventAt = now + randomGap(c);
        }
    }

    /** Average gap = the frequency setting, randomized between half and 1.5x so it's not predictable. */
    private long randomGap(HConfig c) {
        return (long) (c.paranoiaEvery * 1000 * (0.5 + rnd.nextDouble()));
    }

    private void startEvent(HConfig c, long now) {
        List<Integer> kinds = new ArrayList<Integer>();
        if (c.paranoiaFootsteps) kinds.add(0);
        if (c.paranoiaWool) {
            kinds.add(1);
            kinds.add(2);
        }
        if (c.paranoiaFall) kinds.add(3);
        if (c.paranoiaCombat) kinds.add(4);
        if (c.paranoiaItems) kinds.add(5);
        if (kinds.isEmpty()) return;

        // Behind you: within 70 degrees either side of straight back.
        float angle = 180f + (rnd.nextFloat() * 140f - 70f);
        switch (kinds.get(rnd.nextInt(kinds.size()))) {
            case 0: { // Someone sneaking/walking up behind you
                String[] surfaces = {"step.cloth", "step.cloth", "step.stone", "step.wood"};
                String surface = surfaces[rnd.nextInt(surfaces.length)];
                int steps = 4 + rnd.nextInt(6);
                float from = 10f + rnd.nextFloat() * 4f, to = 2.5f + rnd.nextFloat() * 2f;
                long interval = 300 + rnd.nextInt(120);
                for (int i = 0; i < steps; i++) {
                    float d = from + (to - from) * i / (steps - 1f);
                    queue.add(new Pending(now + i * interval, surface, 0.25f, 0.9f + rnd.nextFloat() * 0.2f, d,
                            angle + rnd.nextFloat() * 6f - 3f));
                }
                break;
            }
            case 1: { // Someone bridging toward you
                int blocks = 4 + rnd.nextInt(6);
                float from = 12f, to = 4f + rnd.nextFloat() * 2f;
                long interval = 220 + rnd.nextInt(100);
                for (int i = 0; i < blocks; i++) {
                    float d = from + (to - from) * i / (blocks - 1f);
                    queue.add(new Pending(now + i * interval, "dig.cloth", 1.0f, 0.8f, d, angle));
                }
                break;
            }
            case 2: { // Someone breaking wool (your bed defense?)
                int breaks = 1 + rnd.nextInt(3);
                float d = 5f + rnd.nextFloat() * 6f;
                for (int i = 0; i < breaks; i++) {
                    queue.add(new Pending(now + i * (350 + rnd.nextInt(250)), "dig.cloth", 1.0f, 0.8f, d, angle));
                }
                break;
            }
            case 3: // Someone landing behind you
                queue.add(new Pending(now, rnd.nextBoolean() ? "game.player.hurt.fall.small" : "game.player.hurt.fall.big",
                        1.0f, 0.9f + rnd.nextFloat() * 0.2f, 3f + rnd.nextFloat() * 5f, angle));
                break;
            case 4: { // Combat: bow, TNT, fireball, pearl
                String[] combat = {"random.bow", "game.tnt.primed", "mob.ghast.fireball", "mob.endermen.portal"};
                String s = combat[rnd.nextInt(combat.length)];
                queue.add(new Pending(now, s, 1.0f, s.equals("random.bow") ? 0.9f + rnd.nextFloat() * 0.3f : 1.0f,
                        4f + rnd.nextFloat() * 8f, angle));
                break;
            }
            default: { // Eating a golden apple / drinking a potion
                boolean eat = rnd.nextBoolean();
                int n = eat ? 5 + rnd.nextInt(3) : 1;
                float d = 3f + rnd.nextFloat() * 4f;
                for (int i = 0; i < n; i++) {
                    queue.add(new Pending(now + i * 200, eat ? "random.eat" : "random.drink",
                            0.5f, 0.9f + rnd.nextFloat() * 0.2f, d, angle));
                }
            }
        }
    }

    private void play(EntityPlayerSP p, Pending s) {
        // Angle is relative to where you're facing, so the sound stays behind you even if you turn a little.
        double yaw = Math.toRadians(p.rotationYaw + s.angle);
        double x = p.posX - Math.sin(yaw) * s.distance;
        double z = p.posZ + Math.cos(yaw) * s.distance;
        double y = p.posY + (rnd.nextFloat() - 0.5f);
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(s.sound),
                s.volume, s.pitch, (float) x, (float) y, (float) z));
    }
}
