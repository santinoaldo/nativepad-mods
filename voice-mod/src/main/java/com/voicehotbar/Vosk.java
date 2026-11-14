package com.voicehotbar;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal binding to the Vosk offline speech recognizer's C API, through the JNA 3.4 that ships with
 * Minecraft 1.8.9 (the official vosk-java needs JNA 5, which would clash with the game's copy).
 */
final class Vosk {

    private static final String[] NATIVES = {"libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libstdc++-6.dll", "libvosk.dll"};

    /** Bundled speech models. Each command word is recognized by the model of its language. */
    enum Lang {
        EN("vosk-model-small-en-us-0.15", "model-en.zip");

        final String dir, zip;

        Lang(String dir, String zip) {
            this.dir = dir;
            this.zip = zip;
        }
    }

    private Function setLogLevel, modelNew, recognizerNewGrm, acceptWaveform, partialResult, result,
            finalResult, reset, recognizerFree, setWords, recognizerNew;
    private final Pointer[] models = new Pointer[Lang.values().length];
    private File baseDir;

    /** Unpacks the natives into {@code baseDir} (first run only) and loads them. Slow: call off-thread. */
    void init(File baseDir) throws IOException {
        this.baseDir = baseDir;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IOException("Voice commands currently support Windows only");
        }
        File nativeDir = new File(baseDir, "natives-0.3.45");
        nativeDir.mkdirs();
        for (String n : NATIVES) {
            File f = new File(nativeDir, n);
            if (!f.isFile()) copy("/voicehotbar/natives/" + n, f);
        }
        // Load dependencies first, in order, so libvosk.dll finds them.
        NativeLibrary lib = null;
        for (String n : NATIVES) lib = NativeLibrary.getInstance(new File(nativeDir, n).getAbsolutePath());

        setLogLevel = lib.getFunction("vosk_set_log_level");
        modelNew = lib.getFunction("vosk_model_new");
        recognizerNewGrm = lib.getFunction("vosk_recognizer_new_grm");
        acceptWaveform = lib.getFunction("vosk_recognizer_accept_waveform");
        partialResult = lib.getFunction("vosk_recognizer_partial_result");
        result = lib.getFunction("vosk_recognizer_result");
        finalResult = lib.getFunction("vosk_recognizer_final_result");
        reset = lib.getFunction("vosk_recognizer_reset");
        recognizerFree = lib.getFunction("vosk_recognizer_free");
        setWords = lib.getFunction("vosk_recognizer_set_words");
        recognizerNew = lib.getFunction("vosk_recognizer_new");

        setLogLevel.invoke(new Object[]{-1});
    }

    /** Unpacks (first use only) and loads a language model. Slow: call off-thread. */
    void loadModel(Lang lang) throws IOException {
        if (models[lang.ordinal()] != null) return;
        File modelDir = new File(baseDir, lang.dir);
        if (!new File(modelDir, "README").isFile()) unzip("/voicehotbar/" + lang.zip, baseDir);
        Pointer m = modelNew.invokePointer(new Object[]{modelDir.getAbsolutePath()});
        if (m == null) throw new IOException("Could not load speech model from " + modelDir);
        models[lang.ordinal()] = m;
    }

    /** Recognizer limited to {@code grammarJson} (a JSON array of phrases), which makes it fast and accurate. */
    Pointer newRecognizer(Lang lang, float sampleRate, String grammarJson) {
        // Pass the grammar as UTF-8 bytes so accented words ("três") reach Vosk intact.
        return recognizerNewGrm.invokePointer(new Object[]{models[lang.ordinal()], sampleRate, utf8(grammarJson)});
    }

    /** Open-vocabulary recognizer (any English words), for dictating chat messages. */
    Pointer newFreeRecognizer(Lang lang, float sampleRate) {
        return recognizerNew.invokePointer(new Object[]{models[lang.ordinal()], sampleRate});
    }

    private static byte[] utf8(String s) {
        byte[] b = s.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        return java.util.Arrays.copyOf(b, b.length + 1); // NUL-terminated
    }

    /** Feed audio. Returns true when an utterance just ended (then call {@link #result}). */
    boolean accept(Pointer rec, byte[] pcm, int len) {
        return acceptWaveform.invokeInt(new Object[]{rec, pcm, len}) != 0;
    }

    /** Ask for per-word confidence scores in results. */
    void enableWordConfidence(Pointer rec) {
        setWords.invoke(new Object[]{rec, 1});
    }

    /** Words of a finished utterance with confidence >= {@code min} (needs {@link #enableWordConfidence}). */
    java.util.List<String> confidentResult(Pointer rec, boolean finalResult, double min) {
        Pointer json = (finalResult ? this.finalResult : this.result).invokePointer(new Object[]{rec});
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (json == null) return out;
        java.util.regex.Matcher m = WORD_CONF.matcher(utf8String(json));
        while (m.find()) {
            if (Double.parseDouble(m.group(1)) >= min) out.add(m.group(2));
        }
        return out;
    }

    private static final java.util.regex.Pattern WORD_CONF =
            java.util.regex.Pattern.compile("\"conf\"\\s*:\\s*([0-9.eE+-]+)[^}]*?\"word\"\\s*:\\s*\"([^\"]+)\"");

    private static String utf8String(Pointer p) {
        long len = p.indexOf(0, (byte) 0);
        return new String(p.getByteArray(0, (int) len), java.nio.charset.Charset.forName("UTF-8"));
    }

    String partial(Pointer rec) {
        return text(partialResult.invokePointer(new Object[]{rec}), "partial");
    }

    String result(Pointer rec) {
        return text(result.invokePointer(new Object[]{rec}), "text");
    }

    String finalResult(Pointer rec) {
        return text(finalResult.invokePointer(new Object[]{rec}), "text");
    }

    void reset(Pointer rec) {
        reset.invoke(new Object[]{rec});
    }

    void free(Pointer rec) {
        if (rec != null) recognizerFree.invoke(new Object[]{rec});
    }

    /** Pulls the value of {"key" : "value"} out of Vosk's small JSON results. */
    private static String text(Pointer json, String key) {
        if (json == null) return "";
        String s = utf8String(json);
        int k = s.indexOf("\"" + key + "\"");
        if (k < 0) return "";
        int start = s.indexOf('"', s.indexOf(':', k) + 1);
        int end = start < 0 ? -1 : s.indexOf('"', start + 1);
        return start < 0 || end < 0 ? "" : s.substring(start + 1, end).trim();
    }

    private static void copy(String resource, File to) throws IOException {
        InputStream in = Vosk.class.getResourceAsStream(resource);
        if (in == null) throw new IOException("Missing " + resource + " in mod jar");
        File tmp = new File(to.getPath() + ".tmp");
        try {
            OutputStream out = new FileOutputStream(tmp);
            try {
                pipe(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        if (!tmp.renameTo(to)) throw new IOException("Could not write " + to);
    }

    private static void unzip(String resource, File dir) throws IOException {
        InputStream raw = Vosk.class.getResourceAsStream(resource);
        if (raw == null) throw new IOException("Missing " + resource + " in mod jar");
        String root = dir.getCanonicalPath() + File.separator;
        ZipInputStream zip = new ZipInputStream(raw);
        try {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                File f = new File(dir, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) continue; // ignore unsafe paths
                if (e.isDirectory()) {
                    f.mkdirs();
                } else {
                    f.getParentFile().mkdirs();
                    OutputStream out = new FileOutputStream(f);
                    try {
                        pipe(zip, out);
                    } finally {
                        out.close();
                    }
                }
            }
        } finally {
            zip.close();
        }
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
