package com.voicehotbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hotbar command words, built from the config lines ("blocks=BLOCKS", "pt:dois=SLOT_2").
 * Pure logic (no Forge), so it can be tested outside the game.
 *
 * Everything is recognized by ONE English recognizer, so item words and numbers compete directly
 * (two separate recognizers made "quatro" fire as "water"). Portuguese numbers are listened for under
 * English spellings that sound like them; these were picked by testing a pt-BR voice:
 * 9/9 numbers correct, no wrong items.
 */
final class WordTable {

    private static final Map<String, String[]> PT_SPELLINGS = new HashMap<String, String[]>();
    /** Words that are listened for but do nothing, so similar-sounding speech lands on them instead. */
    private static final String[] DECOYS = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "set", "for", "says", "um", "boys", "toys", "nova"};

    /**
     * Words the recognizer splits in two ("pickaxe" -> "pick axe"). Listening for the two-word form as ONE
     * word stops the second half from firing on its own ("axe" switching away right after "pickaxe").
     */
    private static final Map<String, String[]> COMPOUNDS = new HashMap<String, String[]>();

    static {
        COMPOUNDS.put("pickaxe", new String[]{"pickaxe", "pick axe"});
        COMPOUNDS.put("fireball", new String[]{"fireball", "fire ball"});
        COMPOUNDS.put("enderpearl", new String[]{"enderpearl", "ender pearl"});
        PT_SPELLINGS.put("um", new String[]{"oom"});
        PT_SPELLINGS.put("dois", new String[]{"doi"});
        PT_SPELLINGS.put("três", new String[]{"tres"});
        PT_SPELLINGS.put("tres", new String[]{"tres"});
        PT_SPELLINGS.put("quatro", new String[]{"quatro", "quattro"});
        PT_SPELLINGS.put("cinco", new String[]{"cinco"});
        PT_SPELLINGS.put("seis", new String[]{"seis"});
        PT_SPELLINGS.put("sete", new String[]{"sete", "sete she", "set she"});
        PT_SPELLINGS.put("oito", new String[]{"oyo", "auto"});
        PT_SPELLINGS.put("nove", new String[]{"novi", "novo"});
    }

    /** recognizer token (phrases joined with '_') -> target / the word to show as feedback */
    private final Map<String, Target> commands = new LinkedHashMap<String, Target>();
    private final Map<String, String> displayWord = new HashMap<String, String>();
    /** multi-word spellings, longest first, so recognized text can be joined into tokens */
    private final List<String> phrases = new ArrayList<String>();

    WordTable(Iterable<String> lines) {
        for (String line : lines) {
            String s = line.trim();
            boolean pt = s.toLowerCase().startsWith("pt:");
            if (pt) s = s.substring(3);
            int eq = s.indexOf('=');
            if (eq <= 0) continue;
            String word = s.substring(0, eq).trim().toLowerCase();
            Target t = Target.parse(s.substring(eq + 1));
            if (word.isEmpty() || t == null || !word.matches("[\\p{L}' ]+")) continue;
            String[] spellings = pt && PT_SPELLINGS.containsKey(word) ? PT_SPELLINGS.get(word)
                    : COMPOUNDS.containsKey(word) ? COMPOUNDS.get(word) : new String[]{word};
            for (String sp : spellings) {
                String token = sp.replace(' ', '_');
                commands.put(token, t);
                displayWord.put(token, word);
                if (sp.contains(" ") && !phrases.contains(sp)) phrases.add(sp);
            }
        }
        Collections.sort(phrases, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
    }

    boolean isEmpty() {
        return commands.isEmpty();
    }

    Target lookup(String token) {
        return commands.get(token);
    }

    /** The word as written in the config ("oito"), for feedback, instead of the spelling heard ("oyo"). */
    String display(String token) {
        String d = displayWord.get(token);
        return d != null ? d : token;
    }

    /** "set she sword" -> "set_she sword", so phrase spellings become single tokens. */
    String joinPhrases(String text) {
        String t = " " + text + " ";
        for (String p : phrases) t = t.replace(" " + p + " ", " " + p.replace(' ', '_') + " ");
        return t.trim();
    }

    /** Vosk grammar with every command word (used before you're in a world). */
    String grammarJson() {
        return grammarJson(null);
    }

    /**
     * Vosk grammar: command spellings + decoys + "[unk]" (anything else).
     * With {@code available} set, item words are only included if that item is in the hotbar,
     * so "bow" can't register when you have no bow. Slot numbers are always included.
     */
    String grammarJson(java.util.Set<Target> available) {
        StringBuilder sb = new StringBuilder("[");
        for (Map.Entry<String, Target> e : commands.entrySet()) {
            Target t = e.getValue();
            if (available != null && t.fixedSlot() < 0 && !available.contains(t)) continue;
            sb.append('"').append(e.getKey().replace('_', ' ')).append("\", ");
        }
        for (String d : DECOYS) {
            if (!commands.containsKey(d)) sb.append('"').append(d).append("\", ");
        }
        return sb.append("\"[unk]\"]").toString();
    }
}
