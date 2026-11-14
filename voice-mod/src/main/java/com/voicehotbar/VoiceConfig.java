package com.voicehotbar;

import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** config/voicehotbar.cfg. Edit in-game via Mods > VoiceHotbar > Config or /voice. */
final class VoiceConfig {

    private static final String C = "voice";
    private static final String DEFAULT_MIC = "Default";
    static final String MODE_ALWAYS = "Always on", MODE_PTT = "Push to talk";
    static final String NAMES_STRICT = "Strict", NAMES_LOOSE = "Loose";
    static final String ENGINE_ACCURATE = "Accurate (Whisper)", ENGINE_FAST = "Fast";
    private static final String[] DEFAULT_COMMANDS = {
            "blocks=BLOCKS", "block=BLOCKS", "wool=BLOCKS",
            "sword=SWORD", "pickaxe=PICKAXE", "pick=PICKAXE", "axe=AXE", "shears=SHEARS",
            "bow=BOW", "pearl=PEARL", "rod=ROD", "apple=GOLDEN_APPLE", "gap=GOLDEN_APPLE",
            "fire=FIRE_CHARGE", "fireball=FIRE_CHARGE", "water=WATER", "potion=POTION", "egg=EGG"
            // Slot numbers are off for now. Portuguese words still work if added back, e.g. "pt:dois=SLOT_2".
    };
    /** Number words from earlier versions (English in 1.0.x, Portuguese in 1.1-1.3.2), removed by the updates below. */
    private static final List<String> OLD_NUMBERS = Arrays.asList(
            "one=SLOT_1", "two=SLOT_2", "three=SLOT_3", "four=SLOT_4", "five=SLOT_5",
            "six=SLOT_6", "seven=SLOT_7", "eight=SLOT_8", "nine=SLOT_9",
            "pt:um=SLOT_1", "pt:dois=SLOT_2", "pt:três=SLOT_3", "pt:quatro=SLOT_4", "pt:cinco=SLOT_5",
            "pt:seis=SLOT_6", "pt:sete=SLOT_7", "pt:oito=SLOT_8", "pt:nove=SLOT_9");

    private final Configuration cfg;

    boolean enabled, pushToTalk, instant, feedback;
    boolean dictation, capitalize, fixNames, punctuation, looseNames, autoPunctuation, whisperDictation;
    String commandWord, cancelWord;
    String microphone;
    /** Hotbar command words; rebuilt on every load. */
    volatile WordTable words = new WordTable(java.util.Collections.<String>emptyList());

    VoiceConfig(File file) {
        cfg = new Configuration(file);
        load();
    }

    void reload() {
        cfg.load();
        load();
    }

    /** Flip "Capitalize messages" (from the keybind) and save it. */
    void toggleCapitalize() {
        cfg.get(C, "capitalize", true).set(!capitalize);
        cfg.save();
        load();
    }

    void applyFromGui() {
        if (cfg.hasChanged()) cfg.save();
        load();
    }

