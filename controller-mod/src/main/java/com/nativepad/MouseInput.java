package com.nativepad;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Sends real mouse button presses through Windows (SendInput), the same thing Steam Input does.
 *
 * Used when a controller button is bound to an action that sits on a mouse button (attack, use,
 * pick block). The game and every mod then see a genuine click: CPS counters that read
 * Mouse.isButtonDown, click sounds, mods that listen for mouse events, etc.
 */
final class MouseInput {

    private static final int INPUT_MOUSE = 0;
    private static final int LEFTDOWN = 0x0002, LEFTUP = 0x0004, RIGHTDOWN = 0x0008, RIGHTUP = 0x0010,
            MIDDLEDOWN = 0x0020, MIDDLEUP = 0x0040, XDOWN = 0x0080, XUP = 0x0100;

    private static Function sendInput;
    private static final boolean X64 = com.sun.jna.Native.POINTER_SIZE == 8;
    /** sizeof(INPUT): 40 bytes on 64-bit Windows, 28 on 32-bit. */
    private static final int SIZE = X64 ? 40 : 28;

    static {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                sendInput = NativeLibrary.getInstance("user32").getFunction("SendInput");
            }
        } catch (Throwable ignored) {
            sendInput = null;
        }
    }

    private static final Memory input = new Memory(SIZE);

    static boolean available() {
        return sendInput != null;
    }

    /** LWJGL mouse button index (0 left, 1 right, 2 middle, 3/4 side) for a key code, or -1 if it's a keyboard key. */
    static int buttonFor(int keyCode) {
        int b = keyCode + 100;
        return keyCode < 0 && b >= 0 && b <= 4 ? b : -1;
    }

    static void press(int button, boolean down) {
        if (sendInput == null) return;
        int flags, data = 0;
        switch (button) {
            case 0: flags = down ? LEFTDOWN : LEFTUP; break;
            case 1: flags = down ? RIGHTDOWN : RIGHTUP; break;
            case 2: flags = down ? MIDDLEDOWN : MIDDLEUP; break;
            case 3: flags = down ? XDOWN : XUP; data = 1; break;
            case 4: flags = down ? XDOWN : XUP; data = 2; break;
            default: return;
        }
        Pointer p = input;
        p.clear(SIZE);
        p.setInt(0, INPUT_MOUSE);
        int base = X64 ? 8 : 4; // MOUSEINPUT starts after the type field (+ padding on 64-bit)
        p.setInt(base + 8, data);   // mouseData
        p.setInt(base + 12, flags); // dwFlags
        sendInput.invokeInt(new Object[]{1, p, SIZE});
    }
}
