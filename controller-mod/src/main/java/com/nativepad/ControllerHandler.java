package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Polls the controller every frame and every game tick.
 *
 * Latency notes:
 *  - Buttons are read at the very start of each game tick (ClientTickEvent START), right before vanilla
 *    processes key bindings, so a press is acted on in the same tick it is seen.
 *  - Buttons are also read every rendered frame, so a tap shorter than one tick (50 ms) is never lost:
 *    presses are applied immediately, releases are deferred until the next tick has seen the press.
 *  - Camera rotation is applied every frame, scaled by real frame time, so aim is smooth at any FPS.
 */
public final class ControllerHandler {

    private static final long RESCAN_NANOS = 2_000_000_000L;
    private static final XInput.Button[] BUTTONS = XInput.Button.values();

    private final Minecraft mc = Minecraft.getMinecraft();
    private final XInput xinput = new XInput();
    private final XInput.PadState state = new XInput.PadState();
    private final XInput.PadState prev = new XInput.PadState();

    private int padIndex = -1;
    private long lastScan;
    private long lastFrame;
    private boolean pendingConnectMessage, pendingDisconnectMessage;

    private final boolean[] suppressed = new boolean[BUTTONS.length];
    private boolean lastInGame;

    // Processed left stick, read by ControllerMovementInput
    static volatile float moveForward, moveStrafe;
    static volatile boolean moveActive;

    // Menu cursor sub-pixel accumulators
    private float cursorFracX, cursorFracY;
    private boolean menuMouseDown;
    private long menuMouseDownAt;

    private final LastSlot lastSlot = new LastSlot();