    List<IConfigElement> guiElements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>(new ConfigElement(cfg.getCategory(C)).getChildElements());
        // Key buttons right under the setting they belong to.
        insertAfter(list, "listenMode", new net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement(
                "pttKey", "voicehotbar.pttKey", KeyCaptureScreen.PushToTalkEntry.class));
        insertAfter(list, "dictation", new net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement(
                "dictateKey", "voicehotbar.dictateKey", KeyCaptureScreen.DictateEntry.class));
        return list;
    }

    private static void insertAfter(List<IConfigElement> list, String name, IConfigElement element) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals(name)) {
                list.add(i + 1, element);
                return;
            }
        }
        list.add(element);
    }

    private void load() {
        List<String> order = new ArrayList<String>();
        enabled = bool("enabled", true, "Turn voice commands on or off.", order);
        // Replaces the old true/false "pushToTalk" setting, keeping its value.
        String oldMode = null;
        if (cfg.getCategory(C).containsKey("pushToTalk")) {
            oldMode = cfg.getCategory(C).get("pushToTalk").getBoolean(false) ? MODE_PTT : MODE_ALWAYS;
            cfg.getCategory(C).remove("pushToTalk");
        }
        Property mode = cfg.get(C, "listenMode", MODE_ALWAYS,
                "Always on: listens all the time.\nPush to talk: only listens while the Push to talk key is held.",
                new String[]{MODE_ALWAYS, MODE_PTT});
        if (oldMode != null) mode.set(oldMode);
        mode.setLanguageKey("voicehotbar.listenMode");
        pushToTalk = MODE_PTT.equals(mode.getString());
        order.add("listenMode");
        instant = bool("instant", true,
                "Act on a word as soon as it's recognized, without waiting for you to stop talking.\n"
                        + "Fastest, but it can occasionally act on a wrong first guess.", order);
        feedback = bool("feedback", true, "Show what was heard above the hotbar.", order);

        // Click to cycle through the microphones Java can see; "Default" = the Windows default input device.
        Property mic = cfg.get(C, "microphone", DEFAULT_MIC,
                "Which microphone to listen to. Default = the Windows default input device. /voice mics lists them.");
        if (mic.getString().trim().isEmpty()) mic.set(DEFAULT_MIC);
        List<String> mics = new ArrayList<String>();
        mics.add(DEFAULT_MIC);
        try {
            mics.addAll(VoiceListener.microphones());
        } catch (Throwable ignored) {
        }
        if (!mics.contains(mic.getString())) mics.add(mic.getString()); // keep a saved mic that's unplugged right now
        mic.setValidValues(mics.toArray(new String[0]));
        mic.setLanguageKey("voicehotbar.microphone");
        microphone = mic.getString().equals(DEFAULT_MIC) ? "" : mic.getString().trim();
        order.add("microphone");

        Property cmds = cfg.get(C, "commands", DEFAULT_COMMANDS,
                "word=TARGET (English) or pt:word=TARGET (Portuguese). Targets: " + Arrays.toString(Target.values())
                        + "\nItem targets pick the first hotbar slot holding that item; say it again to cycle to the next one.");
        cmds.setLanguageKey("voicehotbar.commands");
        order.add("commands");

        // One-time update: remove the default slot-number words (English or Portuguese). Other words are kept.
        Property cmdsVersion = cfg.get(C, "commandsVersion", 1, "Internal; don't change.");
        if (cmdsVersion.getInt() < 3) {
            List<String> updated = new ArrayList<String>();
            for (String line : cmds.getStringList()) {
                if (!OLD_NUMBERS.contains(line.trim().toLowerCase().replace("slot", "SLOT"))) updated.add(line);
            }
            cmds.set(updated.toArray(new String[0]));
            cmdsVersion.set(3);
        }
        cmdsVersion.setShowInGui(false);

        // ---- Dictate to chat ----
        dictation = bool("dictation", true,
                "Hold the 'Dictate to chat' key, talk, release: what you said is sent to chat. No need to open chat.", order);
        Property engine = cfg.get(C, "dictationEngine", ENGINE_ACCURATE,
                "Accurate (Whisper): much better words, real punctuation and capitals; sends about 0.6 s after you let go.\n"
                        + "Fast: the old engine, sends about 0.3 s after you let go. Hotbar words always use the fast engine.",
                new String[]{ENGINE_ACCURATE, ENGINE_FAST});
        engine.setLanguageKey("voicehotbar.dictationEngine");
        order.add("dictationEngine");
        whisperDictation = ENGINE_ACCURATE.equals(engine.getString());
        commandWord = word("commandWord", "command",
                "Start with this word (or \"slash\") to send a command: \"command shout hello\" sends /shout hello.", order);
        cancelWord = word("cancelWord", "cancel", "End with this word to throw the message away instead of sending it.", order);
        capitalize = bool("capitalize", true, "Capitalize the first letter of normal chat messages.", order);
        punctuation = bool("punctuation", true,
                "Spoken punctuation: say 'exclamation point', 'question mark', 'period', 'comma', 'colon' or 'dot dot dot'.", order);
        fixNames = bool("fixNames", true,
                "Autocorrect player names: \"hello super girly gamer\" -> \"hello SuperGirlyGamer\" if they're in tab.", order);
        Property nm = cfg.get(C, "nameMatching", NAMES_STRICT,
                "Strict: only clear matches become names. Loose: also catches names heard as other words\n"
                        + "('sentinel' -> Santinoaldo) but sometimes turns phrases like 'send in' into a name.",
                new String[]{NAMES_STRICT, NAMES_LOOSE});
        nm.setLanguageKey("voicehotbar.nameMatching");
        order.add("nameMatching");
        looseNames = NAMES_LOOSE.equals(nm.getString());
        autoPunctuation = bool("autoPunctuation", false,
                "Automatically end messages with '.' (or '?' if they start like a question). Commands are left alone.", order);

        words = new WordTable(Arrays.asList(cmds.getStringList()));

        cfg.setCategoryPropertyOrder(C, order);
        cfg.getCategory(C).setLanguageKey("voicehotbar.category");
        if (cfg.hasChanged()) cfg.save();
        VoiceHotbar.bumpConfigVersion();
    }

    private String word(String name, String def, String comment, List<String> order) {
        Property p = cfg.get(C, name, def, comment);
        p.setLanguageKey("voicehotbar." + name);
        order.add(name);
        return p.getString().trim().toLowerCase();
    }

    private boolean bool(String name, boolean def, String comment, List<String> order) {
        Property p = cfg.get(C, name, def, comment);
        p.setLanguageKey("voicehotbar." + name);
        order.add(name);
        return p.getBoolean(def);
    }

}
