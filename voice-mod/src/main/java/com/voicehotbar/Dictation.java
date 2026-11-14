package com.voicehotbar;

/** Turns recognized speech into the chat line to send. Pure logic, no Minecraft calls. */
final class Dictation {

    /** Minecraft 1.8.9's chat length limit. */
    static final int MAX_LENGTH = 100;

    private Dictation() {
    }

    private static final String[] DIGIT_WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
    private static final int MAX_WINDOW = 5;

    /**
     * Replaces spoken runs of words with player names they sound like:
     * "hello super girly gamer" + [SuperGirlyGamer] -> "hello SuperGirlyGamer".
     * Words are glued together and compared letter-by-letter (digit words count as digits),
     * so the speech recognizer splitting a name into pieces doesn't matter.
     */
    static String fixNames(String text, java.util.Collection<String> names) {
        return fixNames(text, names, false);
    }

    /**
     * @param loose Loose matching: one sound may be off for 6-sound names, and l/r count as the same sound.
     *              Catches more attempts at a name ("sentinel", "sent in order" -> Santinoaldo) but sometimes
     *              turns ordinary phrases into a name ("send in more blocks"). Tested: strict 5/9 names, 0/60
     *              normal lines changed; loose 9/9 names, 8/60 normal lines changed.
     */
    static String fixNames(String text, java.util.Collection<String> names, boolean loose) {
        if (text == null || text.isEmpty() || names.isEmpty()) return text;
        String[] words = text.split(" ");
        java.util.List<String> nameList = new java.util.ArrayList<String>();
        java.util.List<String> normList = new java.util.ArrayList<String>();
        java.util.List<String> soundList = new java.util.ArrayList<String>();
        for (String n : names) {
            String norm = normalize(n);
            if (norm.length() >= 3) {
                nameList.add(n);
                normList.add(norm);
                soundList.add(sound(norm, loose));
            }
        }
        java.util.List<String> out = new java.util.ArrayList<String>();
        boolean lastWasName = false;
        int i = 0;
        while (i < words.length) {
            int bestLen = 0, bestBorrow = 0;
            String bestName = null;
            double bestScore = 0;
            String prev = out.isEmpty() || lastWasName ? null : out.get(out.size() - 1);
            for (int len = Math.min(MAX_WINDOW, words.length - i); len >= 1; len--) {
                String c = spoken(words, i, i + len);
                if (c.isEmpty()) continue;
                // The recognizer sometimes glues a name's first letter onto the previous word
                // ("invites anti know aldo"), so also try borrowing up to 2 letters back from it.
                for (int borrow = 0; borrow <= 2; borrow++) {
                    if (borrow > 0 && (prev == null || prev.length() < borrow + 2)) break;
                    String cand = borrow == 0 ? c : prev.substring(prev.length() - borrow).toLowerCase() + c;
                    String candSound = sound(cand, loose);
                    for (int n = 0; n < normList.size(); n++) {
                        String norm = normList.get(n);
                        double score = 0;
                        // 1) Spelling: close letter-by-letter.
                        if (Math.abs(norm.length() - cand.length()) <= Math.max(2, norm.length() / 4)) {
                            double s = 1.0 - (double) levenshtein(cand, norm) / Math.max(cand.length(), norm.length());
                            // Short names must match almost exactly so everyday words don't become names.
                            double needed = norm.length() <= 4 ? 1.0 : norm.length() <= 6 ? 0.85 : 0.75;
                            if (s >= needed) score = s;
                        }
                        // 2) Sound: the recognizer often spells a name as different words that sound the
                        //    same ("cindy know all though" ~ "santinoaldo": both s-n-t-n-l-t). Only for
                        //    names with enough consonants that a coincidence is unlikely.
                        String nameSound = soundList.get(n);
                        if (score == 0 && borrow == 0 && nameSound.length() >= 5 && cand.length() <= norm.length() * 2) {
                            int d = levenshtein(candSound, nameSound);
                            int allowed = nameSound.length() >= (loose ? 6 : 7) ? 1 : 0;
                            // Don't let a match start with a word it doesn't need ("is cindy know..." / "hello super...").
                            boolean firstWordNeeded = len == 1
                                    || levenshtein(sound(spoken(words, i + 1, i + len), loose), nameSound) > d;
                            if (d <= allowed && firstWordNeeded) {
                                score = 0.7 - 0.05 * d - 0.001 * Math.abs(cand.length() - norm.length());
                            }
                        }
                        // 3) Short names (4 sounds, e.g. PaiXD = p-k-s-t): an exact sound match is enough when the
                        //    spelling is also close and about the same length ("PAYXD" -> PaiXD).
                        if (score == 0 && borrow == 0 && nameSound.length() == 4 && candSound.equals(nameSound)
                                && Math.abs(cand.length() - norm.length()) <= 1
                                && 1.0 - (double) levenshtein(cand, norm) / Math.max(cand.length(), norm.length()) >= 0.6) {
                            score = 0.69;
                        }
                        if (score > 0 && score > bestScore + 1e-9) {
                            bestScore = score;
                            bestName = nameList.get(n);
                            bestLen = len;
                            bestBorrow = borrow;
                        }
                    }
                }
            }
            if (bestName != null) {
                if (bestBorrow > 0) {
                    String p = out.remove(out.size() - 1);
                    out.add(p.substring(0, p.length() - bestBorrow));
                }
                // Keep punctuation that followed the spoken name ("Santino Aldo," -> "Santinoaldo,").
                String lastWord = words[i + bestLen - 1];
                String trailing = lastWord.replaceAll("^.*?([.,!?;:]*)$", "$1");
                out.add(bestName + trailing);
                lastWasName = true;
                i += bestLen;
            } else {
                out.add(words[i]);
                lastWasName = false;
                i++;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String w : out) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }

    /**
     * Picks between the final text and the live-preview text, both name-corrected. The recognizer's
     * final pass sometimes mangles a name the preview had right ("santi no aldo" -> "st in a the the
     * te tino aldo"), so if the preview version contains more player names, that's what gets sent.
     */
    static String pickVersion(String finalText, String previewText, java.util.Collection<String> names) {
        return pickVersion(finalText, previewText, names, false);
    }

    static String pickVersion(String finalText, String previewText, java.util.Collection<String> names, boolean loose) {
        String a = fixNames(finalText, names, loose);
        String b = fixNames(previewText, names, loose);
        return countNames(b, names) > countNames(a, names) ? b : a;
    }

    private static int countNames(String text, java.util.Collection<String> names) {
        if (text == null || text.isEmpty()) return 0;
        int n = 0;
        for (String w : text.split(" ")) if (names.contains(w)) n++;
        return n;
    }

    /**
     * Rough "how it sounds" key: consonant skeleton with similar sounds merged.
     * "santinoaldo" -> "sntnlt", "cindy know all though" -> "sntnlt", "sent you know aldo" -> "sntnlt".
     */
    static String sound(String text) {
        return sound(text, false);
    }

    static String sound(String text, boolean mergeLR) {
        String s = text.toLowerCase().replaceAll("[^a-z0-9]", "");
        s = s.replace("ph", "f").replace("th", "t").replace("gh", "").replace("ck", "k").replace("kn", "n")
                .replace("wr", "r").replace("sh", "s").replace("ch", "k");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
            char m;
            switch (c) {
                case 'a': case 'e': case 'i': case 'o': case 'u': case 'y': case 'w': case 'h':
                    continue; // vowels and glides carry little; the recognizer swaps them freely
                case 'c': m = (next == 'e' || next == 'i' || next == 'y') ? 's' : 'k'; break;
                case 'b': m = 'p'; break;
                case 'd': m = 't'; break;
                case 'g': case 'q': m = 'k'; break;
                case 'v': m = 'f'; break;
                case 'z': m = 's'; break;
                case 'm': m = 'n'; break;
                case 'r': m = mergeLR ? 'l' : 'r'; break;
                case 'x':
                    if (out.length() == 0 || out.charAt(out.length() - 1) != 'k') out.append('k');
                    m = 's';
                    break;
                default: m = c;
            }
            if (out.length() == 0 || out.charAt(out.length() - 1) != m) out.append(m);
        }
        return out.toString();
    }

