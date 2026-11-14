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

    // ---- invisible player: footprints and footsteps walking toward you, nobody there ----

    private final class InvisibleFake extends Fake {
        private boolean leftFoot;

        InvisibleFake(double sx, double sy, double sz, double ex, double ey, double ez, int ticks) {
            super(sx, sy, sz, ex, ey, ez, ticks);
        }

        @Override
        void place(double t) {
            if (age % 4 != 0) return; // a step every 0.2 s: running
            // Footprints alternate left and right of the walking line, like the invisibility footprints in Bedwars.
            double dx = ex - sx, dz = ez - sz, len = Math.sqrt(dx * dx + dz * dz);
            double side = (leftFoot ? 1 : -1) * 0.15;
            leftFoot = !leftFoot;
            double x = x(t) + (-dz / len) * side, z = z(t) + (dx / len) * side;
            mc.theWorld.spawnParticle(EnumParticleTypes.FOOTSTEP, x, y(t) + 0.01, z, 0, 0, 0);
            sound(rnd.nextBoolean() ? "step.cloth" : "step.stone", 0.2f, 1.0f, x, y(t), z);
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
                if (!walkable(sx2, y, sz2, px, pz) || !walkable(px, y, pz, ex, ez)) continue;
                int ticks = Math.max(10, (int) ((dist + beyond) / 0.28)); // sprinting ~5.6 blocks/s
                add(new InvisibleFake(sx2, y, sz2, ex, y, ez, ticks));
                return;
            }
        }
    }

    // ---- arrow fly-by: shot from far away, whizzes past your head, sticks in something behind you ----

    private final class ArrowFake extends Fake {
        private final float yaw, pitch;

        ArrowFake(double sx, double sy, double sz, double ex, double ey, double ez, int ticks) {
            super(sx, sy, sz, ex, ey, ez, ticks);
            net.minecraft.entity.projectile.EntityArrow a = new net.minecraft.entity.projectile.EntityArrow(mc.theWorld);
            double dx = ex - sx, dy = ey - sy, dz = ez - sz;
            yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            a.setPositionAndRotation(sx, sy, sz, yaw, pitch);
            a.prevRotationYaw = yaw;
            a.prevRotationPitch = pitch;
            entity = a;
            sound("random.bow", 0.7f, 1.0f / (rnd.nextFloat() * 0.4f + 1.2f) + 0.5f, sx, sy, sz); // the distant shot
        }

        @Override
        void place(double t) {
            entity.motionX = entity.motionY = entity.motionZ = 0;
            entity.setPosition(x(t), y(t), z(t));
            entity.rotationYaw = entity.prevRotationYaw = yaw;
            entity.rotationPitch = entity.prevRotationPitch = pitch;
        }

        @Override
        void finish() {
            sound("random.bowhit", 1.0f, 1.2f / (rnd.nextFloat() * 0.2f + 0.9f), ex, ey, ez); // thunk
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
        double dx = px - s[0], dy = py - s[1], dz = pz - s[2], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ex = px + dx / len * 8, ey = py + dy / len * 8, ez = pz + dz / len * 8;
        int ticks = Math.max(6, (int) ((len + 8) / 2.0)); // arrows are fast: ~40 blocks/s
        add(new ArrowFake(s[0], s[1], s[2], ex, ey, ez, ticks));
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

        BridgerFake(int[] cx, int[] cz, boolean[] needsBlock, int[] segStart, int y, float faceYaw,
                    GameProfile profile, int wool) {
            super(cx[0] + 0.5, y, cz[0] + 0.5, cx[cx.length - 1] + 0.5, y, cz[cz.length - 1] + 0.5,
                    segStart[cx.length - 1] + 12); // plus a short pause at the end before vanishing
            this.cx = cx;
            this.cz = cz;
            this.needsBlock = needsBlock;
            this.segStart = segStart;
            this.y = y;
            this.wool = wool;
            this.faceYaw = faceYaw;
            FakePlayer p = new FakePlayer(mc.theWorld, profile);
            p.inventory.setInventorySlotContents(0, new ItemStack(Blocks.wool, 64, wool));
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
     * Finds a real place to bridge from: solid ground in front of you (or to the side), with open air
     * between it and you at your floor level. Returns false (and shows nothing) if there's no such spot.
     */
    private boolean spawnBridger() {
        EntityPlayerSP p = mc.thePlayer;
        int y = (int) Math.floor(p.posY + 0.01);
        int px = (int) Math.floor(p.posX), pz = (int) Math.floor(p.posZ);
        double look = Math.toRadians(p.rotationYaw);
        final double lx = -Math.sin(look), lz = Math.cos(look);
        // The four straight directions (bridges run along the grid), most in front of you first; never from behind.
        Integer[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        java.util.Arrays.sort(dirs, new java.util.Comparator<Integer[]>() {
            @Override
            public int compare(Integer[] a, Integer[] b) {
                return Double.compare(b[0] * lx + b[1] * lz, a[0] * lx + a[1] * lz);
            }
        });
        int[] sides = {0, 1, -1, 2, -2};
        for (Integer[] d : dirs) {
            if (d[0] * lx + d[1] * lz < -0.3) continue;
            for (int side : sides) {
                for (int attempt = 0; attempt < 4; attempt++) {
                    int dist = 9 + rnd.nextInt(8); // starts 9-16 blocks away, stops 3 blocks short of you
                    int n = dist - 3 + 1;
                    int[] cx = new int[n], cz = new int[n];
                    boolean[] need = new boolean[n];
                    int blocks = 0;
                    boolean ok = true;
                    for (int i = 0; i < n && ok; i++) {
                        int along = dist - i;
                        cx[i] = px + d[0] * along - d[1] * side;
                        cz[i] = pz + d[1] * along + d[0] * side;
                        if (solid(cx[i], y, cz[i]) || solid(cx[i], y + 1, cz[i])) ok = false; // body must fit
                        else if (solid(cx[i], y - 1, cz[i])) need[i] = false;               // real ground
                        else if (mc.theWorld.isAirBlock(new BlockPos(cx[i], y - 1, cz[i]))) {
                            need[i] = true;
                            blocks++;
                        } else ok = false; // water, lava, plants: not a clean gap to bridge
                    }
                    if (!ok || need[0] || blocks < 3) continue; // must start on real ground and actually bridge
                    int[] segStart = new int[n];
                    for (int i = 1; i < n; i++) {
                        // ~2.2 blocks/s, a normal crouch-bridging pace (a block every 8-10 ticks); a bit quicker over real ground.
                        segStart[i] = segStart[i - 1] + (need[i] ? 8 + rnd.nextInt(3) : 6);
                    }
                    float yaw = (float) Math.toDegrees(Math.atan2(-d[0], d[1])); // facing away from you
                    GameProfile profile = randomProfile();
                    add(new BridgerFake(cx, cz, need, segStart, y, yaw, profile, teamWool(profile.getName())));
                    return true;
                }
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
        String[] how = {"was destroyed by", "was dismantled by", "was iced by", "got sent to the shadow realm by"};
        p.addChatMessage(new net.minecraft.util.ChatComponentText("\n§f§lBED DESTRUCTION > §7Your Bed "
                + how[rnd.nextInt(how.length)] + " §c" + by + "§7!\n"));
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
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = mc.thePlayer;
        WorldClient w = mc.theWorld;
        if (p == null || w == null) {
            active.clear();
            fakeBlocks.clear();
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
                if (f.entity != null) w.removeEntityFromWorld(f.id);
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
        if (f.entity != null) mc.theWorld.addEntityToWorld(f.id, f.entity);
        active.add(f);
    }

    private void sound(String name, float volume, float pitch, double x, double y, double z) {
        float v = volume * Math.max(0f, Math.min(1f, Handicaps.config.paranoiaVolume));
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(name), v, pitch,
                (float) x, (float) y, (float) z));
    }
}
