package com.voicehotbar;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.IConfigElement;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** "Press a key for ..." screen, opened from a row in the settings list. */
public final class KeyCaptureScreen extends GuiScreen {

    private final GuiScreen parent;
    private final KeyBinding key;
    private final String title;

    KeyCaptureScreen(GuiScreen parent, KeyBinding key, String title) {
        this.parent = parent;
        this.key = key;
        this.title = title;
    }

    static String keyName(KeyBinding kb) {
        return GameSettings.getKeyDisplayString(kb.getKeyCode());
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 100, height / 2 + 30, 98, 20, "Unbind"));
        buttonList.add(new GuiButton(2, width / 2 + 2, height / 2 + 30, 98, 20, "Cancel"));
    }

    private void set(int keyCode) {
        mc.gameSettings.setOptionKeyBinding(key, keyCode); // also saves options.txt
        KeyBinding.resetKeyBindingArrayAndHash();
        close();
    }

    private void close() {
        if (parent instanceof GuiConfig) ((GuiConfig) parent).needsRefresh = true; // refresh the button label
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) set(Keyboard.KEY_NONE);
        else close();
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) close();
        else if (code != Keyboard.KEY_NONE) set(code);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        // Left click still works the buttons; other mouse buttons (side buttons, middle) become the key.
        if (button == 0) super.mouseClicked(x, y, button);
        else set(button - 100);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 40, 0xFFFFFF);
        drawCenteredString(fontRendererObj, EnumChatFormatting.YELLOW + "Press a key or mouse button (right, middle, side)",
                width / 2, height / 2 - 18, 0xFFFFFF);
        drawCenteredString(fontRendererObj, EnumChatFormatting.GRAY + "Current: " + keyName(key)
                + "   |   Controller: use NativePad > Controller Bindings", width / 2, height / 2 + 2, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Settings-list row: a button showing the current key that opens the capture screen. */
    private abstract static class KeyEntry extends GuiConfigEntries.CategoryEntry {
        KeyEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement element) {
            super(owningScreen, owningEntryList, element);
            btnSelectCategory.displayString = title() + ": " + keyName(key());
        }

        abstract KeyBinding key();

        abstract String title();

        @Override
        protected GuiScreen buildChildScreen() {
            return new KeyCaptureScreen(this.owningScreen, key(), title());
        }
    }

    public static final class PushToTalkEntry extends KeyEntry {
        public PushToTalkEntry(GuiConfig s, GuiConfigEntries l, IConfigElement e) {
            super(s, l, e);
        }

        @Override
        KeyBinding key() {
            return VoiceHotbar.pushToTalk;
        }

        @Override
        String title() {
            return "Push to talk key";
        }
    }

    public static final class DictateEntry extends KeyEntry {
        public DictateEntry(GuiConfig s, GuiConfigEntries l, IConfigElement e) {
            super(s, l, e);
        }

        @Override
        KeyBinding key() {
            return VoiceHotbar.dictateKey;
        }

        @Override
        String title() {
            return "Dictate to chat key (hold)";
        }
    }
}