    private static final java.util.Set<String> QUESTION_STARTS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "what", "where", "when", "why", "how", "who", "whose", "which", "is", "are", "was", "were", "can",
            "could", "do", "does", "did", "will", "would", "should", "shall", "have", "has", "may", "anyone", "anybody"));

    /**
     * Ends a sentence that has no punctuation yet: "?" if it starts like a question
     * ("where is red base" -> "where is red base?"), otherwise ".".
     */
    static String autoPunctuate(String text) {
        if (text == null || text.isEmpty()) return text;
        char last = text.charAt(text.length() - 1);
        if (last == '.' || last == '!' || last == '?' || last == ',' || last == ':') return text;
        String first = text.split(" ")[0].toLowerCase();
        return text + (QUESTION_STARTS.contains(first) ? "?" : ".");
    }

    /** Spoken punctuation, longest phrases first so "exclamation point" wins over shorter matches. */
    private static final String[][] PUNCTUATION = {
            {"dot dot dot", "..."}, {"exclamation point", "!"}, {"exclamation mark", "!"},
            {"question mark", "?"}, {"full stop", "."}, {"period", "."}, {"comma", ","}, {"colon", ":"},
            {"ellipsis", "..."},
    };

    /**
     * "gg exclamation point exclamation point" -> "gg!!", "where are you question mark" -> "where are you?".
     * Punctuation attaches to the word before it.
     */
    static String applyPunctuation(String text) {
        String[] words = text.split(" ");
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String symbol = null;
            int used = 0;
            for (String[] p : PUNCTUATION) {
                String[] pw = p[0].split(" ");
                if (i + pw.length > words.length) continue;
                boolean match = true;
                for (int k = 0; k < pw.length; k++) {
                    if (!words[i + k].equalsIgnoreCase(pw[k])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    symbol = p[1];
                    used = pw.length;
                    break;
                }
            }
            if (symbol != null) {
                out.append(symbol); // no space: sticks to the previous word / previous punctuation
                i += used;
            } else {
                if (out.length() > 0) out.append(' ');
                out.append(words[i]);
                i++;
            }
        }
        return out.toString().trim();
    }

    private static final String[] TEENS = {"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    /** Glues words[from..to) into letters/digits: "dark knight ninety nine" -> "darkknight99". */
    private static String spoken(String[] words, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int k = from; k < to; k++) {
            String w = words[k].toLowerCase();
            int tens = indexOf(TENS, w), teen = indexOf(TEENS, w), digit = indexOf(DIGIT_WORDS, w);
            if (tens >= 2) {
                int unit = k + 1 < to ? indexOf(DIGIT_WORDS, words[k + 1].toLowerCase()) : -1;
                if (unit >= 1) {
                    sb.append(tens).append(unit);
                    k++;
                } else {
                    sb.append(tens).append('0');
                }
            } else if (teen >= 0) {
                sb.append(10 + teen);
            } else if (digit >= 0) {
                sb.append(digit);
            } else {
                sb.append(w.replaceAll("[^a-z0-9]", ""));
            }
        }
        return sb.toString();
    }

    private static int indexOf(String[] arr, String w) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(w)) return i;
        return -1;
    }

    private static String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /**
     * "command shout hello world" -> "/shout hello world"
     * "good game everyone"        -> "Good game everyone"
     * "... cancel"                -> null (don't send)
     *
     * @param commandWord spoken word that turns the rest into a /command (e.g. "command")
     * @param cancelWord  spoken word that, said last, discards the message (e.g. "cancel")
     * @return the line to send, or null for nothing
     */
    static String format(String spoken, String commandWord, String cancelWord, boolean capitalize) {
        return format(spoken, commandWord, cancelWord, capitalize, true);
    }

    static String format(String spoken, String commandWord, String cancelWord, boolean capitalize, boolean punctuation) {
        return format(spoken, commandWord, cancelWord, capitalize, punctuation, false);
    }

    static String format(String spoken, String commandWord, String cancelWord, boolean capitalize, boolean punctuation,
                         boolean autoPunctuation) {
        if (spoken == null) return null;
        String text = spoken.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;
        String lower = text.toLowerCase();
        // Whisper writes real punctuation ("..., cancel." / "Command, shout..."), so ignore it for these checks.
        String bare = lower.replaceAll("[.!?,;:]", "").trim();

        if (!cancelWord.isEmpty() && (bare.equals(cancelWord) || bare.endsWith(" " + cancelWord))) return null;

        String out;
        // "command ..." or "slash ..." at the start turns the rest into a /command.
        String starter = null;
        for (String w : new String[]{commandWord, "slash"}) {
            if (!w.isEmpty() && (bare.equals(w) || bare.startsWith(w + " "))) {
                starter = w;
                break;
            }
        }
        if (starter != null || lower.startsWith("/")) {
            String rest = starter != null ? text.substring(starter.length()) : text.substring(1);
            rest = rest.replaceFirst("^[\\s,.:;!?-]+", "");     // "Command, shout" / "command: shout"
            if (punctuation) rest = applyPunctuation(rest);
            if (rest.isEmpty()) return null;
            // Command name: lowercase, no punctuation stuck to it ("shout," -> "shout").
            String[] parts = rest.split(" ", 2);
            String name = parts[0].replaceAll("[.,:;!?]+$", "").toLowerCase();
            String args = parts.length > 1 ? parts[1].trim() : "";
            args = args.replaceAll("\\.+$", "");                 // no trailing period ("/party invite steve.")
            out = "/" + name + (args.isEmpty() ? "" : " " + args);
        } else {
            if (punctuation) text = applyPunctuation(text);
            if (autoPunctuation) text = autoPunctuate(text);
            if (capitalize) {
                // First letter, plus "i" on its own ("i'm", "i'll" too).
                text = (" " + text + " ").replaceAll("(?<= )i(?=[ '!?.,:])", "I").trim();
                out = Character.toUpperCase(text.charAt(0)) + text.substring(1);
            } else {
                out = text;
            }
        }
        return out.length() > MAX_LENGTH ? out.substring(0, MAX_LENGTH) : out;
    }
}
