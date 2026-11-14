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
            TUNNEL = "tunnel", DEAF = "deaf", DRUNK = "drunk";
    private static final String[] CATEGORIES = {GENERAL, SIGHT, BLUR, GRAY, TUNNEL, DEAF, DRUNK};
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

    HConfig(File file) {
        cfg = new Configuration(file);
        load();
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
        for (String c : CATEGORIES) list.add(new ConfigElement(cfg.getCategory(c)));
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

        category(GENERAL, "General", "");
        category(SIGHT, "Short sight (fog)", "You can only see a few blocks.");
        category(BLUR, "Near-sighted (blur)", "Far things get blurry.");
        category(GRAY, "Grayscale", "Full color blindness.");
        category(TUNNEL, "Tunnel vision", "Dark edges.");
        category(DEAF, "Deaf", "No sound.");
        category(DRUNK, "Drunk", "Sway, wobble, drift.");

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

    private boolean bool(String cat, String name, boolean def, String comment) {
        Property p = cfg.get(cat, name, def, comment);
        p.setLanguageKey("handicaps." + cat + "." + name);
        track(cat, name);
        return p.getBoolean(def);
    }
}
