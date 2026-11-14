package com.voicehotbar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collection;

/**
 * Accurate dictation with Whisper (whisper.cpp, MIT license), running locally.
 *
 * The whisper.cpp server is bundled in the jar and started as a hidden background process that only
 * listens on 127.0.0.1, with the model kept loaded so each message takes ~0.6 s (CPU, base.en, measured).
 * The model (148 MB) is downloaded once from the official whisper.cpp model page and checked by SHA-256.
 * Nothing is sent anywhere except that one-time model download.
 */
final class Whisper {

    private static final String EXE = "voicehotbar-whisper.exe";
    private static final String[] FILES = {EXE, "whisper.dll", "ggml.dll", "ggml-base.dll", "ggml-cpu.dll", "LICENSE-whisper.cpp.txt"};
    private static final String MODEL = "ggml-base.en.bin";
    private static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin";
    private static final String MODEL_SHA256 = "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002";
    /** Audio window: 768 = ~15 s, which is plenty for a 100-character chat line and twice as fast as the full 30 s. */
    static final int MAX_SECONDS = 15;
    private static final String AUDIO_CTX = "768";
    private static final String THREADS = "6";

    private final File dir;
    private Process server;
    private int port;

    volatile String status = "Not started";
    volatile boolean ready;

    Whisper(File baseDir) {
        this.dir = new File(baseDir, "whisper-1.8.2");
    }

