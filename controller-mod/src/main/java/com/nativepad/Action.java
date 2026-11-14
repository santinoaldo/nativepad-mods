package com.nativepad;

import net.minecraft.client.resources.I18n;

/**
 * NativePad's own actions that aren't Minecraft key bindings.
 *
 * A controller button's target is stored as a string: either one of these names, or
 * {@code key:<description>} for any Minecraft / mod key binding (e.g. {@code key:key.jump}).
 */
public enum Action {
    NONE("Nothing"),
    SWAP_LAST("Swap to last hotbar slot"),
    HOTBAR_PREV("Hotbar: previous slot"),
    HOTBAR_NEXT("Hotbar: next slot"),
    PAUSE("Pause menu");

    public static final String KEY = "key:";

    public final String label;

    Action(String label) {
        this.label = label;
    }

    public String target() {
        return name();
    }

    public static String key(String description) {
        return KEY + description;
    }

    /** Special action for a target string, or null if it's a key binding target. */
    public static Action special(String target) {
        if (target == null || target.startsWith(KEY)) return null;
        try {
            return valueOf(target.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /** Human-readable name of a target, for the bindings screen. */
    public static String label(String target) {
        Action a = special(target);
        if (a != null) return a.label;
        return I18n.format(target.substring(KEY.length()));
    }

    /** Converts names used by NativePad 1.0.x configs into the current format. */
    public static String migrate(String v) {
        if (v == null) return NONE.name();
        String s = v.trim();
        if (s.startsWith(KEY)) return s;
        String u = s.toUpperCase();
        if (u.startsWith("SLOT_") && u.length() == 6) return key("key.hotbar." + u.charAt(5));
        if (u.equals("JUMP")) return key("key.jump");
        if (u.equals("SNEAK")) return key("key.sneak");
        if (u.equals("SPRINT")) return key("key.sprint");
        if (u.equals("ATTACK")) return key("key.attack");
        if (u.equals("USE")) return key("key.use");
        if (u.equals("PLAYER_LIST")) return key("key.playerlist");
        if (u.equals("INVENTORY")) return key("key.inventory");
        if (u.equals("DROP")) return key("key.drop");
        if (u.equals("CHAT")) return key("key.chat");
        if (u.equals("PERSPECTIVE")) return key("key.togglePerspective");
        if (u.equals("PICK_BLOCK")) return key("key.pickItem");
        return special(u).name();
    }
}
