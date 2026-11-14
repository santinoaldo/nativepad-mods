package com.nativepad;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;

/**
 * Minimal direct binding to the Windows XInput API through JNA (bundled with Minecraft 1.8.9).
 *
 * XInput is used instead of LWJGL's JInput-based Controllers class because it is lower latency,
 * reports LT and RT as separate axes, and is what Xbox-style pads (like the EasySMX X10) speak natively.
 */
public final class XInput {

    private static final int ERROR_SUCCESS = 0;

    private static Function getState, setState, getBattery;
    private static String loadedLibrary;

    static {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            for (String name : new String[]{"xinput1_4", "xinput1_3", "xinput9_1_0"}) {
                try {
                    NativeLibrary lib = NativeLibrary.getInstance(name);
                    getState = lib.getFunction("XInputGetState");
                    loadedLibrary = name;
                    try {
                        setState = lib.getFunction("XInputSetState");
                    } catch (Throwable ignored) {
                    }
                    try {
                        getBattery = lib.getFunction("XInputGetBatteryInformation"); // not in xinput9_1_0
                    } catch (Throwable ignored) {
                    }
                    break;
                } catch (Throwable ignored) {
                    // try next version
                }
            }
        }
    }

    private final Memory vibration = new Memory(4); // XINPUT_VIBRATION: WORD left (strong), WORD right (light)
    private final Memory battery = new Memory(2);   // XINPUT_BATTERY_INFORMATION: BYTE type, BYTE level

    /** Rumble motors, 0..1. Left = strong/low motor, right = light/high motor. */
    public void vibrate(int index, float left, float right) {
        if (setState == null || index < 0) return;
        vibration.setShort(0, (short) Math.round(Math.max(0f, Math.min(1f, left)) * 65535));
        vibration.setShort(2, (short) Math.round(Math.max(0f, Math.min(1f, right)) * 65535));
        try {
            setState.invokeInt(new Object[]{index, vibration});
        } catch (Throwable ignored) {
        }
    }

    public static final int BATTERY_DISCONNECTED = 0, BATTERY_WIRED = 1, BATTERY_UNKNOWN = 0xFF;

    /** {type, level 0-3 (empty, low, medium, full)} or null if Windows can't tell. */
    public int[] battery(int index) {
        if (getBattery == null || index < 0) return null;
        try {
            if (getBattery.invokeInt(new Object[]{index, (byte) 0, battery}) != ERROR_SUCCESS) return null;
        } catch (Throwable t) {
            return null;
        }
        return new int[]{battery.getByte(0) & 0xFF, battery.getByte(1) & 0xFF};
    }

    /** XINPUT_STATE is 16 bytes: DWORD packet, WORD buttons, BYTE lt, BYTE rt, SHORT lx, ly, rx, ry. */
    private final Memory buffer = new Memory(16);

    public static boolean isAvailable() {
        return getState != null;
    }

    public static String libraryName() {
        return loadedLibrary;
    }

    /** Reads controller {@code index} (0-3) into {@code out}. Returns false if nothing is connected there. */
    public boolean read(int index, PadState out) {
        if (getState == null) return false;
        int result;
        try {
            result = getState.invokeInt(new Object[]{index, buffer});
        } catch (Throwable t) {
            return false;
        }
        if (result != ERROR_SUCCESS) return false;
        out.buttons = buffer.getShort(4) & 0xFFFF;
        out.lt = (buffer.getByte(6) & 0xFF) / 255f;
        out.rt = (buffer.getByte(7) & 0xFF) / 255f;
        out.lx = axis(buffer.getShort(8));
        out.ly = axis(buffer.getShort(10));
        out.rx = axis(buffer.getShort(12));
        out.ry = axis(buffer.getShort(14));
        return true;
    }

    private static float axis(short raw) {
        return Math.max(-1f, raw / 32767f);
    }

    /** Snapshot of one controller. Stick Y is positive when pushed up. */
    public static final class PadState {
        public int buttons;
        public float lt, rt, lx, ly, rx, ry;

        public void copyFrom(PadState o) {
            buttons = o.buttons; lt = o.lt; rt = o.rt;
            lx = o.lx; ly = o.ly; rx = o.rx; ry = o.ry;
        }

        public void clear() {
            buttons = 0; lt = rt = lx = ly = rx = ry = 0f;
        }

        public boolean isDown(Button b, float triggerThreshold) {
            if (b == Button.LT) return lt > triggerThreshold;
            if (b == Button.RT) return rt > triggerThreshold;
            return (buttons & b.mask) != 0;
        }
    }

    public enum Button {
        A(0x1000), B(0x2000), X(0x4000), Y(0x8000),
        LB(0x0100), RB(0x0200), LT(0), RT(0),
        L3(0x0040), R3(0x0080),
        BACK(0x0020), START(0x0010),
        DPAD_UP(0x0001), DPAD_DOWN(0x0002), DPAD_LEFT(0x0004), DPAD_RIGHT(0x0008);

        final int mask;

        Button(int mask) {
            this.mask = mask;
        }
    }
}
