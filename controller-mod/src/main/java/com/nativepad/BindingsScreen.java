package com.nativepad;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Like Minecraft's Controls screen, but for the controller: click an action, then press a controller button.
 * Lists NativePad's own actions plus every key binding in the game (including other mods').
 * Each controller button does one thing; binding it to a new action moves it.
 */
public final class BindingsScreen extends GuiScreen {

    private static final int ROW_H = 22, TOP = 44, DONE_ID = 10000, LISTEN_TIMEOUT_MS = 6000;

    private final GuiScreen parent;
    private final List<Row> rows = new ArrayList<Row>();
    private int scroll;
    private int listening = -1;
    private long listenStart;

    private static final class Row {
        final String label;
        final String target; // null for a section header

        Row(String label, String target) {
            this.label = label;
            this.target = target;
        }
    }

    public BindingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    // ---- controller hooks (called by ControllerHandler) ----

    public boolean isListening() {
        return listening >= 0;
    }

    public void onControllerButton(XInput.Button b) {
        if (listening < 0) return;
        NativePad.config.setBinding(b, rows.get(listening).target);
        listening = -1;
        layoutButtons();
    }

    public void scrollBy(int n) {
        int max = Math.max(0, rows.size() - visibleRows());
        int s = Math.max(0, Math.min(max, scroll + n));
        if (s != scroll) {
            scroll = s;
            layoutButtons();
        }
    }

    // ---- screen ----

    @Override
    public void initGui() {
        if (rows.isEmpty()) buildRows();
        scrollBy(0);
        layoutButtons();
    }

    private void buildRows() {
        rows.add(new Row("NativePad", null));
        for (Action a : Action.values()) {
            if (a != Action.NONE) rows.add(new Row(a.label, a.target()));
        }
        KeyBinding[] keys = Arrays.copyOf(mc.gameSettings.keyBindings, mc.gameSettings.keyBindings.length);
        Arrays.sort(keys); // by category, then name, like the Controls screen
        String category = null;
        for (KeyBinding kb : keys) {
            if (kb == NativePad.swapKey) continue; // already listed above
            if (!kb.getKeyCategory().equals(category)) {
                category = kb.getKeyCategory();
                rows.add(new Row(I18n.format(category), null));
            }
            rows.add(new Row(I18n.format(kb.getKeyDescription()), Action.key(kb.getKeyDescription())));
        }
    }

    private int visibleRows() {
        return Math.max(1, (height - TOP - 36) / ROW_H);
    }

    private void layoutButtons() {
        buttonList.clear();
        int x = width / 2 + 10;
        int vis = visibleRows();
        for (int i = 0; i < vis && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            if (r.target == null) continue;
            int y = TOP + i * ROW_H;
            int idx = scroll + i;
            String text = idx == listening
                    ? EnumChatFormatting.YELLOW + "> Press a controller button <"
                    : assignedText(r.target);
            buttonList.add(new GuiButton(idx * 2, x, y, 150, 20, text));
            buttonList.add(new GuiButton(idx * 2 + 1, x + 154, y, 40, 20, "Clear"));
        }
        buttonList.add(new GuiButton(DONE_ID, width / 2 - 100, height - 28, 200, 20, I18n.format("gui.done")));
    }

    private String assignedText(String target) {
        StringBuilder sb = new StringBuilder();
        for (XInput.Button b : XInput.Button.values()) {
            if (target.equals(NativePad.config.buttons.get(b))) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(buttonName(b));
            }
        }
        return sb.length() == 0 ? EnumChatFormatting.DARK_GRAY + "-" : sb.toString();
    }

    static String buttonName(XInput.Button b) {
        switch (b) {
            case BACK: return "Back";
            case START: return "Start";
            case DPAD_UP: return "D-pad Up";
            case DPAD_DOWN: return "D-pad Down";
            case DPAD_LEFT: return "D-pad Left";
            case DPAD_RIGHT: return "D-pad Right";
            default: return b.name();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE_ID) {
            mc.displayGuiScreen(parent);
            return;
        }
        int idx = button.id / 2;
        if (idx < 0 || idx >= rows.size()) return;
        if (button.id % 2 == 0) {
            listening = idx == listening ? -1 : idx;
            listenStart = System.currentTimeMillis();
        } else {
            listening = -1;
            String target = rows.get(idx).target;
            for (XInput.Button b : XInput.Button.values()) {
                if (target.equals(NativePad.config.buttons.get(b))) {
                    NativePad.config.setBinding(b, Action.NONE.target());
                }
            }
        }
        layoutButtons();
    }

    @Override
    public void updateScreen() {
        if (listening >= 0 && System.currentTimeMillis() - listenStart > LISTEN_TIMEOUT_MS) {
            listening = -1;
            layoutButtons();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollBy(wheel > 0 ? -3 : 3);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            if (listening >= 0) {
                listening = -1;
                layoutButtons();
            } else {
                mc.displayGuiScreen(parent);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Controller Bindings", width / 2, 10, 0xFFFFFF);
        drawCenteredString(fontRendererObj, EnumChatFormatting.GRAY
                + "Click an action, then press a controller button.  Right stick / mouse wheel scrolls.", width / 2, 24, 0xFFFFFF);
        int vis = visibleRows();
        for (int i = 0; i < vis && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int y = TOP + i * ROW_H;
            if (r.target == null) {
                drawCenteredString(fontRendererObj, EnumChatFormatting.BOLD + r.label, width / 2, y + 8, 0xFFDD55);
            } else {
                drawString(fontRendererObj, r.label, width / 2 - 190, y + 6, 0xFFFFFF);
            }
        }
        if (rows.size() > vis) {
            String pos = (scroll + 1) + "-" + Math.min(rows.size(), scroll + vis) + " of " + rows.size();
            drawString(fontRendererObj, EnumChatFormatting.DARK_GRAY + pos, 6, height - 22, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
