package com.voicehotbar;

import com.sun.jna.Pointer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Background thread: microphone -> Vosk -> recognized command words.
 *
 * Speed: audio is fed in 20 ms chunks and the recognizer's partial (in-progress) guess is checked after
 * every chunk, so a word fires the moment it's recognized instead of after you stop talking.
 */
final class VoiceListener implements Runnable {

    private static final float RATE = 16000f;
    private static final int CHUNK = 640; // 20 ms of 16-bit mono audio

    /** Recognized words, consumed on the game thread. */
    final Queue<Heard> heard = new ConcurrentLinkedQueue<Heard>();

    // Written by the game thread.
    volatile boolean pushToTalkDown;
    volatile boolean muted;
    volatile int configVersion;
    /** Items currently in the hotbar (null = not in a world: listen for everything). Written by the game thread. */
    volatile java.util.Set<Target> available;
    private java.util.Set<Target> builtAvailable;

    volatile String status = "Starting...";
    volatile boolean ready;

    private final File baseDir;
    private final Vosk vosk = new Vosk();
    private int builtVersion = -1;
    private Pointer recognizer;
    private TargetDataLine line;
    private String openedMic;

    // Command words from the current utterance that already fired (so partial + final don't double-fire).
    private int fired;

    // ---- dictation ----
    /** Keeps listening this long after the key is released, so the last word isn't cut off. */
    private static final long DICTATE_TAIL_MS = 300;
    volatile boolean dictateDown;            // written by the game thread
    volatile String dictationPreview;        // live text while dictating, read by the game thread
    /** Finished messages: {final text, preview text}. */
    final Queue<String[]> dictated = new ConcurrentLinkedQueue<String[]>();
    /** Third element of a dictated entry when the text came from Whisper. */
    static final String WHISPER = "whisper";

