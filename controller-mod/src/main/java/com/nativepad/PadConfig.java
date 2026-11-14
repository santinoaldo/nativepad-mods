package com.nativepad;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.DummyConfigElement;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * All tunables, stored in config/nativepad.cfg.
 * Edit in-game via Mods > NativePad > Config, or /nativepad. Hand edits: /nativepad reload.
 * Labels come from assets/nativepad/lang/en_US.lang.
 */
public final class PadConfig {

    static final String GENERAL = "general", AIM = "aim", MOVEMENT = "movement",
            TRIGGERS = "triggers", MENU = "menu", BUTTONS = "buttons", RUMBLE = "rumble", BATTERY = "battery";
    private static final String[] GUI_CATEGORIES = {GENERAL, AIM, MOVEMENT, TRIGGERS, MENU, RUMBLE, BATTERY};
    static final String HUD_OFF = "Off", HUD_TOP_LEFT = "Top left", HUD_TOP_RIGHT = "Top right",
            HUD_BOTTOM_LEFT = "Bottom left", HUD_BOTTOM_RIGHT = "Bottom right";

    // Rumble
    public boolean rumble, rumbleOnDamage, rumbleLowHealth, rumbleOnChatAlerts;
    public float rumbleStrength, lowHealthHearts;
    public String[] rumbleChatPhrases = new String[0];

    // Battery
    public String batteryHud = HUD_TOP_RIGHT;
    public boolean batteryWarning;

    /** Flip rumble on/off (from the keybind) and save. */
    public void toggleRumble() {
        cfg.get(RUMBLE, "enabled", true).set(!rumble);
        cfg.save();
        load();
    }

    private final Configuration cfg;
    private final Map<String, List<String>> order = new java.util.HashMap<String, List<String>>();

    /** Bumped whenever bindings change so the handler re-resolves them. */
    public volatile int version;

    public boolean enabled;

    // Aim (right stick)
    public float yawSpeed, pitchSpeed, aimDeadzone, aimOuterDeadzone, aimCurve;
    public boolean invertY;

    // Movement (left stick)
    public float moveDeadzone;
    public boolean analogMovement, autoSprint;

    // Triggers
    public float triggerThreshold;

    // Menus
    public float cursorSpeed, cursorCurve;
    public boolean snapToSlots, stickSnapInInventories;

    /** Controller button -> target string (see {@link Action}). */
    public final Map<XInput.Button, String> buttons = new EnumMap<XInput.Button, String>(XInput.Button.class);

    public PadConfig(File file) {
        cfg = new Configuration(file);
        load();
    }

    /** Re-read the file from disk (after hand edits). */
    public void reload() {
        cfg.load();
        load();
    }

    /** Apply values changed in the settings screen and write them to disk. */
    public void applyFromGui() {
        if (cfg.hasChanged()) cfg.save();
        load();
    }

    /** Assign a controller button and save immediately. */
    public void setBinding(XInput.Button button, String target) {
        cfg.get(BUTTONS, button.name(), Action.NONE.name()).set(target);
        cfg.save();
        buttons.put(button, target);
        version++;
    }

