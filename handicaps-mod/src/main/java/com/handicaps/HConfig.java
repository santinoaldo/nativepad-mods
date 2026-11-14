package com.handicaps;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** config/handicaps.cfg. Edit in-game with /handicaps or Mods > Handicaps > Config. */
final class HConfig {

    static final String GENERAL = "general", SIGHT = "shortsight", BLUR = "nearsighted", GRAY = "grayscale",
            TUNNEL = "tunnel", DEAF = "deaf", DRUNK = "drunk", PARANOIA = "paranoia", HALLU = "hallucinations";
    private static final String[] CATEGORIES = {GENERAL, SIGHT, BLUR, GRAY, TUNNEL, DEAF, DRUNK, PARANOIA, HALLU};
    static final String[] FOG_COLORS = {"Black", "Gray", "White", "Dark red"};

    private final Configuration cfg;
    private final Map<String, List<String>> order = new HashMap<String, List<String>>();

    /** Master switch, flipped by the "Toggle all handicaps" key. Not saved: every launch starts with it on. */
    volatile boolean paused;

    boolean enabled;
    boolean shortSight;
    float sightDistance, fogSoftness;
    String fogColor;
    boolean nearSighted;
    float clearDistance, blurRamp, blurStrength;
    boolean grayscale;
    float grayscaleAmount;
    boolean tunnel;
    float tunnelSize, tunnelSoftness;
    boolean deaf;
    boolean drunk;
    float drunkStrength;
    boolean paranoia;
    private final java.util.EnumMap<ParanoiaSound, Boolean> paranoiaSounds = new java.util.EnumMap<ParanoiaSound, Boolean>(ParanoiaSound.class);
    float paranoiaEvery, paranoiaVolume;
    boolean hallucinations, fakeFireballs, fakePlayers, fakeInvisible, fakeArrow, fakeBed, fakeBridger, fakeFill;
    float hallucinationEvery;
    boolean fakeBlocksOff;
    String paranoiaSet;

    HConfig(File file) {
        cfg = new Configuration(file);
        load();
    }

    /** Each settings section's main switch (the "enabled" setting in that section). */
    boolean isOn(String category) {
        return cfg.getCategory(category).containsKey("enabled") && cfg.getCategory(category).get("enabled").getBoolean(false);
    }

    /** Flip a section's main switch from the main list; saved and applied immediately. */
    void setOn(String category, boolean on) {
        cfg.get(category, "enabled", false).set(on);
        cfg.save();
        load();
    }

    boolean paranoiaOn(ParanoiaSound s) {
        Boolean b = paranoiaSounds.get(s);
        return b == null || b;
    }

    boolean active() {
        return enabled && !paused;
    }

    void reload() {
        cfg.load();
        load();
    }

    void applyFromGui() {
        if (cfg.hasChanged()) cfg.save();
        load();
    }

