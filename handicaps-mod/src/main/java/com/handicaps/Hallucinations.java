package com.handicaps;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
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

        // ---- walking over real terrain (players / invisible players) ----

        /** Floor height (feet level) at evenly spaced points from start to end; null = flat at sy. */
        int[] floors;
        /** Body height and vertical speed, simulated with vanilla physics (jump 0.42, gravity 0.08, drag 0.98). */
        double bodyY = Double.NaN, vy;
        boolean inAir;

        double floorAt(double t) {
            if (floors == null) return y(t);
            return floors[(int) Math.round(Math.max(0, Math.min(1, t)) * (floors.length - 1))];
        }

        /**
         * Advances the body one tick and returns its height. Jumps up a 1-block step shortly before reaching it
         * (a jump needs ~3 ticks to clear a block), falls off ledges, and can sprint-jump on flat ground.
         * Returns +1 when it just landed (caller plays a landing sound), else 0, via {@link #landed}.
         */
        double body(double t, boolean wantJump) {
            landed = false;
            double now = floorAt(t), ahead = floorAt(t + 3.0 / ticks);
            // Parkour: the hole starts on the next step, so jump right at the edge for the longest jump.
            boolean holeNext = floorAt(t + 1.0 / ticks) == GAP;
            if (Double.isNaN(bodyY)) bodyY = now;
            if (!inAir) {
                if (ahead > bodyY + 0.01 || wantJump || holeNext) {
                    inAir = true;
                    vy = 0.42;
                } else if (now < bodyY - 0.01) {
                    inAir = true; // walked off a ledge
                    vy = 0;
                } else {
                    bodyY = now;
                }
            }
            if (inAir) {
                bodyY += vy;
                vy = (vy - 0.08) * 0.98;
                // Never sink into the step being climbed or the far side of a hole (over the hole, now = GAP).
                if (bodyY < now) bodyY = now;
                if (vy < 0 && bodyY <= now + 0.001) {
                    bodyY = now;
                    inAir = false;
                    landed = true;
                }
            }
            return bodyY;
        }

        boolean landed;
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
            ItemStack sword = seenSword(profile.getName()); // the sword they really had, if we've seen it
            p.inventory.setInventorySlotContents(0, sword != null ? sword
                    : new ItemStack(rnd.nextBoolean() ? Items.iron_sword : Items.stone_sword));
            dress(p, profile.getName());
            p.inventory.currentItem = 0;
            float yaw = (float) Math.toDegrees(Math.atan2(-(ex - sx), ez - sz));
            p.setPositionAndRotation(sx, sy, sz, yaw, 0);
            p.rotationYawHead = yaw;
            p.setSprinting(true);
            entity = p;
        }

        /** About half of the fakes sprint-jump at you, chaining jumps like a real player. */
        final boolean jumper = rnd.nextBoolean();
        @Override
        void place(double t) {
            FakePlayer p = (FakePlayer) entity;
            float yaw = (float) Math.toDegrees(Math.atan2(-(ex - sx), ez - sz));
            double x = x(t), z = z(t);
            double y = body(t, jumper && t < 0.9 && hasHeadroom(x, floorAt(t), z));
            if (landed) sound(rnd.nextBoolean() ? "step.cloth" : "step.stone", 0.3f, 1.0f, x, y, z);

            // Interpolated movement, exactly how real players arrive from the server (legs animate).
            p.setPositionAndRotation2(x, y, z, yaw, 0, 3, false);
            p.rotationYawHead = yaw;
            if (!inAir && age % 6 == 0) {
                sound(rnd.nextBoolean() ? "step.cloth" : "step.stone", 0.25f, 1.0f, x, y, z);
            }
            if (t > 0.75 && age % 4 == 0) p.swingItem(); // swinging as it gets close
        }

        /** Room to jump: the block above head height must be free too. */
        private boolean hasHeadroom(double x, double y, double z) {
            return !solid((int) Math.floor(x), (int) y + 2, (int) Math.floor(z));
        }
    }

    // ---- invisible player: footprints and footsteps walking toward you, nobody there ----

    private final class InvisibleFake extends Fake {
        private boolean leftFoot;

        InvisibleFake(double sx, double sy, double sz, double ex, double ey, double ez, int ticks) {
            super(sx, sy, sz, ex, ey, ez, ticks);
        }

        @Override
        void place(double t) {
            double y = body(t, false); // they jump up steps and drop off ledges too
            if (landed) sound(rnd.nextBoolean() ? "step.cloth" : "step.stone", 0.3f, 1.0f, x(t), y, z(t));
            if (inAir || age % 4 != 0) return; // a step every 0.2 s while running; no footprints mid-jump
            // Footprints alternate left and right of the walking line, like the invisibility footprints in Bedwars.
            double dx = ex - sx, dz = ez - sz, len = Math.sqrt(dx * dx + dz * dz);
            double side = (leftFoot ? 1 : -1) * 0.15;
            leftFoot = !leftFoot;
            double x = x(t) + (-dz / len) * side, z = z(t) + (dx / len) * side;
            mc.theWorld.spawnParticle(EnumParticleTypes.FOOTSTEP, x, y + 0.01, z, 0, 0, 0);
            sound(rnd.nextBoolean() ? "step.cloth" : "step.stone", 0.2f, 1.0f, x, y, z);
        }
    }

    private void spawnInvisible() {
        EntityPlayerSP p = mc.thePlayer;
        double y = Math.floor(p.posY + 0.01);
        // They run past you (just beside you, or right through on a 1-wide bridge) and keep going 8-15 blocks.
        double[] sideOffsets = {0.9, -0.9, 0.5, -0.5, 0.0};
        for (int attempt = 0; attempt < 24; attempt++) {
            // Can come from anywhere around you, including behind.
            double yaw = Math.toRadians(p.rotationYaw + rnd.nextDouble() * 360), dist = 8 + rnd.nextDouble() * 6;
            double sx = p.posX - Math.sin(yaw) * dist, sz = p.posZ + Math.cos(yaw) * dist;
            double ux = (p.posX - sx) / dist, uz = (p.posZ - sz) / dist; // direction toward you
            for (double off : sideOffsets) {
                double px = p.posX - uz * off, pz = p.posZ + ux * off;   // where they pass you
                double sx2 = px - ux * dist, sz2 = pz - uz * dist;       // start, lined up with that pass
                double beyond = 8 + rnd.nextDouble() * 7;
                double ex = px + ux * beyond, ez = pz + uz * beyond;     // and carry on past you
                // Terrain along the whole run, traced outward from where they pass you (at your floor level).
                int n = Math.max(2, (int) ((dist + beyond) / 0.25));
                int[] floors = terrain(sx2, sz2, ex, ez, (int) Math.round(n * dist / (dist + beyond)), (int) y, n);
                if (floors == null) continue;
                int ticks = Math.max(10, (int) ((dist + beyond) / 0.28)); // sprinting ~5.6 blocks/s
                InvisibleFake f = new InvisibleFake(sx2, floors[0], sz2, ex, floors[n], ez, ticks);
                f.floors = floors;
                add(f);
                return;
            }
        }
    }

    // ---- arrow fly-by: shot from far away, whizzes past your head, sticks in something behind you ----

    /** Vanilla arrow physics per tick: move, then air drag x0.99, then gravity -0.05. */
    private static final double ARROW_DRAG = 0.99, ARROW_GRAVITY = 0.05;

    private final class ArrowFake extends Fake {
        private double x, y, z, vx, vy, vz;

        ArrowFake(double sx, double sy, double sz, double vx, double vy, double vz, double ex, double ey, double ez,
                  int ticks) {
            super(sx, sy, sz, ex, ey, ez, ticks);
            x = sx;
            y = sy;
            z = sz;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            net.minecraft.entity.projectile.EntityArrow a = new net.minecraft.entity.projectile.EntityArrow(mc.theWorld);
            a.setPosition(sx, sy, sz);
            aim(a);
            a.prevRotationYaw = a.rotationYaw;
            a.prevRotationPitch = a.rotationPitch;
            entity = a;
            sound("random.bow", 0.7f, 1.0f / (rnd.nextFloat() * 0.4f + 1.2f) + 0.5f, sx, sy, sz); // the distant shot
        }

        /** Points the arrow along its current velocity, like vanilla (so it tips nose-down as it falls). */
        private void aim(Entity a) {
            a.rotationYaw = (float) Math.toDegrees(Math.atan2(vx, vz));
            a.rotationPitch = (float) Math.toDegrees(Math.atan2(vy, Math.sqrt(vx * vx + vz * vz)));
        }

        @Override
        void place(double t) {
            Entity a = entity;
            a.prevRotationYaw = a.rotationYaw;
            a.prevRotationPitch = a.rotationPitch;
            x += vx;
            y += vy;
            z += vz;
            vx *= ARROW_DRAG;
            vy *= ARROW_DRAG;
            vz *= ARROW_DRAG;
            vy -= ARROW_GRAVITY;
            a.motionX = a.motionY = a.motionZ = 0; // we move it ourselves
            a.setPosition(x, y, z);
            aim(a);
        }

        @Override
        void finish() {
            sound("random.bowhit", 1.0f, 1.2f / (rnd.nextFloat() * 0.2f + 0.9f), x, y, z); // thunk
        }
    }

    private void spawnArrow() {
        EntityPlayerSP p = mc.thePlayer;
        double[] s = startPoint(20 + rnd.nextDouble() * 15, 1 + rnd.nextDouble() * 3);
        // Passes about a block beside your head, then keeps going 8 blocks behind you.
        double look = Math.toRadians(p.rotationYaw), side = look + Math.PI / 2;
        double passOffset = (rnd.nextBoolean() ? 1 : -1) * (0.8 + rnd.nextDouble() * 0.6);
        double px = p.posX - Math.sin(side) * passOffset, pz = p.posZ + Math.cos(side) * passOffset;
        double py = p.posY + p.getEyeHeight();
        double hx = px - s[0], hz = pz - s[2], dist = Math.sqrt(hx * hx + hz * hz);
        hx /= dist;
        hz /= dist;
        // Launch speed: a bow pulled 70-100% (full draw is 3 blocks/tick). Find the launch angle whose real,
        // falling arc reaches your head height exactly when it passes you (lower of the two arcs).
        double speed = 3.0 * (0.7 + rnd.nextDouble() * 0.3);
        double lo = Math.toRadians(-30), hi = Math.toRadians(45);
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (heightAt(speed, mid, dist) < py - s[1]) lo = mid;
            else hi = mid;
        }
        double angle = (lo + hi) / 2;
        double vx = hx * speed * Math.cos(angle), vz = hz * speed * Math.cos(angle), vy = speed * Math.sin(angle);
        // Fly it ahead of time to know when it's 8 blocks past you (where it "thunks" into something).
        double x = 0, y = 0, svx = speed * Math.cos(angle), svy = vy;
        int ticks = 0;
        while (x < dist + 8 && ticks < 100) {
            x += svx;
            y += svy;
            svx *= ARROW_DRAG;
            svy = svy * ARROW_DRAG - ARROW_GRAVITY;
            ticks++;
        }
        add(new ArrowFake(s[0], s[1], s[2], vx, vy, vz, s[0] + hx * x, s[1] + y, s[2] + hz * x, ticks));
    }

    /** Height gained (blocks) by an arrow shot at this speed and angle once it has flown this far sideways. */
    private static double heightAt(double speed, double angle, double dist) {
        double x = 0, y = 0, vx = speed * Math.cos(angle), vy = speed * Math.sin(angle);
        for (int i = 0; i < 200 && x < dist; i++) {
            double step = Math.min(1, (dist - x) / vx); // last partial step, so we land exactly on dist
            x += vx * step;
            y += vy * step;
            vx *= ARROW_DRAG;
            vy = vy * ARROW_DRAG - ARROW_GRAVITY;
            if (vx < 1e-3) return Double.NEGATIVE_INFINITY;
        }
        return y;
    }

    // ---- fake bridger: someone speed-bridging toward you with wool that only exists on your screen ----

    /** A block that is only drawn; it is never put in the world (you can't touch, select or stand on it). */
    private static final class FakeBlock {
        final BlockPos pos;
        final IBlockState state;
        final Fake owner;

        FakeBlock(BlockPos pos, IBlockState state, Fake owner) {
            this.pos = pos;
            this.state = state;
            this.owner = owner;
        }
    }

    private final List<FakeBlock> fakeBlocks = new ArrayList<FakeBlock>();

    private final class BridgerFake extends Fake {
        private final int[] cx, cz;          // cells from the start to where they stop, in order
        private final boolean[] needsBlock;  // air under that cell: a fake block goes there first
        private final int[] segStart;        // tick at which they start moving onto each cell
        private final int y, wool;
        private final float faceYaw;
        private int reached;                 // last cell whose block has been placed / step started
        /** Speed bridging (stand up, only crouch for a moment at each edge) instead of holding crouch. */
        private final boolean speed;

        BridgerFake(int[] cx, int[] cz, boolean[] needsBlock, int[] segStart, int y, float faceYaw,
                    GameProfile profile, int wool, List<BlockPos> trail, boolean speed) {
            super(cx[0] + 0.5, y, cz[0] + 0.5, cx[cx.length - 1] + 0.5, y, cz[cz.length - 1] + 0.5,
                    segStart[cx.length - 1] + 12); // plus a short pause at the end before vanishing
            this.cx = cx;
            this.cz = cz;
            this.needsBlock = needsBlock;
            this.segStart = segStart;
            this.y = y;
            this.wool = wool;
            this.faceYaw = faceYaw;
            this.speed = speed;
            for (BlockPos b : trail) fakeBlocks.add(new FakeBlock(b, Blocks.wool.getStateFromMeta(wool), this));
            FakePlayer p = new FakePlayer(mc.theWorld, profile);
            p.inventory.setInventorySlotContents(0, new ItemStack(Blocks.wool, 64, wool));
            dress(p, profile.getName());
            p.inventory.currentItem = 0;
            p.setSneaking(true); // crouched at the edge, like a real bridger
            p.setPositionAndRotation(sx, sy, sz, faceYaw, 75);
            p.rotationYawHead = faceYaw;
            entity = p;
        }

        @Override
        void place(double t) {
            FakePlayer p = (FakePlayer) entity;
            int last = cx.length - 1;
            // They move from cell i-1 onto cell i between segStart[i-1] and segStart[i]; the block goes down first.
            while (reached < last && age >= segStart[reached]) {
                reached++;
                if (needsBlock[reached]) {
                    BlockPos pos = new BlockPos(cx[reached], y - 1, cz[reached]);
                    fakeBlocks.add(new FakeBlock(pos, Blocks.wool.getStateFromMeta(wool), this));
                    sound("dig.cloth", 1.0f, 0.8f, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    p.swingItem();
                }
            }
            // Position: back from cell i-1 to cell i during [segStart[i-1], segStart[i]].
            double x = cx[last] + 0.5, z = cz[last] + 0.5;
            for (int i = 1; i <= last; i++) {
                if (age < segStart[i]) {
                    double f = Math.max(0, (age - segStart[i - 1]) / (double) (segStart[i] - segStart[i - 1]));
                    x = cx[i - 1] + 0.5 + (cx[i] - cx[i - 1]) * f;
                    z = cz[i - 1] + 0.5 + (cz[i] - cz[i - 1]) * f;
                    break;
                }
            }
            // Crouching: slow bridgers hold it the whole way across; speed bridgers only tap it for ~2 ticks at
            // each edge as the block goes down; on real ground both stand up and walk.
            int cur = Math.max(1, reached);
            boolean sneak = needsBlock[cur] && (!speed || age - segStart[cur - 1] < 2);
            if (age >= segStart[last]) sneak = !speed; // stopped at the end
            p.setSneaking(sneak);
            p.setPositionAndRotation2(x, y, z, faceYaw, 75, 3, false);
            p.rotationYawHead = faceYaw;
        }

        @Override
        void finish() {
            for (int i = 0; i < fakeBlocks.size(); i++) {
                if (fakeBlocks.get(i).owner == this) fakeBlocks.remove(i--);
            }
        }
    }

    /**
     * Picks a line that crosses in front of you: they start just off-screen to your left or right, a few blocks
     * ahead, and bridge sideways across your view (backwards, crouched, like a real bridger), finishing near the
     * edge of your view on the other side. If they start over the void, a short bit of the bridge they've already
     * built trails behind them (off-screen). Returns false (and shows nothing) if there's no gap to bridge across.
     */
    private boolean spawnBridger() {
        EntityPlayerSP p = mc.thePlayer;
        int y = (int) Math.floor(p.posY + 0.01);
        int px = (int) Math.floor(p.posX), pz = (int) Math.floor(p.posZ);
        double look = Math.toRadians(p.rotationYaw);
        double lx = -Math.sin(look), lz = Math.cos(look);
        // Forward = the grid direction closest to where you look; they travel along the perpendicular.
        int fx, fz;
        if (Math.abs(lx) > Math.abs(lz)) {
            fx = lx > 0 ? 1 : -1;
            fz = 0;
        } else {
            fx = 0;
            fz = lz > 0 ? 1 : -1;
        }
        int firstSign = rnd.nextBoolean() ? 1 : -1;
        int[] aheads = {4, 5, 3, 6, 7};
        for (int ahead : aheads) {
            for (int s = 0; s < 2; s++) {
                int sign = s == 0 ? firstSign : -firstSign;
                int mx = -fz * sign, mz = fx * sign; // direction of travel (sideways across your view)
                // Start off-screen (more than ~60 degrees to the side), end just past the middle on the other side.
                int from = -(ahead * 2 + 1), to = ahead + 1 + rnd.nextInt(2);
                int n = to - from + 1;
                int[] cx = new int[n], cz = new int[n];
                boolean[] need = new boolean[n];
                int blocks = 0;
                boolean ok = true;
                for (int i = 0; i < n && ok; i++) {
                    int lat = from + i;
                    cx[i] = px + fx * ahead + mx * lat;
                    cz[i] = pz + fz * ahead + mz * lat;
                    if (solid(cx[i], y, cz[i]) || solid(cx[i], y + 1, cz[i])) ok = false; // body must fit
                    else if (solid(cx[i], y - 1, cz[i])) need[i] = false;               // real ground: walk
                    else if (mc.theWorld.isAirBlock(new BlockPos(cx[i], y - 1, cz[i]))) {
                        need[i] = true;
                        blocks++;
                    } else ok = false; // water, lava, plants: not a clean gap to bridge
                }
                if (!ok || blocks < 3) continue; // must actually bridge across a gap
                // Already partway across: a short trail of their bridge behind them, out of view.
                List<BlockPos> trail = new ArrayList<BlockPos>();
                if (need[0]) {
                    trail.add(new BlockPos(cx[0], y - 1, cz[0]));
                    int len = 3 + rnd.nextInt(5);
                    for (int k = 1; k <= len; k++) {
                        int tx = cx[0] - mx * k, tz = cz[0] - mz * k;
                        BlockPos below = new BlockPos(tx, y - 1, tz);
                        if (solid(tx, y, tz) || solid(tx, y + 1, tz) || !mc.theWorld.isAirBlock(below)) break;
                        trail.add(below);
                    }
                }
                // 1.8.9 speeds: walking 4.317 blocks/s, crouching x0.3 = 1.295 blocks/s (backwards is no slower).
                // Slow bridging holds crouch: a block every 15-17 ticks (~1.2-1.3 blocks/s). Speed bridging only
                // crouches at each edge: a block every 7-8 ticks (~2.5-2.9 blocks/s). Real ground: plain walking.
                boolean speed = rnd.nextFloat() < 0.4f;
                int[] segStart = new int[n];
                for (int i = 1; i < n; i++) {
                    int perBlock = speed ? 7 + rnd.nextInt(2) : 15 + rnd.nextInt(3);
                    segStart[i] = segStart[i - 1] + (need[i] ? perBlock : 5);
                }
                float yaw = (float) Math.toDegrees(Math.atan2(mx, -mz)); // facing back along their bridge
                GameProfile profile = randomProfile();
                add(new BridgerFake(cx, cz, need, segStart, y, yaw, profile, teamWool(profile.getName()), trail, speed));
                return true;
            }
        }
        return false;
    }

    /** Their team's wool color, from the scoreboard team prefix (Hypixel colors names by team). */
    private int teamWool(String name) {
        ScorePlayerTeam team = mc.theWorld.getScoreboard().getPlayersTeam(name);
        if (team == null) return 0;
        String pre = team.getColorPrefix();
        for (int i = 0; i + 1 < pre.length(); i++) {
            if (pre.charAt(i) != '§') continue;
            switch (Character.toLowerCase(pre.charAt(i + 1))) {
                case 'c': case '4': return 14; // red
                case '9': case '1': return 11; // blue
                case 'a': return 5;            // lime (green team)
                case '2': return 13;           // dark green
                case 'e': return 4;            // yellow
                case '6': return 1;            // orange
                case 'b': case '3': return 9;  // aqua -> cyan
                case 'd': return 6;            // pink
                case '5': return 10;           // purple
                case '8': return 7;            // gray
                case '7': return 8;            // light gray
                case '0': return 15;           // black
                case 'f': return 0;            // white
                default: break;                // formatting code (bold etc.): keep looking
            }
        }
        return 0;
    }

    /** Draws the fake blocks. Runs before the fog/blur pass so they get fogged like real blocks. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (fakeBlocks.isEmpty() || mc.theWorld == null) return;
        RenderManager rm = mc.getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        RenderHelper.enableStandardItemLighting(); // shaded faces, like real blocks
        mc.entityRenderer.enableLightmap();
        for (FakeBlock b : fakeBlocks) {
            int light = mc.theWorld.getCombinedLight(b.pos, 0); // same light as the air it sits in
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
            GlStateManager.pushMatrix();
            // +1 on z: the block-model renderer turns the model 90 degrees, which shifts it one block.
            GlStateManager.translate(b.pos.getX() - rm.viewerPosX, b.pos.getY() - rm.viewerPosY,
                    b.pos.getZ() - rm.viewerPosZ + 1);
            mc.getBlockRendererDispatcher().renderBlockBrightness(b.state, 1.0f);
            GlStateManager.popMatrix();
        }
        mc.entityRenderer.disableLightmap();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    // ---- fake "BED DESTROYED!" ----

    private void fakeBedDestroyed() {
        // The Hypixel Bedwars bed-break alert: big red title, subtitle, wither sound, chat line.
        mc.ingameGUI.displayTitle(null, null, 10, 60, 20); // timings first, like the server sends them
        mc.ingameGUI.displayTitle(null, "§fYou will no longer respawn!", -1, -1, -1);
        mc.ingameGUI.displayTitle("§c§lBED DESTROYED!", null, -1, -1, -1);
        EntityPlayerSP p = mc.thePlayer;
        sound("mob.wither.death", 1.0f, 1.0f, p.posX, p.posY, p.posZ);
        String by = randomProfile().getName();
        String color = teamColor(by); // their team's color, like the real message
        if (color == null) color = "§c";
        String[] how = {"was destroyed by", "was dismantled by", "was iced by", "got sent to the shadow realm by"};
        p.addChatMessage(new net.minecraft.util.ChatComponentText("\n§f§lBED DESTRUCTION > §7Your Bed "
                + how[rnd.nextInt(how.length)] + " " + color + by + "§7!\n"));
    }

    // ---- scheduling ----

    /** Test button: show this hallucination right now (even if Hallucinations are off). */
    void test(String key) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if ("fakeFireball".equals(key)) spawnFireball();
        else if ("fakePlayer".equals(key)) spawnPlayer();
        else if ("fakeInvisible".equals(key)) spawnInvisible();
        else if ("fakeArrow".equals(key)) spawnArrow();
        else if ("fakeBed".equals(key)) fakeBedDestroyed();
        else if ("fakeBridger".equals(key) && !spawnBridger() && mc.currentScreen != null) {
            // Only the Test button explains; a random one with no gap around you just doesn't happen.
            mc.ingameGUI.setRecordPlaying("§eNo gap in front of you to bridge across: stand near an edge", false);
        } else if ("fakeFill".equals(key)) {
            // Test: fill every bridge hole nearby (not just some) for 30 seconds, and say how many.
            testFillUntil = System.currentTimeMillis() + 30000;
            int n = scanFills(mc.theWorld, mc.thePlayer, true);
            mc.ingameGUI.setRecordPlaying(n == 0 ? "§eNo holes in bridges within 20 blocks"
                    : "§e" + n + " bridge hole" + (n == 1 ? "" : "s") + " filled with fake blocks for 30 s", false);
        }
    }

    // ---- fake bridge fill: holes in bridges look filled in, so you walk onto air ----

    /** Fake blocks currently filling holes, by position. */
    private final java.util.Map<BlockPos, FakeBlock> fills = new java.util.HashMap<BlockPos, FakeBlock>();
    /** Holes we decided to leave open (so the choice doesn't flicker every scan). */
    private final java.util.Set<BlockPos> leftOpen = new java.util.HashSet<BlockPos>();
    private long testFillUntil;

    /**
     * Finds holes in bridges within 20 blocks and fills some of them (all, for the Test button) with a
     * drawn-only copy of the bridge's block. Fills go away when the hole does (someone places a real block)
     * or when you move away. Returns how many holes are filled now.
     */
    private int scanFills(WorldClient w, EntityPlayerSP p, boolean all) {
        int px = (int) Math.floor(p.posX), py = (int) Math.floor(p.posY), pz = (int) Math.floor(p.posZ), r = 20;
        java.util.Set<BlockPos> holes = new java.util.HashSet<BlockPos>();
        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                for (int y = py - 5; y <= py + 1; y++) {
                    if (!w.isAirBlock(new BlockPos(x, y, z))) continue;
                    IBlockState fill = holeFill(w, x, y, z);
                    if (fill == null) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    holes.add(pos);
                    if (fills.containsKey(pos) || (!all && leftOpen.contains(pos))) continue;
                    if (all || rnd.nextBoolean()) { // about half of the holes
                        FakeBlock fb = new FakeBlock(pos, fill, null);
                        fills.put(pos, fb);
                        fakeBlocks.add(fb);
                        leftOpen.remove(pos);
                    } else {
                        leftOpen.add(pos);
                    }
                }
            }
        }
        // Holes that are gone (filled for real) or out of range: drop their fake block.
        for (java.util.Iterator<java.util.Map.Entry<BlockPos, FakeBlock>> it = fills.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<BlockPos, FakeBlock> en = it.next();
            if (!holes.contains(en.getKey())) {
                fakeBlocks.remove(en.getValue());
                it.remove();
            }
        }
        leftOpen.retainAll(holes);
        return fills.size();
    }

    private void clearFills() {
        for (FakeBlock fb : fills.values()) fakeBlocks.remove(fb);
        fills.clear();
        leftOpen.clear();
    }

    /**
     * If (x, y, z) is part of a 1- or 2-block hole in a straight bridge, returns the bridge's block to fill it
     * with; otherwise null. A hole: open air with a drop of 3+ blocks under it, and at least two of the same
     * full block on each side along the line (e.g. wool wool _ wool wool, or wool wool _ _ wool wool).
     */
    private IBlockState holeFill(WorldClient w, int x, int y, int z) {
        for (int k = 1; k <= 3; k++) if (!w.isAirBlock(new BlockPos(x, y - k, z))) return null; // must be a drop
        int[][] axes = {{1, 0}, {0, 1}};
        for (int[] a : axes) {
            for (int width = 1; width <= 2; width++) {
                for (int o = 0; o < width; o++) { // this cell is the o-th of the hole
                    boolean ok = true;
                    for (int g = -o; g < width - o && ok; g++) {
                        if (g != 0 && !w.isAirBlock(new BlockPos(x + a[0] * g, y, z + a[1] * g))) ok = false;
                    }
                    if (!ok) continue;
                    IBlockState s1 = bridgeBlock(w, x - a[0] * (o + 1), y, z - a[1] * (o + 1));
                    IBlockState s2 = bridgeBlock(w, x - a[0] * (o + 2), y, z - a[1] * (o + 2));
                    IBlockState s3 = bridgeBlock(w, x + a[0] * (width - o), y, z + a[1] * (width - o));
                    IBlockState s4 = bridgeBlock(w, x + a[0] * (width - o + 1), y, z + a[1] * (width - o + 1));
                    if (s1 == null || s2 == null || s3 == null || s4 == null) continue;
                    net.minecraft.block.Block b = s1.getBlock();
                    if (s2.getBlock() == b && s3.getBlock() == b && s4.getBlock() == b) return s1;
                }
            }
        }
        return null;
    }

    /** A full solid block (wool, clay, wood, end stone...), or null. */
    private IBlockState bridgeBlock(WorldClient w, int x, int y, int z) {
        IBlockState s = w.getBlockState(new BlockPos(x, y, z));
        net.minecraft.block.Block b = s.getBlock();
        return b.isFullCube() && b.getMaterial().blocksMovement() ? s : null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = mc.thePlayer;
        WorldClient w = mc.theWorld;
        if (p == null || w == null) {
            active.clear();
            fakeBlocks.clear();
            fills.clear();
            leftOpen.clear();
            nextAt = 0;
            return;
        }
        if (p.ticksExisted % 20 == 0) trackGear(w); // remember everyone's armor once a second
        // Move/finish running hallucinations (Test buttons included).
        for (int i = 0; i < active.size(); i++) {
            Fake f = active.get(i);
            f.age++;
            double t = Math.min(1.0, f.age / (double) f.ticks);
            f.place(t);
            if (t >= 1.0) {
                f.finish();
                if (f.entity != null) w.removeEntityFromWorld(f.id);
                active.remove(i--);
            }
        }

        HConfig c = Handicaps.config;
        long now = System.currentTimeMillis();
        // Bridge holes: kept up to date twice a second while on (or for 30 s after its Test button).
        boolean testing = now < testFillUntil;
        if ((c.active() && c.hallucinations && c.fakeFill) || testing) {
            if (p.ticksExisted % 10 == 0) scanFills(w, p, testing);
        } else if (!fills.isEmpty() || !leftOpen.isEmpty()) {
            clearFills();
        }
        if (!c.active() || !c.hallucinations) {
            nextAt = 0;
            return;
        }
        if (nextAt == 0) nextAt = now + gap(c);
        if (now >= nextAt && mc.currentScreen == null) {
            List<String> on = new ArrayList<String>();
            if (c.fakeFireballs) on.add("fakeFireball");
            if (c.fakePlayers) on.add("fakePlayer");
            if (c.fakeInvisible) on.add("fakeInvisible");
            if (c.fakeArrow) on.add("fakeArrow");
            if (c.fakeBed) on.add("fakeBed");
            if (c.fakeBridger) on.add("fakeBridger");
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
        // From 18 blocks (the minimum) up to 2.5x that (45 blocks), so some arrive fast and some from far off.
        double[] s = startPoint(18 + rnd.nextDouble() * 27, 2 + rnd.nextDouble() * 4);
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
            // Terrain traced back from you: steps up/down of one block are fine (they jump / drop).
            int n = Math.max(2, (int) ((len - 1.5) / 0.25));
            int[] floors = terrain(s[0], s[2], ex, ez, n, (int) y, n);
            if (floors == null) continue;
            int ticks = Math.max(10, (int) ((len - 1.5) / 0.28)); // sprint speed ~5.6 blocks/s
            PlayerFake f = new PlayerFake(s[0], floors[0], s[2], ex, y, ez, ticks, randomProfile());
            f.floors = floors;
            add(f);
            return;
        }
        spawnFireball();
    }

    /**
     * Traces the ground along the line a..b at n+1 evenly spaced points, starting at point {@code anchor} on floor
     * {@code anchorFloor} and walking outward both ways. Each point may be one block higher (a step they jump up)
     * or one block lower (a ledge they drop off) than its neighbour. Holes up to 3 blocks wide are jumped over
     * like parkour (landing at the same height; or one up/down for a 1-block gap); those points are {@link #GAP}.
     * Walls of 2+ blocks, wider holes, the void, or no room for a body make the path invalid (null).
     * Returns the floor (feet) height at each point.
     */
    private int[] terrain(double ax, double az, double bx, double bz, int anchor, int anchorFloor, int n) {
        int[] f = new int[n + 1];
        if (!stand(cell(ax, bx, anchor, n), anchorFloor, cell(az, bz, anchor, n))) return null;
        f[anchor] = anchorFloor;
        double spacing = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az)) / n;
        for (int dir = -1; dir <= 1; dir += 2) {
            int i = anchor + dir;
            while (i >= 0 && i <= n) {
                int x = cell(ax, bx, i, n), z = cell(az, bz, i, n), prev = f[i - dir];
                if (stand(x, prev, z)) f[i] = prev;
                else if (stand(x, prev + 1, z) && !solid(cell(ax, bx, i - dir, n), prev + 2, cell(az, bz, i - dir, n))) {
                    f[i] = prev + 1; // one block up (room to jump from the lower side)
                } else if (stand(x, prev - 1, z) && !solid(x, prev + 1, z)) {
                    f[i] = prev - 1; // one block down
                } else {
                    // A hole? Look for ground on the other side within a sprint-jump (3 blocks), with open air
                    // above the hole for the jump arc.
                    int land = -1;
                    for (int j = i; j >= 0 && j <= n; j += dir) {
                        double across = Math.abs(j - (i - dir)) * spacing; // from the take-off point
                        if (across > 3.6) break;
                        int jx = cell(ax, bx, j, n), jz = cell(az, bz, j, n);
                        if (stand(jx, prev, jz)) {
                            land = j;
                            f[j] = prev;
                        } else if (across <= 1.6 && stand(jx, prev - 1, jz) && !solid(jx, prev + 1, jz)) {
                            land = j;
                            f[j] = prev - 1;
                        } else if (across <= 1.6 && stand(jx, prev + 1, jz) && !solid(jx, prev + 2, jz)) {
                            land = j;
                            f[j] = prev + 1;
                        }
                        if (land >= 0) break;
                        boolean open = !solid(jx, prev - 1, jz) && !solid(jx, prev, jz)
                                && !solid(jx, prev + 1, jz) && !solid(jx, prev + 2, jz);
                        if (!open) break; // a wall or a block in the way of the jump
                    }
                    if (land < 0) return null;
                    for (int j = i; j != land; j += dir) f[j] = GAP;
                    i = land;
                }
                i += dir;
            }
        }
        return f;
    }

    /** Floor value for a point over a hole being jumped (no ground there). */
    static final int GAP = Integer.MIN_VALUE / 2;

    private static int cell(double a, double b, int i, int n) {
        return (int) Math.floor(a + (b - a) * i / n);
    }

    /** Somewhere a player can stand: solid block under the feet, feet and head clear. */
    private boolean stand(int x, int y, int z) {
        return solid(x, y - 1, z) && !solid(x, y, z) && !solid(x, y + 1, z);
    }

    private boolean solid(int x, int y, int z) {
        return mc.theWorld.getBlockState(new net.minecraft.util.BlockPos(x, y, z)).getBlock().getMaterial().blocksMovement();
    }

    /**
     * Copies an enemy from the tab list (so the fake has their skin and name): someone on another team, never
     * a teammate. Outside team games (no teams at all) anyone but you; with nobody else, a default player.
     */
    private GameProfile randomProfile() {
        // Teams are told apart by name color (Hypixel colors every name by team).
        String myColor = teamColor(mc.thePlayer.getName());
        List<GameProfile> pool = new ArrayList<GameProfile>();
        if (mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                GameProfile gp = info.getGameProfile();
                if (gp == null || gp.getName() == null || !gp.getName().matches("[A-Za-z0-9_]{3,16}")
                        || gp.getId().equals(mc.thePlayer.getUniqueID())) continue;
                if (myColor != null) {
                    String color = teamColor(gp.getName());
                    // Teammate (same color), or no team (spectator / eliminated): skip.
                    if (color == null || color.equals(myColor) || color.equals("§7")) continue;
                }
                pool.add(gp);
            }
        }
        if (!pool.isEmpty()) {
            GameProfile gp = pool.get(rnd.nextInt(pool.size()));
            return new GameProfile(gp.getId(), gp.getName());
        }
        return new GameProfile(UUID.randomUUID(), "Player" + (100 + rnd.nextInt(900)));
    }

    /** The color code their name is shown in (e.g. "§c" for red team), from the scoreboard team; null if none. */
    private String teamColor(String name) {
        ScorePlayerTeam team = mc.theWorld.getScoreboard().getPlayersTeam(name);
        if (team == null) return null;
        String pre = team.getColorPrefix();
        for (int i = 0; i + 1 < pre.length(); i++) {
            char c = Character.toLowerCase(pre.charAt(i + 1));
            if (pre.charAt(i) == '§' && "0123456789abcdef".indexOf(c) >= 0) return "§" + c;
        }
        return null;
    }

    // ---- armor tracking: fakes wear what that player was really wearing ----

    /** Last armor (boots..helmet) and sword seen on each real player your game has loaded. */
    private final java.util.Map<String, ItemStack[]> seenGear = new java.util.HashMap<String, ItemStack[]>();
    private WorldClient gearWorld;

    private void trackGear(WorldClient w) {
        if (w != gearWorld) { // new game or new server: old gear is meaningless
            seenGear.clear();
            gearWorld = w;
        }
        for (Object o : w.playerEntities) {
            if (!(o instanceof EntityOtherPlayerMP) || o instanceof FakePlayer) continue;
            EntityOtherPlayerMP other = (EntityOtherPlayerMP) o;
            ItemStack[] gear = new ItemStack[5];
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                ItemStack a = other.getCurrentArmor(i);
                gear[i] = a == null ? null : a.copy();
                any |= a != null;
            }
            ItemStack held = other.getHeldItem();
            ItemStack[] old = seenGear.get(other.getName());
            if (held != null && held.getItem() instanceof net.minecraft.item.ItemSword) gear[4] = held.copy();
            else if (old != null) gear[4] = old[4]; // keep the last sword we saw them hold
            if (any || gear[4] != null) seenGear.put(other.getName(), gear);
        }
    }

    /** Puts the player's real (last seen) armor on the fake, or their team's default leather if never seen. */
    private void dress(FakePlayer p, String name) {
        ItemStack[] gear = seenGear.get(name);
        if (gear != null) {
            for (int i = 0; i < 4; i++) p.inventory.armorInventory[i] = gear[i] == null ? null : gear[i].copy();
            return;
        }
        // Bedwars start: full leather dyed in the team color.
        int rgb = teamRgb(teamColor(name));
        if (rgb < 0) return; // not in a team game: no armor
        net.minecraft.item.Item[] leather = {Items.leather_boots, Items.leather_leggings, Items.leather_chestplate,
                Items.leather_helmet};
        for (int i = 0; i < 4; i++) {
            ItemStack s = new ItemStack(leather[i]);
            ((net.minecraft.item.ItemArmor) leather[i]).setColor(s, rgb);
            p.inventory.armorInventory[i] = s;
        }
    }

    /** The sword they were last seen holding, or null. */
    private ItemStack seenSword(String name) {
        ItemStack[] gear = seenGear.get(name);
        return gear == null || gear[4] == null ? null : gear[4].copy();
    }

    private static int teamRgb(String code) {
        if (code == null) return -1;
        switch (code.charAt(1)) {
            case 'c': case '4': return 0xFF0000;
            case '9': case '1': return 0x0000FF;
            case 'a': case '2': return 0x00FF00;
            case 'e': return 0xFFFF00;
            case '6': return 0xFF8000;
            case 'b': case '3': return 0x00FFFF;
            case 'd': return 0xFF55FF;
            case '5': return 0x8000FF;
            case '8': case '7': return 0x808080;
            case '0': return 0x000000;
            default: return 0xFFFFFF;
        }
    }

    private void add(Fake f) {
        if (f.entity != null) mc.theWorld.addEntityToWorld(f.id, f.entity);
        active.add(f);
    }

    private void sound(String name, float volume, float pitch, double x, double y, double z) {
        float v = volume * Math.max(0f, Math.min(1f, Handicaps.config.paranoiaVolume));
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(name), v, pitch,
                (float) x, (float) y, (float) z));
    }
}