    /** Entries for the settings screen: the bindings screen first, then each category. */
    public List<IConfigElement> guiElements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        list.add(new DummyConfigElement.DummyCategoryElement("bindings", "nativepad.bindings",
                GuiFactory.BindingsEntry.class));
        for (String c : GUI_CATEGORIES) list.add(new ConfigElement(cfg.getCategory(c)));
        return list;
    }

    private void load() {
        order.clear();
        enabled = bool(GENERAL, "enabled", true, "Turn controller input on or off.");

        yawSpeed = slider(AIM, "yawSpeed", 450f, 30f, 2000f, "Max horizontal turn speed in degrees/second at full stick.");
        pitchSpeed = slider(AIM, "pitchSpeed", 300f, 30f, 2000f, "Max vertical turn speed in degrees/second at full stick.");
        aimCurve = slider(AIM, "curve", 1.8f, 1f, 4f, "1 = linear. Higher = finer control near the center, fast flicks at the edge.");
        aimDeadzone = slider(AIM, "deadzone", 0.08f, 0f, 0.5f, "Raise this if the camera drifts on its own.");
        aimOuterDeadzone = slider(AIM, "outerDeadzone", 0.95f, 0.5f, 1f, "How far the stick must go to count as full speed.");
        invertY = bool(AIM, "invertY", false, "Push up to look down.");

        moveDeadzone = slider(MOVEMENT, "deadzone", 0.15f, 0f, 0.6f, "How far you push before you start walking.");
        // Old key defaulted to on; replaced so it starts off for everyone.
        if (cfg.getCategory(MOVEMENT).containsKey("autoSprint")) cfg.getCategory(MOVEMENT).remove("autoSprint");
        autoSprint = bool(MOVEMENT, "autoSprintOn", false, "Sprint whenever the stick is pushed (same as toggle sprint).");
        // Old key defaulted to true and caused anticheat setbacks; drop it so everyone gets the safe default.
        if (cfg.getCategory(MOVEMENT).containsKey("analog")) cfg.getCategory(MOVEMENT).remove("analog");
        analogMovement = bool(MOVEMENT, "analogUnsafe", false,
                "Off (recommended): stick snaps to 8 directions exactly like WASD.\n"
                        + "On: partial tilt walks slower. Hypixel and other anticheats will lag you back for this.");

        triggerThreshold = slider(TRIGGERS, "triggerThreshold", 0.10f, 0.01f, 0.9f,
                "How far LT/RT must be pulled to count as pressed. Lower = faster clicks.");

        cursorSpeed = slider(MENU, "cursorSpeed", 1.3f, 0.2f, 5f, "Screen-heights per second at full tilt.");
        cursorCurve = slider(MENU, "cursorCurve", 2.0f, 1f, 4f, "Higher = slower, more precise near the center.");
        snapToSlots = bool(MENU, "snapToSlots", true,
                "D-pad jumps the cursor to the next slot (inventories, chests, shops) or button (other menus).");
        stickSnapInInventories = bool(MENU, "stickSnap", true,
                "In inventories, chests and shops the left stick also hops slot to slot (hold to repeat). Off = free cursor.");

        rumble = bool(RUMBLE, "enabled", true, "Controller vibration. Also has a 'Toggle rumble' keybind.");
        rumbleStrength = slider(RUMBLE, "strength", 0.8f, 0.1f, 1f, "How strong every rumble is.");
        rumbleOnDamage = bool(RUMBLE, "onDamage", true, "Thump when you take damage (harder hits rumble harder).");
        rumbleLowHealth = bool(RUMBLE, "lowHealth", true, "Heartbeat while your health is low.");
        lowHealthHearts = slider(RUMBLE, "lowHealthHearts", 3f, 1f, 10f, "Heartbeat starts at this many hearts or fewer.");
        rumbleOnChatAlerts = bool(RUMBLE, "chatAlerts", true,
                "Long rumble when a chat message contains one of the phrases below (bed destroyed, trap, ...).");
        Property phrases = cfg.get(RUMBLE, "chatPhrases", new String[]{"BED DESTRUCTION > Your Bed", "trap has been set off",
                "Your trap was triggered"}, "Chat text that triggers a long rumble (not case-sensitive). Edit to match your server.");
        phrases.setLanguageKey("nativepad." + RUMBLE + ".chatPhrases");
        track(RUMBLE, "chatPhrases");
        rumbleChatPhrases = phrases.getStringList();

        Property hud = cfg.get(BATTERY, "position", HUD_TOP_RIGHT, "Where to show the controller battery.",
                new String[]{HUD_OFF, HUD_TOP_LEFT, HUD_TOP_RIGHT, HUD_BOTTOM_LEFT, HUD_BOTTOM_RIGHT});
        hud.setLanguageKey("nativepad." + BATTERY + ".position");
        track(BATTERY, "position");
        batteryHud = hud.getString();
        batteryWarning = bool(BATTERY, "warning", true, "Chat warning when the controller battery gets low.");

        category(GENERAL, "");
        category(AIM, "How the camera turns.");
        category(MOVEMENT, "How you walk.");
        category(TRIGGERS, "LT / RT sensitivity.");
        category(MENU, "Left stick cursor in menus.\nA = click, X = right click, RB = shift-click, B/Start = back.\nD-pad = jump between slots/buttons.");
        category(RUMBLE, "Vibration on game events.");
        category(BATTERY, "Controller battery indicator. Wireless only; some controllers don't report it.");

        cfg.setCategoryComment(BUTTONS, "Controller button -> action. Use the in-game Controller Bindings screen,\n"
                + "or: SWAP_LAST, HOTBAR_PREV, HOTBAR_NEXT, PAUSE, NONE, or key:<key binding name> (e.g. key:key.jump).");
        bind(XInput.Button.A, Action.key("key.jump"));
        bind(XInput.Button.B, Action.key("key.sneak"));
        bind(XInput.Button.X, Action.SWAP_LAST.target());
        bind(XInput.Button.Y, Action.key("key.inventory"));
        bind(XInput.Button.LB, Action.HOTBAR_PREV.target());
        bind(XInput.Button.RB, Action.HOTBAR_NEXT.target());
        bind(XInput.Button.LT, Action.key("key.use"));
        bind(XInput.Button.RT, Action.key("key.attack"));
        bind(XInput.Button.L3, Action.key("key.sprint"));
        bind(XInput.Button.R3, Action.key("key.togglePerspective"));
        bind(XInput.Button.BACK, Action.key("key.playerlist"));
        bind(XInput.Button.START, Action.PAUSE.target());
        bind(XInput.Button.DPAD_LEFT, Action.key("key.hotbar.1"));
        bind(XInput.Button.DPAD_UP, Action.key("key.hotbar.2"));
        bind(XInput.Button.DPAD_RIGHT, Action.key("key.hotbar.3"));
        bind(XInput.Button.DPAD_DOWN, Action.key("key.hotbar.4"));

        if (cfg.hasChanged()) cfg.save();
        version++;
    }

    // ---- helpers ----

    private void track(String cat, String name) {
        List<String> l = order.get(cat);
        if (l == null) order.put(cat, l = new ArrayList<String>());
        l.add(name);
    }

    private void category(String name, String comment) {
        ConfigCategory c = cfg.getCategory(name);
        c.setLanguageKey("nativepad.cat." + name);
        c.setComment(comment);
        List<String> l = order.get(name);
        if (l != null) cfg.setCategoryPropertyOrder(name, l);
    }

    private float slider(String cat, String name, float def, float min, float max, String comment) {
        // 1.0.x saved these as text ("S:"), which Forge's slider treats as whole numbers (0.08 -> 0).
        // Convert to a real decimal property, keeping the user's value.
        ConfigCategory c = cfg.getCategory(cat);
        double keep = def;
        if (c.containsKey(name) && c.get(name).getType() != Property.Type.DOUBLE) {
            try {
                keep = Double.parseDouble(c.get(name).getString());
            } catch (NumberFormatException ignored) {
            }
            c.remove(name);
        }
        Property p = cfg.get(cat, name, def, comment + " [" + min + " - " + max + ", default " + def + "]", min, max);
        if (keep != def) p.set(keep);
        p.setLanguageKey("nativepad." + cat + "." + name);
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
        p.setLanguageKey("nativepad." + cat + "." + name);
        track(cat, name);
        return p.getBoolean(def);
    }

    private void bind(XInput.Button button, String def) {
        Property p = cfg.get(BUTTONS, button.name(), def, "Default: " + def);
        String v = Action.migrate(p.getString());
        if (!v.equals(p.getString())) p.set(v);
        buttons.put(button, v);
    }
}