    // ---- events ----

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            if (mc.thePlayer != null && !(mc.thePlayer.movementInput instanceof ControllerMovementInput)) {
                mc.thePlayer.movementInput = new ControllerMovementInput(mc.gameSettings);
            }
            if (poll()) handleButtons(true);
            flushMessages();
        } else {
            // Keyboard hotkey for the swap, so it also works for Steam Input / keyboard players.
            while (NativePad.swapKey.isPressed()) {
                if (mc.thePlayer != null && mc.currentScreen == null) lastSlot.swap(mc.thePlayer);
            }
            if (mc.thePlayer != null) lastSlot.observe(mc.thePlayer);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min((now - lastFrame) / 1e9f, 0.1f);
        lastFrame = now;
        boolean ok = poll();
        boolean inMenu = ok && mc.currentScreen != null && Display.isActive();
        if (inMenu && MenuCursor.available()) {
            cursor.gameWindow = MenuCursor.foregroundWindow();
            cursor.padIndex = padIndex;
        }
        // In inventories with stick snapping, the stick hops between slots instead of gliding the cursor.
        boolean stickSnap = inMenu && NativePad.config.stickSnapInInventories && mc.currentScreen instanceof GuiContainer;
        cursor.active = inMenu && !stickSnap;
        if (!ok) return;
        handleButtons(false);
        if (mc.currentScreen instanceof BindingsScreen) {
            // Right stick scrolls the bindings list.
            float[] v = shape(state.rx, state.ry, 0.3f, 0.95f, 1.5f);
            scrollAcc += -v[1] * 14f * dt;
            int rows = (int) scrollAcc;
            if (rows != 0) {
                scrollAcc -= rows;
                ((BindingsScreen) mc.currentScreen).scrollBy(rows);
            }
            if (v[1] == 0f) scrollAcc = 0f;
        }
        menuDpad();
        menuStick();
        if (mc.currentScreen == null) {
            applyLook(dt);
        } else if (MenuCursor.available()) {
            // The cursor itself moves on the background thread; just keep any drag in progress updated.
            if (menuMouseDown) {
                int[] s = scaledMouse();
                Reflect.mouseClickMove(mc.currentScreen, s[0], s[1], 0, Minecraft.getSystemTime() - menuMouseDownAt);
            }
        } else {
            moveCursor(dt);
        }
    }

    private final MenuCursor cursor = new MenuCursor();
    private float scrollAcc;

    ControllerHandler() {
        if (MenuCursor.available()) cursor.start();
    }

    // ---- polling ----

    /** Reads the pad into {@link #state}. Returns false (and releases everything) if there's no usable pad. */
    private boolean poll() {
        PadConfig cfg = NativePad.config;
        if (!cfg.enabled || !XInput.isAvailable()) return disconnected();

        if (padIndex < 0) {
            long now = System.nanoTime();
            // Querying empty XInput slots is slow, so only scan occasionally.
            if (now - lastScan < RESCAN_NANOS) return disconnected();
            lastScan = now;
            for (int i = 0; i < 4; i++) {
                if (xinput.read(i, state)) {
                    padIndex = i;
                    prev.clear();
                    pendingConnectMessage = true;
                    pendingDisconnectMessage = false;
                    break;
                }
            }
            if (padIndex < 0) return disconnected();
        } else {
            prev.copyFrom(state);
            if (!xinput.read(padIndex, state)) {
                padIndex = -1;
                lastScan = System.nanoTime();
                pendingDisconnectMessage = true;
                pendingConnectMessage = false;
                return disconnected();
            }
        }
        // Ignore input while the game window isn't focused.
        if (!Display.isActive()) {
            state.clear();
        }
        updateMoveStick(cfg);
        return true;
    }

    /** Per controller button: the real mouse button it is holding down, or -1. */
    private final int[] mouseFor = initMouseFor();

    private static int[] initMouseFor() {
        int[] a = new int[XInput.Button.values().length];
        java.util.Arrays.fill(a, -1);
        return a;
    }

    private boolean disconnected() {
        for (int i = 0; i < mouseFor.length; i++) {
            if (mouseFor[i] >= 0) {
                MouseInput.press(mouseFor[i], false);
                mouseFor[i] = -1;
            }
        }
        moveActive = false;
        moveForward = moveStrafe = 0f;
        if (!holding.isEmpty()) {
            for (KeyBinding kb : holding) Reflect.setPressed(kb, false);
            holding.clear();
        }
        state.clear();
        prev.clear();
        return false;
    }

    // ---- binding resolution ----

    /** Resolved target per button: a KeyBinding, an Action, or null. Rebuilt when config or key list changes. */
    private final Object[] resolved = new Object[BUTTONS.length];
    private int resolvedVersion = -1, resolvedKeyCount = -1;

    private Object target(XInput.Button b) {
        PadConfig cfg = NativePad.config;
        KeyBinding[] all = mc.gameSettings.keyBindings;
        if (cfg.version != resolvedVersion || all.length != resolvedKeyCount) {
            resolvedVersion = cfg.version;
            resolvedKeyCount = all.length;
            for (XInput.Button btn : BUTTONS) {
                String t = cfg.buttons.get(btn);
                Object r = null;
                Action special = Action.special(t);
                if (special != null) {
                    r = special == Action.NONE ? null : special;
                } else if (t != null) {
                    String desc = t.substring(Action.KEY.length());
                    for (KeyBinding kb : all) {
                        if (kb.getKeyDescription().equals(desc)) {
                            r = kb == NativePad.swapKey ? Action.SWAP_LAST : kb;
                            break;
                        }
                    }
                }
                resolved[btn.ordinal()] = r;
            }
        }
        return resolved[b.ordinal()];
    }

    private int hotbarIndex(KeyBinding kb) {
        KeyBinding[] hotbar = mc.gameSettings.keyBindsHotbar;
        for (int i = 0; i < hotbar.length; i++) if (hotbar[i] == kb) return i;
        return -1;
    }

    // ---- buttons ----

    // Key bindings currently held down by the controller, and the ones wanted this poll.
    private final java.util.Set<KeyBinding> holding =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<KeyBinding, Boolean>());
    private final java.util.Set<KeyBinding> want =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<KeyBinding, Boolean>());

    private void handleButtons(boolean tickPass) {
        PadConfig cfg = NativePad.config;
        float trig = cfg.triggerThreshold;
        boolean inGame = mc.thePlayer != null && mc.currentScreen == null;

        want.clear();

        // Buttons held while a menu opens or closes (e.g. B to close a chest) are ignored until released,
        // so they don't leak into the other context as a sneak/jump.
        boolean contextChanged = inGame != lastInGame;
        lastInGame = inGame;

        for (XInput.Button b : BUTTONS) {
            boolean down = state.isDown(b, trig);
            int bi = b.ordinal();
            // Let go of a real mouse button we're holding as soon as the controller button is released
            // (or a menu opens), exactly like lifting your finger off the mouse.
            if (mouseFor[bi] >= 0 && (!down || !inGame)) {
                MouseInput.press(mouseFor[bi], false);
                mouseFor[bi] = -1;
            }
            if (contextChanged && down) suppressed[bi] = true;
            if (suppressed[bi]) {
                if (down) continue;
                suppressed[bi] = false;
                continue;
            }
            boolean pressed = down && !prev.isDown(b, trig);
            boolean released = !down && prev.isDown(b, trig);

            if (!inGame) {
                if (mc.currentScreen != null) handleMenuButton(b, pressed, released);
                continue;
            }
            Object t = target(b);
            if (t instanceof KeyBinding) {
                KeyBinding kb = (KeyBinding) t;
                int slot = hotbarIndex(kb);
                if (slot >= 0) {
                    // Select instantly instead of waiting for vanilla's key loop.
                    if (pressed) mc.thePlayer.inventory.currentItem = slot;
                } else if (MouseInput.available() && MouseInput.buttonFor(kb.getKeyCode()) >= 0) {
                    // Bound to a mouse button (attack/use/pick block): send a real click, like Steam Input,
                    // so CPS counters and click-based mods see it too.
                    if (pressed) {
                        int mb = MouseInput.buttonFor(kb.getKeyCode());
                        MouseInput.press(mb, true);
                        mouseFor[b.ordinal()] = mb;
                    }
                } else if (isKeyboardLoopOnly(kb)) {
                    // Vanilla only checks these inside its physical-keyboard loop, so do what it would do.
                    if (pressed) runKeyboardLoopAction(kb);
                } else if (down) {
                    want.add(kb);
                    if (pressed && !kb.getKeyCategory().startsWith("key.categories.")) moddedPresses.add(kb);
                }
            } else if (t instanceof Action && pressed) {
                firePress((Action) t);
            }
        }
        if (inGame && cfg.autoSprint && moveActive && moveForward > 0f) {
            want.add(mc.gameSettings.keyBindSprint);
        }

        // Held keys behave exactly like a physical key: down while held, one press counted per new press.
        for (KeyBinding kb : want) {
            // Re-assert every poll: vanilla clears all keys whenever a menu opens.
            Reflect.setPressed(kb, true);
            if (holding.add(kb)) Reflect.addPress(kb);
        }
        // Many mods only check their keys when Forge reports a keyboard event, so report one.
        if (!moddedPresses.isEmpty()) {
            moddedPresses.clear();
            MinecraftForge.EVENT_BUS.post(new InputEvent.KeyInputEvent());
        }
        if (tickPass || !inGame) {
            java.util.Iterator<KeyBinding> it = holding.iterator();
            while (it.hasNext()) {
                KeyBinding kb = it.next();
                if (!want.contains(kb)) {
                    Reflect.setPressed(kb, false);
                    it.remove();
                }
            }
        }
    }

    private final java.util.List<KeyBinding> moddedPresses = new java.util.ArrayList<KeyBinding>();

    /** Keys vanilla 1.8.9 only reads while processing a physical keyboard event. */
    private boolean isKeyboardLoopOnly(KeyBinding kb) {
        GameSettings gs = mc.gameSettings;
        return kb == gs.keyBindTogglePerspective || kb == gs.keyBindSmoothCamera
                || kb == gs.keyBindFullscreen || kb == gs.keyBindScreenshot;
    }

    /** Same effects as Minecraft.runTick's keyboard loop. */
    private void runKeyboardLoopAction(KeyBinding kb) {
        GameSettings gs = mc.gameSettings;
        if (kb == gs.keyBindTogglePerspective) {
            if (++gs.thirdPersonView > 2) gs.thirdPersonView = 0;
            if (gs.thirdPersonView == 0) mc.entityRenderer.loadEntityShader(mc.getRenderViewEntity());
            else if (gs.thirdPersonView == 1) mc.entityRenderer.loadEntityShader(null);
            mc.renderGlobal.setDisplayListEntitiesDirty();
        } else if (kb == gs.keyBindSmoothCamera) {
            gs.smoothCamera = !gs.smoothCamera;
        } else if (kb == gs.keyBindFullscreen) {
            mc.toggleFullscreen();
        } else if (kb == gs.keyBindScreenshot) {
            mc.ingameGUI.getChatGUI().printChatMessage(ScreenShotHelper.saveScreenshot(
                    mc.mcDataDir, mc.displayWidth, mc.displayHeight, mc.getFramebuffer()));
        }
    }

    private void firePress(Action a) {
        EntityPlayerSP p = mc.thePlayer;
        switch (a) {
            case SWAP_LAST:
                lastSlot.swap(p);
                return;
            case HOTBAR_PREV:
                p.inventory.currentItem = (p.inventory.currentItem + 8) % 9;
                return;
            case HOTBAR_NEXT:
                p.inventory.currentItem = (p.inventory.currentItem + 1) % 9;
                return;
            case PAUSE:
                mc.displayInGameMenu();
                return;
            default:
        }
    }

    // ---- look ----

    private void applyLook(float dt) {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || dt <= 0f) return;
        PadConfig cfg = NativePad.config;
        float[] v = shape(state.rx, state.ry, cfg.aimDeadzone, cfg.aimOuterDeadzone, cfg.aimCurve);
        if (v[0] == 0f && v[1] == 0f) {
            lookAccX = lookAccY = 0f;
            return;
        }
        float yawDeg = v[0] * cfg.yawSpeed * dt;
        float pitchUpDeg = v[1] * cfg.pitchSpeed * dt * (cfg.invertY ? -1f : 1f);

        // Turn in whole "mouse counts", exactly like vanilla EntityRenderer does for a real mouse.
        // A mouse can only rotate in multiples of this step; perfectly smooth rotation is a classic
        // aimbot signature that anticheats look for.
        float f = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float mult = f * f * f * 8.0f;
        float degPerCount = mult * 0.15f;
        lookAccX += yawDeg / degPerCount;
        lookAccY += pitchUpDeg / degPerCount;
        int cx = (int) lookAccX, cy = (int) lookAccY;
        lookAccX -= cx;
        lookAccY -= cy;
        if (cx != 0 || cy != 0) {
            // Same call vanilla makes: setAngles(dx * mult, dy * mult). Also updates prevRotation for smooth rendering.
            p.setAngles(cx * mult, cy * mult);
        }
    }

    private float lookAccX, lookAccY;

    // ---- movement ----

    private void updateMoveStick(PadConfig cfg) {
        if (mc.currentScreen != null) {
            moveActive = false;
            moveForward = moveStrafe = 0f;
            return;
        }
        float x = state.lx, y = state.ly;
        float mag = (float) Math.sqrt(x * x + y * y);
        if (mag < cfg.moveDeadzone) {
            moveActive = false;
            moveForward = moveStrafe = 0f;
            return;
        }
        if (!cfg.analogMovement) {
            // Keyboard-exact: snap to the nearest of 8 directions with values of exactly -1, 0 or 1,
            // the same inputs WASD can produce. Server anticheats predict movement from these
            // and pull you back (setback) for anything in between.
            double angle = Math.atan2(y, x);
            double snapped = Math.round(angle / (Math.PI / 4)) * (Math.PI / 4);
            moveForward = Math.round(Math.sin(snapped));
            moveStrafe = -Math.round(Math.cos(snapped)); // vanilla: positive strafe = left
            moveActive = true;
            return;
        }
        float amount = Math.min(1f, (mag - cfg.moveDeadzone) / (0.9f - cfg.moveDeadzone));
        // Map the circle onto a square so full diagonal tilt behaves like W+A (and still sprints).
        float nx = x / mag, ny = y / mag;
        float scale = amount / Math.max(Math.abs(nx), Math.abs(ny));
        moveForward = clamp(ny * scale);
        moveStrafe = clamp(-nx * scale); // vanilla: positive strafe = left
        moveActive = true;
    }

    // ---- menus ----

    private void moveCursor(float dt) {
        if (dt <= 0f) return;
        PadConfig cfg = NativePad.config;
        float[] v = shape(state.lx, state.ly, 0.15f, 0.95f, cfg.cursorCurve);
        if (v[0] == 0f && v[1] == 0f) return;
        float speed = cfg.cursorSpeed * mc.displayHeight * dt;
        cursorFracX += v[0] * speed;
        cursorFracY += v[1] * speed; // LWJGL mouse Y grows upward, same as stick Y
        int dx = (int) cursorFracX, dy = (int) cursorFracY;
        cursorFracX -= dx;
        cursorFracY -= dy;
        if (dx == 0 && dy == 0) return;
        int nx = Math.max(0, Math.min(mc.displayWidth - 1, Mouse.getX() + dx));
        int ny = Math.max(0, Math.min(mc.displayHeight - 1, Mouse.getY() + dy));
        Mouse.setCursorPosition(nx, ny);
        if (menuMouseDown) {
            int[] s = scaledMouse();
            Reflect.mouseClickMove(mc.currentScreen, s[0], s[1], 0, Minecraft.getSystemTime() - menuMouseDownAt);
        }
    }

    // ---- D-pad snap navigation in menus ----

    private static final XInput.Button[] DPAD = {XInput.Button.DPAD_UP, XInput.Button.DPAD_DOWN,
            XInput.Button.DPAD_LEFT, XInput.Button.DPAD_RIGHT};
    private static final int[][] DPAD_DIR = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private final boolean[] dpadWas = new boolean[4];
    private final long[] dpadRepeatAt = new long[4];
    private boolean wasInMenu;

    /** D-pad in a menu: jump to the next slot/button that way. Holding repeats. */
    private boolean stickHeld;
    private long stickRepeatAt;

    /**
     * Left stick in inventories/chests/shops: hop between slots like the D-pad. Flick = one slot; hold =
     * repeat, faster the further you push. Direction is rounded to up/down/left/right so a slightly
     * crooked flick still moves straight along the grid.
     */
    private void menuStick() {
        GuiScreen screen = mc.currentScreen;
        boolean on = screen instanceof GuiContainer && NativePad.config.stickSnapInInventories && Display.isActive();
        float[] v = shape(state.lx, state.ly, 0.15f, 0.95f, 1f);
        float mag = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1]);
        if (!on || mag < 0.5f) {
            stickHeld = false; // released (or not in an inventory): next push is a fresh flick
            return;
        }
        long now = System.currentTimeMillis();
        if (stickHeld && now < stickRepeatAt) return;
        int dx = 0, dy = 0;
        if (Math.abs(v[0]) >= Math.abs(v[1])) dx = v[0] > 0 ? 1 : -1;
        else dy = v[1] > 0 ? -1 : 1; // stick up = screen up
        MenuSnap.step(mc, screen, dx, dy);
        stickRepeatAt = now + (stickHeld ? (mag > 0.9f ? 90 : 160) : 300);
        stickHeld = true;
    }

    private void menuDpad() {
        GuiScreen screen = mc.currentScreen;
        boolean inMenu = screen != null && NativePad.config.snapToSlots
                && !(screen instanceof BindingsScreen && ((BindingsScreen) screen).isListening());
        long now = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            boolean down = state.isDown(DPAD[i], 0f);
            if (inMenu && wasInMenu && down) {
                if (!dpadWas[i]) {
                    MenuSnap.step(mc, screen, DPAD_DIR[i][0], DPAD_DIR[i][1]);
                    dpadRepeatAt[i] = now + 350;
                } else if (now >= dpadRepeatAt[i]) {
                    MenuSnap.step(mc, screen, DPAD_DIR[i][0], DPAD_DIR[i][1]);
                    dpadRepeatAt[i] = now + 90;
                }
            }
            dpadWas[i] = down; // a direction already held when the menu opens doesn't count as a press
        }
        wasInMenu = inMenu;
    }

    private void handleMenuButton(XInput.Button b, boolean pressed, boolean released) {
        GuiScreen screen = mc.currentScreen;
        // Bindings screen waiting for "press a controller button": that press is the answer.
        if (screen instanceof BindingsScreen && ((BindingsScreen) screen).isListening()) {
            menuMouseDown = false;
            if (pressed) ((BindingsScreen) screen).onControllerButton(b);
            return;
        }
        // The button bound to Inventory closes inventories/chests too, like the keyboard key does.
        if (pressed && screen instanceof GuiContainer && mc.thePlayer != null
                && b != XInput.Button.A && b != XInput.Button.X
                && target(b) == mc.gameSettings.keyBindInventory) {
            menuMouseDown = false;
            mc.thePlayer.closeScreen();
            return;
        }
        if (b == XInput.Button.A) {
            int[] s = scaledMouse();
            if (pressed) {
                menuMouseDown = true;
                menuMouseDownAt = Minecraft.getSystemTime();
                Reflect.mouseClicked(screen, s[0], s[1], 0);
            } else if (released && menuMouseDown) {
                menuMouseDown = false;
                Reflect.mouseReleased(screen, s[0], s[1], 0);
            }
        } else if (b == XInput.Button.X) {
            int[] s = scaledMouse();
            if (pressed) Reflect.mouseClicked(screen, s[0], s[1], 1);
            else if (released) Reflect.mouseReleased(screen, s[0], s[1], 1);
        } else if (pressed && b == XInput.Button.RB) {
            if (screen instanceof GuiContainer && mc.thePlayer != null) {
                Slot slot = Reflect.hoveredSlot((GuiContainer) screen);
                if (slot != null) {
                    // mode 1 = shift-click (quick move)
                    mc.playerController.windowClick(((GuiContainer) screen).inventorySlots.windowId,
                            slot.slotNumber, 0, 1, mc.thePlayer);
                }
            }
        } else if (pressed && (b == XInput.Button.B || b == XInput.Button.START)) {
            menuMouseDown = false;
            Reflect.keyTyped(screen, (char) 0, Keyboard.KEY_ESCAPE);
        }
    }

    private int[] scaledMouse() {
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        return new int[]{Mouse.getX() * w / mc.displayWidth, h - Mouse.getY() * h / mc.displayHeight - 1};
    }

    // ---- helpers ----

    /** Radial deadzone + response curve. Returns a vector with length 0..1. */
    static float[] shape(float x, float y, float dz, float outer, float curve) {
        float mag = (float) Math.sqrt(x * x + y * y);
        if (mag <= dz) return new float[]{0f, 0f};
        float m = Math.min(1f, (mag - dz) / Math.max(0.01f, outer - dz));
        m = (float) Math.pow(m, curve);
        return new float[]{x / mag * m, y / mag * m};
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private void flushMessages() {
        if (mc.thePlayer == null) return;
        if (pendingConnectMessage) {
            pendingConnectMessage = false;
            if (padIndex >= 0) chat(EnumChatFormatting.GREEN + "Controller connected (slot " + (padIndex + 1) + ")");
        }
        if (pendingDisconnectMessage) {
            pendingDisconnectMessage = false;
            chat(EnumChatFormatting.RED + "Controller disconnected");
        }
    }

    XInput xinput() {
        return xinput;
    }

    int padIndex() {
        return padIndex;
    }

    void chat(String msg) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "[NativePad] " + msg));
        }
    }

    String status() {
        if (!XInput.isAvailable()) return "XInput not available (Windows only)";
        if (padIndex < 0) return "No controller found (using " + XInput.libraryName() + ")";
        return "Controller in slot " + (padIndex + 1) + " (" + XInput.libraryName() + ")";
    }

    /** Reflection into private vanilla members (dev names + obfuscated SRG names). Lookups are cached. */
    static final class Reflect {
        private static final Field PRESSED = ReflectionHelper.findField(KeyBinding.class, "pressed", "field_74513_e");
        private static final Field PRESS_TIME = ReflectionHelper.findField(KeyBinding.class, "pressTime", "field_151474_i");
        private static final Field THE_SLOT = ReflectionHelper.findField(GuiContainer.class, "theSlot", "field_147006_u");
        private static final Method MOUSE_CLICKED = method(new String[]{"mouseClicked", "func_73864_a"}, int.class, int.class, int.class);
        private static final Method MOUSE_RELEASED = method(new String[]{"mouseReleased", "func_146286_b"}, int.class, int.class, int.class);
        private static final Method MOUSE_CLICK_MOVE = method(new String[]{"mouseClickMove", "func_146273_a"}, int.class, int.class, int.class, long.class);
        private static final Method KEY_TYPED = method(new String[]{"keyTyped", "func_73869_a"}, char.class, int.class);

        private static Method method(String[] names, Class<?>... args) {
            return ReflectionHelper.findMethod(GuiScreen.class, null, names, args);
        }

        static void setPressed(KeyBinding kb, boolean down) {
            try {
                PRESSED.setBoolean(kb, down);
            } catch (IllegalAccessException ignored) {
            }
        }

        static void addPress(KeyBinding kb) {
            try {
                PRESS_TIME.setInt(kb, PRESS_TIME.getInt(kb) + 1);
            } catch (IllegalAccessException ignored) {
            }
        }

        static Slot hoveredSlot(GuiContainer gui) {
            try {
                return (Slot) THE_SLOT.get(gui);
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        static void mouseClicked(GuiScreen s, int x, int y, int button) {
            invoke(MOUSE_CLICKED, s, x, y, button);
        }

        static void mouseReleased(GuiScreen s, int x, int y, int button) {
            invoke(MOUSE_RELEASED, s, x, y, button);
        }

        static void mouseClickMove(GuiScreen s, int x, int y, int button, long time) {
            invoke(MOUSE_CLICK_MOVE, s, x, y, button, time);
        }

        static void keyTyped(GuiScreen s, char c, int key) {
            invoke(KEY_TYPED, s, c, key);
        }

        private static void invoke(Method m, GuiScreen s, Object... args) {
            if (s == null) return;
            try {
                m.invoke(s, args);
            } catch (Exception e) {
                NativePad.LOG.warn("Menu input failed on " + s.getClass().getName(), e);
            }
        }
    }
}
