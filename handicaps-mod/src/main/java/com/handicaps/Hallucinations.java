package com.handicaps;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Hallucinations: things that only exist on your screen.
 *
 * The fake entities are added to your client's world only; the server never hears about them and nobody
 * else sees them. They can't be targeted (clicks pass straight through), because hitting an entity the
 * server doesn't know about is something anticheats watch for.
 */
final class Hallucinations {

    static Hallucinations instance;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rnd = new Random();
    private long nextAt;
    private int nextFakeId = -1_000_000;
    private final List<Fake> active = new ArrayList<Fake>();

    Hallucinations() {
        instance = this;
    }

    /** One running hallucination: an entity flying/running from start to end, then gone. */
    private abstract class Fake {
        final int id = nextFakeId--;
        final double sx, sy, sz, ex, ey, ez;
        final int ticks;
        int age;
        Entity entity;

        Fake(double sx, double sy, double sz, double ex, double ey, double ez, int ticks) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.ticks = ticks;
        }

        double x(double t) {
            return sx + (ex - sx) * t;
        }

        double y(double t) {
            return sy + (ey - sy) * t;
        }

        double z(double t) {
            return sz + (ez - sz) * t;
        }

        abstract void place(double t);

        void finish() {
        }
    }

    // ---- fake fireball ----

    /** A fireball that can't be hit or collided with. */
    private static final class FakeFireball extends EntityLargeFireball {
        FakeFireball(World w) {
            super(w);
        }

        @Override
        public boolean canBeCollidedWith() {
            return false;
        }
    }

    private final class FireballFake extends Fake {
        FireballFake(double sx, double sy, double sz, double ex, double ey, double ez, int ticks) {
            super(sx, sy, sz, ex, ey, ez, ticks);
            FakeFireball f = new FakeFireball(mc.theWorld);
            f.accelerationX = f.accelerationY = f.accelerationZ = 0; // we move it ourselves
            f.setPosition(sx, sy, sz);
            entity = f;
            sound("mob.ghast.fireball", 1.0f, 1.0f, sx, sy, sz);
        }

        @Override
        void place(double t) {
            entity.motionX = entity.motionY = entity.motionZ = 0;
            entity.setPosition(x(t), y(t), z(t));
        }

        @Override
        void finish() {
            // "Explodes" next to you: sound and particles only. No damage, no knockback, no broken blocks.
            sound("random.explode", 4.0f, (1.0f + (rnd.nextFloat() - rnd.nextFloat()) * 0.2f) * 0.7f, ex, ey, ez);
            mc.theWorld.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, ex, ey, ez, 0, 0, 0);
            for (int i = 0; i < 8; i++) {
                mc.theWorld.spawnParticle(EnumParticleTypes.SMOKE_LARGE, ex + rnd.nextGaussian() * 0.8,
                        ey + rnd.nextGaussian() * 0.5, ez + rnd.nextGaussian() * 0.8, 0, 0.05, 0);
            }
        }
    }

    // ---- fake player ----

    /** A player that can't be hit or collided with. */
    private static final class FakePlayer extends EntityOtherPlayerMP {
        FakePlayer(World w, GameProfile p) {
            super(w, p);
        }

        @Override
        public boolean canBeCollidedWith() {
            return false;
        }

        @Override
        public boolean canBePushed() {
            return false;
        }
    }

    private final class PlayerFake extends Fake {
        PlayerFake(double sx, double sy, double sz, double ex, double ey, double ez, int ticks, GameProfile profile) {
            super(sx, sy, sz, ex, ey, ez, ticks);
            FakePlayer p = new FakePlayer(mc.theWorld, profile);
            p.inventory.setInventorySlotContents(0, new ItemStack(rnd.nextBoolean() ? Items.iron_sword : Items.stone_sword));
            p.inventory.currentItem = 0;
            float yaw = (float) Math.toDegrees(Math.atan2(-(ex - sx), ez - sz));
            p.setPositionAndRotation(sx, sy, sz, yaw, 0);
            p.rotationYawHead = yaw;
            p.setSprinting(true);
            entity = p;
        }

        /** About half of the fakes sprint-jump at you, chaining jumps like a real player. */
        final boolean jumper = rnd.nextBoolean();
        /** Current jump, simulated with vanilla physics: height above the floor and vertical speed. */
        double jumpHeight, jumpSpeed;
        boolean inAir;

        @Override
        void place(double t) {
            FakePlayer p = (FakePlayer) entity;
            float yaw = (float) Math.toDegrees(Math.atan2(-(ex - sx), ez - sz));
            double x = x(t), z = z(t);

            if (jumper && !inAir && t < 0.9 && hasHeadroom(x, y(t), z)) {
                inAir = true;
                jumpSpeed = 0.42; // vanilla jump velocity
            }
            if (inAir) {
                // Vanilla: move by velocity, then gravity 0.08 and air drag 0.98 per tick (~1.25 block peak).
                jumpHeight += jumpSpeed;
                jumpSpeed = (jumpSpeed - 0.08) * 0.98;
                if (jumpHeight <= 0) {
                    jumpHeight = 0;
                    inAir = false;
                    sound(rnd.nextBoolean() ? "step.cloth" : "step.stone", 0.3f, 1.0f, x, y(t), z); // landing
                }
            }

            // Interpolated movement, exactly how real players arrive from the server (legs animate).
            p.setPositionAndRotation2(x, y(t) + jumpHeight, z, yaw, 0, 3, false);
            p.rotationYawHead = yaw;
            if (!inAir && age % 6 == 0) {
                sound(rnd.nextBoolean() ? "step.cloth" : "step.stone", 0.25f, 1.0f, x, y(t), z);
            }
            if (t > 0.75 && age % 4 == 0) p.swingItem(); // swinging as it gets close
        }

        /** Room to jump: the block above head height must be free too. */
        private boolean hasHeadroom(double x, double y, double z) {
            return !solid((int) Math.floor(x), (int) y + 2, (int) Math.floor(z));
        }
    }

    // ---- scheduling ----

    /** Test button: show this hallucination right now (even if Hallucinations are off). */
    void test(String key) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if ("fakeFireball".equals(key)) spawnFireball();
        else if ("fakePlayer".equals(key)) spawnPlayer();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = mc.thePlayer;
        WorldClient w = mc.theWorld;
        if (p == null || w == null) {
            active.clear();
            nextAt = 0;
            return;
        }
        // Move/finish running hallucinations (Test buttons included).
        for (int i = 0; i < active.size(); i++) {
            Fake f = active.get(i);
            f.age++;
            double t = Math.min(1.0, f.age / (double) f.ticks);
            f.place(t);
            if (t >= 1.0) {
                f.finish();
                w.removeEntityFromWorld(f.id);
                active.remove(i--);
            }
        }

        HConfig c = Handicaps.config;
        long now = System.currentTimeMillis();
        if (!c.active() || !c.hallucinations) {
            nextAt = 0;
            return;
        }
        if (nextAt == 0) nextAt = now + gap(c);
        if (now >= nextAt && mc.currentScreen == null) {
            List<String> on = new ArrayList<String>();
            if (c.fakeFireballs) on.add("fakeFireball");
            if (c.fakePlayers) on.add("fakePlayer");
            if (!on.isEmpty()) test(on.get(rnd.nextInt(on.size())));
            nextAt = now + gap(c);
        }
    }

    private long gap(HConfig c) {
        return (long) (c.hallucinationEvery * 1000 * (0.5 + rnd.nextDouble()));
    }

    /** Somewhere in front of you (so you actually see it), within 50 degrees of where you look. */
    private double[] startPoint(double distance, double height) {
        EntityPlayerSP p = mc.thePlayer;
        double yaw = Math.toRadians(p.rotationYaw + (rnd.nextDouble() * 100 - 50));
        return new double[]{p.posX - Math.sin(yaw) * distance, p.posY + height, p.posZ + Math.cos(yaw) * distance};
    }

    private void spawnFireball() {
        EntityPlayerSP p = mc.thePlayer;
        double[] s = startPoint(18 + rnd.nextDouble() * 8, 2 + rnd.nextDouble() * 4);
        // Lands 2-3 blocks in front of you (where you're facing), a little to either side.
        double look = Math.toRadians(p.rotationYaw), side = look + Math.PI / 2;
        double ahead = 2 + rnd.nextDouble(), offset = (rnd.nextDouble() - 0.5) * 1.4;
        double ex = p.posX - Math.sin(look) * ahead - Math.sin(side) * offset;
        double ez = p.posZ + Math.cos(look) * ahead + Math.cos(side) * offset;
        double ey = p.posY + 0.3;
        double dist = Math.sqrt((ex - s[0]) * (ex - s[0]) + (ey - s[1]) * (ey - s[1]) + (ez - s[2]) * (ez - s[2]));
        add(new FireballFake(s[0], s[1], s[2], ex, ey, ez, Math.max(10, (int) (dist / 1.1))));
    }

    private void spawnPlayer() {
        EntityPlayerSP p = mc.thePlayer;
        double y = Math.floor(p.posY + 0.01); // stand on the same floor level as you
        // Only run where a real player could: try random directions until one has walkable ground all the
        // way to you. On a thin bridge over the void there may be none: then it's a fireball instead.
        for (int attempt = 0; attempt < 24; attempt++) {
            double[] s = startPoint(9 + rnd.nextDouble() * 9, 0);
            double dx = p.posX - s[0], dz = p.posZ - s[2], len = Math.sqrt(dx * dx + dz * dz);
            double ex = p.posX - dx / len * 1.5, ez = p.posZ - dz / len * 1.5;
            if (!walkable(s[0], y, s[2], ex, ez)) continue;
            int ticks = Math.max(10, (int) ((len - 1.5) / 0.28)); // sprint speed ~5.6 blocks/s
            add(new PlayerFake(s[0], y, s[2], ex, y, ez, ticks, randomProfile()));
            return;
        }
        spawnFireball();
    }

    /** Solid ground under every step of the path, and room for a body (feet and head clear). */
    private boolean walkable(double sx, double y, double sz, double ex, double ez) {
        double len = Math.sqrt((ex - sx) * (ex - sx) + (ez - sz) * (ez - sz));
        int steps = Math.max(1, (int) (len / 0.4));
        int by = (int) y;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int bx = (int) Math.floor(sx + (ex - sx) * t), bz = (int) Math.floor(sz + (ez - sz) * t);
            if (!solid(bx, by - 1, bz)) return false;           // nothing to stand on
            if (solid(bx, by, bz) || solid(bx, by + 1, bz)) return false; // wall in the way
        }
        return true;
    }

    private boolean solid(int x, int y, int z) {
        return mc.theWorld.getBlockState(new net.minecraft.util.BlockPos(x, y, z)).getBlock().getMaterial().blocksMovement();
    }

    /** Copies someone from the tab list (so the fake has their skin and name), or a default player. */
    private GameProfile randomProfile() {
        List<GameProfile> pool = new ArrayList<GameProfile>();
        if (mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                GameProfile gp = info.getGameProfile();
                if (gp != null && gp.getName() != null && gp.getName().matches("[A-Za-z0-9_]{3,16}")
                        && !gp.getId().equals(mc.thePlayer.getUniqueID())) {
                    pool.add(gp);
                }
            }
        }
        if (!pool.isEmpty()) {
            GameProfile gp = pool.get(rnd.nextInt(pool.size()));
            return new GameProfile(gp.getId(), gp.getName());
        }
        return new GameProfile(UUID.randomUUID(), "Player" + (100 + rnd.nextInt(900)));
    }

    private void add(Fake f) {
        mc.theWorld.addEntityToWorld(f.id, f.entity);
        active.add(f);
    }

    private void sound(String name, float volume, float pitch, double x, double y, double z) {
        float v = volume * Math.max(0f, Math.min(1f, Handicaps.config.paranoiaVolume));
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(name), v, pitch,
                (float) x, (float) y, (float) z));
    }
}