    /** Unpacks the server, downloads the model if needed, starts the server. Slow: call off-thread. */
    void start(Runnable onDownloadStart) throws IOException {
        dir.mkdirs();
        for (String f : FILES) {
            File out = new File(dir, f);
            if (!out.isFile()) copyResource("/voicehotbar/whisper/" + f, out);
        }
        File model = new File(dir, MODEL);
        if (!model.isFile()) {
            status = "Downloading dictation model (148 MB)...";
            if (onDownloadStart != null) onDownloadStart.run();
            download(MODEL_URL, model, MODEL_SHA256);
        }

        // A server left over from a game that crashed would hold memory; it's ours (unique name), so stop it.
        try {
            new ProcessBuilder("taskkill", "/IM", EXE, "/F").redirectErrorStream(true).start().waitFor();
        } catch (Exception ignored) {
        }

        port = freePort();
        status = "Starting dictation engine...";
        ProcessBuilder pb = new ProcessBuilder(new File(dir, EXE).getAbsolutePath(),
                "-m", model.getAbsolutePath(), "--host", "127.0.0.1", "--port", String.valueOf(port),
                "-t", THREADS, "-ac", AUDIO_CTX);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(dir, "server.log"));
        server = pb.start(); // Java starts console programs without a window on Windows
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }, "VoiceHotbar whisper shutdown"));

        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive()) throw new IOException("Dictation engine exited; see " + new File(dir, "server.log"));
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/").openConnection();
                c.setConnectTimeout(500);
                c.setReadTimeout(500);
                c.getResponseCode();
                c.disconnect();
                ready = true;
                status = "Ready";
                return;
            } catch (IOException notYet) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
        throw new IOException("Dictation engine didn't start in time");
    }

    void stop() {
        ready = false;
        if (server != null) server.destroy();
    }

    private boolean isAlive() {
        try {
            server.exitValue();
            return false;
        } catch (IllegalThreadStateException running) {
            return true;
        }
    }

    /**
     * Transcribes 16 kHz 16-bit mono PCM. {@code names} are given to Whisper as context so it spells
     * player names the way they're written. Returns null on failure.
     */
    String transcribe(byte[] pcm, int length, Collection<String> names) {
        if (!ready || length <= 0) return null;
        try {
            StringBuilder prompt = new StringBuilder("Minecraft Bedwars chat. Words: gapple, obsidian, shout, bed, rush, diamonds, emeralds.");
            int n = 0;
            for (String name : names) {
                if (n++ == 0) prompt.append(" Players: ");
                else prompt.append(", ");
                prompt.append(name);
                if (n >= 40) break;
            }
            if (n > 0) prompt.append('.');

            String boundary = "----voicehotbar" + System.nanoTime();
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/inference").openConnection();
            c.setDoOutput(true);
            c.setConnectTimeout(2000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            OutputStream out = c.getOutputStream();
            field(out, boundary, "response_format", "json");
            field(out, boundary, "temperature", "0.0");
            field(out, boundary, "prompt", prompt.toString());
            write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n");
            out.write(wavHeader(length));
            out.write(pcm, 0, length);
            write(out, "\r\n--" + boundary + "--\r\n");
            out.close();
            if (c.getResponseCode() != 200) return null;
            String json = readAll(c.getInputStream());
            c.disconnect();
            String text = jsonText(json);
            // Drop noise tags Whisper adds for non-speech: "[BLANK_AUDIO]", "(wind blowing)", "[ Silence ]".
            return text == null ? null : text.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", "").replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            VoiceHotbar.LOG.warn("Whisper transcription failed: " + e);
            return null;
        }
    }

    // ---- helpers ----

    private static void field(OutputStream out, String boundary, String name, String value) throws IOException {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes("UTF-8"));
    }

    private static byte[] wavHeader(int dataLen) {
        int rate = 16000, channels = 1, bits = 16, byteRate = rate * channels * bits / 8;
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        le32(h, 4, 36 + dataLen);
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        le32(h, 16, 16);
        le16(h, 20, 1);
        le16(h, 22, channels);
        le32(h, 24, rate);
        le32(h, 28, byteRate);
        le16(h, 32, channels * bits / 8);
        le16(h, 34, bits);
        put(h, 36, "data");
        le32(h, 40, dataLen);
        return h;
    }

    private static void put(byte[] b, int at, String s) {
        for (int i = 0; i < 4; i++) b[at + i] = (byte) s.charAt(i);
    }

    private static void le32(byte[] b, int at, int v) {
        for (int i = 0; i < 4; i++) b[at + i] = (byte) (v >> (8 * i));
    }

    private static void le16(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }

    /** Pulls "text" out of {"text":"..."} and decodes JSON escapes. */
    static String jsonText(String json) {
        int k = json.indexOf("\"text\"");
        if (k < 0) return null;
        int i = json.indexOf('"', json.indexOf(':', k) + 1) + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char ch = json.charAt(i++);
            if (ch == '"') break;
            if (ch == '\\' && i < json.length()) {
                char e = json.charAt(i++);
                switch (e) {
                    case 'n': case 'r': case 't': sb.append(' '); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    private static int freePort() throws IOException {
        ServerSocket s = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        int p = s.getLocalPort();
        s.close();
        return p;
    }

    private static void copyResource(String resource, File to) throws IOException {
        InputStream in = Whisper.class.getResourceAsStream(resource);
        if (in == null) throw new IOException("Missing " + resource + " in mod jar");
        File tmp = new File(to.getPath() + ".tmp");
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            out.close();
            in.close();
        }
        if (!tmp.renameTo(to)) throw new IOException("Could not write " + to);
    }

    /** Downloads to a temp file, checks SHA-256, then moves it into place. */
    private static void download(String url, File to, String sha256) throws IOException {
        File tmp = new File(to.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        // HttpURLConnection won't follow a redirect to another host by itself, so follow manually.
        for (int hops = 0; hops < 5; hops++) {
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                URL next = new URL(c.getURL(), c.getHeaderField("Location"));
                c.disconnect();
                c = (HttpURLConnection) next.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
            } else {
                break;
            }
        }
        if (c.getResponseCode() != 200) throw new IOException("Model download failed: HTTP " + c.getResponseCode());
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
            }
        } finally {
            out.close();
            in.close();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format("%02x", b));
        if (!hex.toString().equals(sha256)) {
            tmp.delete();
            throw new IOException("Model download was corrupted (checksum mismatch)");
        }
        if (!tmp.renameTo(to)) throw new IOException("Could not save " + to);
    }
}