    // ---- Whisper (accurate dictation) ----
    final Whisper whisper;
    /** Player names for Whisper's context; written by the game thread. */
    volatile java.util.Collection<String> playerNames = java.util.Collections.emptyList();
    volatile boolean transcribing;
    private final byte[] recording = new byte[16000 * 2 * Whisper.MAX_SECONDS];
    private int recordingLen;
    private final java.util.concurrent.ExecutorService whisperWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "VoiceHotbar dictation");
                    t.setDaemon(true);
                    return t;
                }
            });
    private final StringBuilder partialText = new StringBuilder();
    private String lastPartial = "";

    private static void append(StringBuilder sb, String text) {
        if (text == null || text.isEmpty()) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(text);
    }
    private Pointer dictRec;
    private boolean wasDictating;
    private long dictateTailUntil;
    private final StringBuilder dictText = new StringBuilder();

    private void appendDictation(String text) {
        if (text == null || text.isEmpty()) return;
        if (dictText.length() > 0) dictText.append(' ');
        dictText.append(text);
    }

    VoiceListener(File baseDir) {
        this.baseDir = baseDir;
        this.whisper = new Whisper(baseDir);
    }

    /** Messages for the player's chat (download started, ready, failed); read by the game thread. */
    final Queue<String> notices = new ConcurrentLinkedQueue<String>();
    private volatile boolean whisperStarting;

    /** Starts Whisper in the background if it's wanted and not running yet (first run downloads the model). */
    void ensureWhisper() {
        VoiceConfig cfg = VoiceHotbar.config;
        if (!cfg.whisperDictation || !cfg.dictation || whisper.ready || whisperStarting) return;
        whisperStarting = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    whisper.start(new Runnable() {
                        @Override
                        public void run() {
                            notices.add("Downloading the accurate dictation model (148 MB, one time). Dictation uses the fast engine until it's done.");
                        }
                    });
                    notices.add("Accurate dictation (Whisper) is ready.");
                } catch (Throwable e) {
                    whisper.status = "Failed: " + e.getMessage();
                    VoiceHotbar.LOG.error("Whisper failed to start", e);
                    notices.add("Accurate dictation couldn't start (" + e.getMessage() + "). Using the fast engine.");
                }
            }
        }, "VoiceHotbar whisper start");
        t.setDaemon(true);
        t.start();
    }

    void start() {
        Thread t = new Thread(this, "VoiceHotbar listener");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        try {
            status = "Loading speech model...";
            vosk.init(baseDir);
        } catch (Throwable t) {
            status = "Failed to load: " + t.getMessage();
            VoiceHotbar.LOG.error("Voice recognizer failed to load", t);
            return;
        }

        byte[] buf = new byte[CHUNK];
        boolean wasTalking = false;
        while (true) {
            try {
                VoiceConfig cfg = VoiceHotbar.config;
                if (!cfg.enabled) {
                    closeMic();
                    ready = false;
                    status = "Off";
                    Thread.sleep(250);
                    continue;
                }
                java.util.Set<Target> avail = available;
                boolean hotbarChanged = avail == null ? builtAvailable != null : !avail.equals(builtAvailable);
                if (builtVersion != configVersion || hotbarChanged) {
                    builtAvailable = avail;
                    builtVersion = configVersion;
                    // One recognizer for all command words, so similar words compete and the best match wins.
                    vosk.free(recognizer);
                    recognizer = null;
                    fired = 0;
                    if (!cfg.words.isEmpty()) {
                        status = "Loading speech model...";
                        vosk.loadModel(Vosk.Lang.EN);
                        recognizer = vosk.newRecognizer(Vosk.Lang.EN, RATE, cfg.words.grammarJson(avail));
                    }
                    if (!cfg.microphone.equals(openedMic)) closeMic();
                }
                if (line == null && !openMic(cfg.microphone)) {
                    ready = false;
                    Thread.sleep(2000);
                    continue;
                }
                ready = true;
                status = "Listening (" + describeMic() + ")";

                int n = line.read(buf, 0, buf.length); // keeps the mic buffer drained even when not feeding
                if (n <= 0) continue;

                // ---- Dictation: while the key is held, all audio goes to the free-speech recognizer ----
                long now = System.currentTimeMillis();
                if (cfg.dictation && dictateDown) dictateTailUntil = now + DICTATE_TAIL_MS;
                boolean dictating = cfg.dictation && (dictateDown || (wasDictating && now < dictateTailUntil));
                if (dictating) {
                    if (dictRec == null) {
                        status = "Loading speech model...";
                        vosk.loadModel(Vosk.Lang.EN);
                        dictRec = vosk.newFreeRecognizer(Vosk.Lang.EN, RATE);
                    }
                    if (!wasDictating) {
                        // Pause hotbar words so "bring blocks to mid" doesn't switch items.
                        if (recognizer != null) vosk.reset(recognizer);
                        fired = 0;
                        wasTalking = false;
                        dictText.setLength(0);
                        partialText.setLength(0);
                        lastPartial = "";
                        recordingLen = 0;
                        wasDictating = true;
                    }
                    // Keep the raw audio for Whisper (up to its 15 s window).
                    if (recordingLen + n <= recording.length) {
                        System.arraycopy(buf, 0, recording, recordingLen, n);
                        recordingLen += n;
                    }
                    if (vosk.accept(dictRec, buf, n)) {
                        appendDictation(vosk.result(dictRec));
                        append(partialText, lastPartial);
                        lastPartial = "";
                    } else {
                        String p = vosk.partial(dictRec);
                        if (!p.isEmpty()) lastPartial = p;
                    }
                    dictationPreview = (dictText + " " + lastPartial).trim();
                    continue;
                }
                if (wasDictating) {
                    // Key released (plus a short tail so the last word isn't cut off): send it.
                    // The final pass sometimes rewrites words that were right in the preview (a name
                    // turning into "st in a the..."), so the preview version goes along as an alternative.
                    appendDictation(vosk.finalResult(dictRec));
                    vosk.reset(dictRec);
                    append(partialText, lastPartial);
                    final String voskFinal = dictText.toString(), voskPreview = partialText.toString();
                    // No speech at all: skip Whisper, which tends to invent text ("Thank you.") for silence.
                    boolean heardSomething = !voskFinal.trim().isEmpty() || !voskPreview.trim().isEmpty();
                    if (cfg.whisperDictation && whisper.ready && heardSomething) {
                        // Accurate pass on a worker thread, so the mic and hotbar words keep running meanwhile.
                        final byte[] audio = java.util.Arrays.copyOf(recording, recordingLen);
                        final java.util.Collection<String> names = playerNames;
                        dictationPreview = "…";
                        transcribing = true;
                        whisperWorker.execute(new Runnable() {
                            @Override
                            public void run() {
                                String text = whisper.transcribe(audio, audio.length, names);
                                if (text != null) dictated.add(new String[]{text, null, WHISPER});
                                else dictated.add(new String[]{voskFinal, voskPreview}); // fall back to the fast result
                                transcribing = false;
                                dictationPreview = null;
                            }
                        });
                    } else {
                        dictated.add(new String[]{voskFinal, voskPreview});
                        dictationPreview = null;
                    }
                    wasDictating = false;
                }

                boolean talking = !muted && (!cfg.pushToTalk || pushToTalkDown);
                if (recognizer != null) {
                    if (talking) {
                        if (vosk.accept(recognizer, buf, n)) {
                            fire(vosk.result(recognizer), cfg);
                            fired = 0;
                        } else if (cfg.instant) {
                            fire(vosk.partial(recognizer), cfg);
                        }
                    } else if (wasTalking) {
                        // Push-to-talk released: finish whatever was said.
                        fire(vosk.finalResult(recognizer), cfg);
                        vosk.reset(recognizer);
                        fired = 0;
                    }
                }
                wasTalking = talking;
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                VoiceHotbar.LOG.warn("Voice listener error", t);
                closeMic();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /** Queues command words from {@code text} that haven't fired yet in this utterance. */
    private void fire(String text, VoiceConfig cfg) {
        // One command per utterance: the first command word fires, and nothing else does until you pause.
        // The rest of a spoken word often looks like another command ("gapple" -> "gap" + "wool",
        // "pickaxe" -> "pick" + "axe"); this ignores those leftovers no matter which word it is.
        if (text.isEmpty() || fired > 0) return;
        for (String token : cfg.words.joinPhrases(text).split("\\s+")) {
            Target t = cfg.words.lookup(token);
            if (t != null) {
                heard.add(new Heard(cfg.words.display(token), t));
                fired = 1;
                return;
            }
        }
    }

    static final class Heard {
        final String word;
        final Target target;

        Heard(String word, Target target) {
            this.word = word;
            this.target = target;
        }
    }

    // ---- microphone ----

    private boolean openMic(String wanted) {
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        try {
            TargetDataLine l = null;
            if (!wanted.isEmpty()) {
                for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                    if (mi.getName().toLowerCase().contains(wanted.toLowerCase())) {
                        Mixer m = AudioSystem.getMixer(mi);
                        if (m.isLineSupported(info)) {
                            l = (TargetDataLine) m.getLine(info);
                            break;
                        }
                    }
                }
            }
            if (l == null) l = (TargetDataLine) AudioSystem.getLine(info); // system default mic
            l.open(fmt, CHUNK * 8);
            l.start();
            line = l;
            openedMic = wanted;
            return true;
        } catch (Throwable t) {
            status = "No microphone available";
            return false;
        }
    }

    private void closeMic() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Throwable ignored) {
            }
            line = null;
        }
        openedMic = null;
    }

    private String describeMic() {
        return VoiceHotbar.config.microphone.isEmpty() ? "default mic" : VoiceHotbar.config.microphone;
    }

    /** Names of capture devices, for /voice mics. */
    static List<String> microphones() {
        List<String> out = new ArrayList<String>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, new AudioFormat(RATE, 16, 1, true, false));
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mi).isLineSupported(info)) out.add(mi.getName());
            } catch (Throwable ignored) {
            }
        }
        return out;
    }
}