    List<IConfigElement> guiElements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        for (String c : CATEGORIES) {
            cfg.getCategory(c).setConfigEntryClass(GuiFactory.ToggleCategoryEntry.class); // row with an ON/OFF button
            list.add(new ConfigElement(cfg.getCategory(c)));
        }
        return list;
    }

    float[] fogRgb() {
        if ("White".equals(fogColor)) return new float[]{0.85f, 0.87f, 0.9f};
        if ("Gray".equals(fogColor)) return new float[]{0.45f, 0.46f, 0.48f};
        if ("Dark red".equals(fogColor)) return new float[]{0.18f, 0.0f, 0.0f};
        return new float[]{0f, 0f, 0f};
    }

    private void load() {
        order.clear();
        enabled = bool(GENERAL, "enabled", true, "Master switch for all handicaps. The 'Toggle all handicaps' key pauses them instantly.");

        shortSight = bool(SIGHT, "enabled", false, "You can only see this far; everything beyond fades into fog.");
        // One-time update: 1.0.0 shipped with short sight on by default. Everything now starts off.
        Property version = cfg.get(GENERAL, "configVersion", 1, "Internal; don't change.");
        version.setShowInGui(false);
        if (version.getInt() < 2) {
            cfg.get(SIGHT, "enabled", false).set(false);
            shortSight = false;
            version.set(2);
        }
        sightDistance = slider(SIGHT, "distance", 4f, 1f, 48f, "How far you can see, in blocks.");
        fogSoftness = slider(SIGHT, "softness", 1.5f, 0f, 16f, "How many blocks the fade takes (0 = hard wall).");
        Property fc = cfg.get(SIGHT, "color", "Black", "Fog color.", FOG_COLORS);
        fc.setLanguageKey("handicaps." + SIGHT + ".color");
        track(SIGHT, "color");
        fogColor = fc.getString();

        nearSighted = bool(BLUR, "enabled", false, "Close things are sharp, far things blurry.");
        clearDistance = slider(BLUR, "clearDistance", 3f, 0f, 32f, "Everything closer than this stays sharp (blocks).");
        blurRamp = slider(BLUR, "ramp", 6f, 0.5f, 32f, "Over how many blocks the blur builds up to full strength.");
        blurStrength = slider(BLUR, "strength", 10f, 1f, 30f, "How blurry it gets (blur radius in pixels).");

        grayscale = bool(GRAY, "enabled", false, "No color: the whole screen, HUD and menus are black and white.");
        grayscaleAmount = slider(GRAY, "amount", 1f, 0f, 1f, "1 = fully gray, lower = washed-out colors.");

        tunnel = bool(TUNNEL, "enabled", false, "A dark ring closes in from the edges of the screen.");
        tunnelSize = slider(TUNNEL, "size", 0.6f, 0.1f, 1.5f, "How much of the view stays visible (bigger = more).");
        tunnelSoftness = slider(TUNNEL, "softness", 0.25f, 0f, 1f, "How soft the edge of the ring is.");

        deaf = bool(DEAF, "enabled", false, "No game sounds or music (menu clicks still play).");

        drunk = bool(DRUNK, "enabled", false, "Swaying camera, wobbly double vision and drifting aim.");
        drunkStrength = slider(DRUNK, "strength", 0.6f, 0.1f, 2f, "How drunk.");

        paranoia = bool(PARANOIA, "enabled", false, "Fake sounds from behind you: footsteps sneaking up, bridging, wool breaking, fall damage, combat.");
        paranoiaEvery = slider(PARANOIA, "every", 25f, 5f, 120f, "About how many seconds between fake sounds (randomized).");
        paranoiaVolume = slider(PARANOIA, "volume", 1f, 0f, 1f, "Volume of the fake sounds and the Test buttons (1 = normal game volume).");
        Property set = cfg.get(PARANOIA, "soundSet", "Bedwars", "Which game's sounds to fake.", new String[]{"Bedwars"});
        set.setLanguageKey("handicaps." + PARANOIA + ".soundSet");
        track(PARANOIA, "soundSet");
        paranoiaSet = set.getString();
        // 1.1.0's grouped toggles are replaced by one setting per sound.
        for (String old : new String[]{"footsteps", "wool", "fallDamage", "combat", "items"}) {
            if (cfg.getCategory(PARANOIA).containsKey(old)) cfg.getCategory(PARANOIA).remove(old);
        }
        // One on/off per sound, each shown with a Test button next to it.
        for (ParanoiaSound s : ParanoiaSound.values()) {
            Property p = cfg.get(PARANOIA, s.key, true, s.label + ".");
            p.setLanguageKey("handicaps." + PARANOIA + "." + s.key);
            p.setConfigEntryClass(GuiFactory.SoundToggleEntry.class);
            track(PARANOIA, s.key);
            paranoiaSounds.put(s, p.getBoolean(true));
        }

        hallucinations = bool(HALLU, "enabled", false,
                "Fake fireballs and players that only you can see. They can't hurt you and can't be hit.");
        fakeBlocksOff = bool(HALLU, "fakeBlocksOff", false, "Fake blocks (bridger, bridge holes) are drawn with their texture turned sideways, so a careful look tells them apart from real ones.");
        hallucinationEvery = slider(HALLU, "every", 45f, 10f, 180f, "About how many seconds between hallucinations (randomized).");
        fakeFireballs = hallu("fakeFireball", "A fireball flies at you and 'explodes' next to you (no damage).");
        fakePlayers = hallu("fakePlayer", "A player from the tab list sprints at you with a sword, then vanishes.");
        fakeInvisible = hallu("fakeInvisible", "An invisible player: footprints and footsteps walk up to you, but nobody is there.");
        fakeArrow = hallu("fakeArrow", "An arrow is shot from far away and whizzes past your head.");
        fakeBed = hallu("fakeBed", "The BED DESTROYED! alert (title, sound, chat line), but your bed is fine.");
        fakeBridger = hallu("fakeBridger", "An enemy bridges toward you from somewhere you can't see (drops down or nerd-poles up if needed), then chases you and swings. Their blocks are only drawn on your screen, never added to the world.");
        fakeFill = hallu("fakeFill", "Some holes in nearby bridges (1-2 blocks, in a straight line) look filled in with the bridge's block, but nothing is there: walk on it and you fall. Only drawn on your screen.");

        category(GENERAL, "General", "");
        category(SIGHT, "Short sight", "You can only see a few blocks.");
        category(BLUR, "Near-sighted", "Far things get blurry.");
        category(GRAY, "Grayscale", "Full color blindness.");
        category(TUNNEL, "Tunnel vision", "Dark edges.");
        category(DEAF, "Deaf", "No sound.");
        category(DRUNK, "Drunk", "Sway, wobble, drift.");
        category(PARANOIA, "Paranoia", "Fake sounds behind you.");
        category(HALLU, "Hallucinations", "Fake things only you can see.");

        if (cfg.hasChanged()) cfg.save();
    }

    // ---- helpers ----

    private void track(String cat, String name) {
        List<String> l = order.get(cat);
        if (l == null) order.put(cat, l = new ArrayList<String>());
        l.add(name);
    }

    private void category(String name, String label, String comment) {
        ConfigCategory c = cfg.getCategory(name);
        c.setLanguageKey("handicaps.cat." + name);
        c.setComment(comment);
        List<String> l = order.get(name);
        if (l != null) cfg.setCategoryPropertyOrder(name, l);
    }

    private float slider(String cat, String name, float def, float min, float max, String comment) {
        Property p = cfg.get(cat, name, (double) def, comment + " [" + min + " - " + max + ", default " + def + "]", min, max);
        p.setLanguageKey("handicaps." + cat + "." + name);
        p.setConfigEntryClass(GuiFactory.DecimalSliderEntry.class);
        track(cat, name);
        double v = p.getDouble(def);
        if (v < min || v > max) {
            v = def;
            p.set(def);
        }
        return (float) v;
    }

    /** A hallucination on/off row with a Test button. */
    private boolean hallu(String key, String comment) {
        Property p = cfg.get(HALLU, key, true, comment);
        p.setLanguageKey("handicaps." + HALLU + "." + key);
        p.setConfigEntryClass(GuiFactory.SoundToggleEntry.class);
        track(HALLU, key);
        return p.getBoolean(true);
    }

    private boolean bool(String cat, String name, boolean def, String comment) {
        Property p = cfg.get(cat, name, def, comment);
        p.setLanguageKey("handicaps." + cat + "." + name);
        track(cat, name);
        return p.getBoolean(def);
    }
}
