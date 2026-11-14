package com.nativepad;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Moves the real Windows cursor from a background thread at ~250 Hz, like a physical mouse or Steam Input.
 *
 * Doing it from the render loop ties smoothness to FPS, and Minecraft caps the title screen at 30 FPS,
 * which made the cursor choppy there. The OS draws the cursor itself, so moving it off-thread is smooth
 * no matter what the game's frame rate is. Win32 cursor calls are thread-safe.
 */
final class MenuCursor implements Runnable {

    private static Function getCursorPos, setCursorPos, getForegroundWindow, getClientRect, clientToScreen;

    static {
        try {
            NativeLibrary user32 = NativeLibrary.getInstance("user32");
            getCursorPos = user32.getFunction("GetCursorPos");
            setCursorPos = user32.getFunction("SetCursorPos");
            getForegroundWindow = user32.getFunction("GetForegroundWindow");
            getClientRect = user32.getFunction("GetClientRect");
            clientToScreen = user32.getFunction("ClientToScreen");
        } catch (Throwable t) {
            getCursorPos = null;
        }
    }

    /** Set by the main thread every frame. */
    volatile boolean active;
    volatile int padIndex = -1;
    volatile Pointer gameWindow;

    private final XInput xinput = new XInput();
    private final XInput.PadState state = new XInput.PadState();
    private final Memory point = new Memory(8), rect = new Memory(16), origin = new Memory(8);
    private float fracX, fracY;
    private long last;

    static boolean available() {
        return getCursorPos != null;
    }

    /** Foreground window handle; called on the main thread while the game window is active. */
    static Pointer foregroundWindow() {
        return available() ? getForegroundWindow.invokePointer(new Object[0]) : null;
    }

    void start() {
        Thread t = new Thread(this, "NativePad menu cursor");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                int index = padIndex;
                Pointer window = gameWindow;
                if (!active || index < 0 || window == null) {
                    last = 0;
                    fracX = fracY = 0f;
                    Thread.sleep(16);
                    continue;
                }
                long now = System.nanoTime();
                float dt = last == 0 ? 0f : Math.min((now - last) / 1e9f, 0.05f);
                last = now;
                if (dt > 0f && xinput.read(index, state)) step(window, dt);
                Thread.sleep(4);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                NativePad.LOG.warn("Menu cursor error", t);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void step(Pointer window, float dt) {
        PadConfig cfg = NativePad.config;
        float[] v = ControllerHandler.shape(state.lx, state.ly, 0.15f, 0.95f, cfg.cursorCurve);
        if (v[0] == 0f && v[1] == 0f) {
            fracX = fracY = 0f;
            return;
        }
        // Only move while Minecraft is the window in front.
        Pointer fg = getForegroundWindow.invokePointer(new Object[0]);
        if (fg == null || !fg.equals(window)) return;

        if (getClientRect.invokeInt(new Object[]{window, rect}) == 0) return;
        int w = rect.getInt(8), h = rect.getInt(12);
        if (w <= 0 || h <= 0) return;
        origin.setInt(0, 0);
        origin.setInt(4, 0);
        clientToScreen.invokeInt(new Object[]{window, origin});
        int ox = origin.getInt(0), oy = origin.getInt(4);

        float speed = cfg.cursorSpeed * h * dt;
        fracX += v[0] * speed;
        fracY -= v[1] * speed; // screen Y grows downward, stick Y grows upward
        int dx = (int) fracX, dy = (int) fracY;
        fracX -= dx;
        fracY -= dy;
        if (dx == 0 && dy == 0) return;

        if (getCursorPos.invokeInt(new Object[]{point}) == 0) return;
        int nx = Math.max(ox, Math.min(ox + w - 1, point.getInt(0) + dx));
        int ny = Math.max(oy, Math.min(oy + h - 1, point.getInt(4) + dy));
        setCursorPos.invokeInt(new Object[]{nx, ny});
    }
}
