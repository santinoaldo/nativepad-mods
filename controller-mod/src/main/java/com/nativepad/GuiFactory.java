package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.Set;

/** Hooks the settings screen into Forge's Mods menu (Mods > NativePad > Config). */
public final class GuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft mc) {
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return SettingsScreen.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    public static final class SettingsScreen extends GuiConfig {
        public SettingsScreen(GuiScreen parent) {
            super(parent, NativePad.config.guiElements(), "nativepad", false, false, "NativePad Settings",
                    "Changes apply when you press Done");
        }
    }

    /** "Controller Bindings" row in the settings list; opens the press-to-bind screen. */
    public static final class BindingsEntry extends GuiConfigEntries.CategoryEntry {
        public BindingsEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement element) {
            super(owningScreen, owningEntryList, element);
        }

        @Override
        protected GuiScreen buildChildScreen() {
            return new BindingsScreen(this.owningScreen);
        }
    }

    /** "Move overlay..." row: opens a screen where you drag the controller overlay with the mouse. */
    public static final class MoveOverlayEntry extends GuiConfigEntries.CategoryEntry {
        public MoveOverlayEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement element) {
            super(owningScreen, owningEntryList, element);
        }

        @Override
        protected GuiScreen buildChildScreen() {
            return new OverlayMoveScreen(this.owningScreen);
        }
    }

    /**
     * Forge's number slider, always showing two decimals (so 0.08 isn't rounded to 0), plus a small box at
     * the right where you can type an exact number: click it, type, then Enter (or click elsewhere).
     */
    public static final class DecimalSliderEntry extends GuiConfigEntries.NumberSliderEntry {
        private static final int BOX = 46;
        private final net.minecraft.client.gui.GuiTextField box;

        public DecimalSliderEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement element) {
            super(owningScreen, owningEntryList, element);
            GuiSlider s = (GuiSlider) this.btnValue;
            s.showDecimal = true;
            s.precision = 2;
            s.setValue(Double.parseDouble(element.get().toString()));
            s.updateSlider();
            box = new net.minecraft.client.gui.GuiTextField(0, Minecraft.getMinecraft().fontRendererObj, 0, 0, BOX - 4, 16);
            box.setMaxStringLength(12);
            box.setText(format(s.getValue()));
        }

        private static String format(double v) {
            String t = String.format(java.util.Locale.ROOT, "%.3f", v);
            while (t.contains(".") && (t.endsWith("0") || t.endsWith("."))) t = t.substring(0, t.length() - 1);
            return t;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY,
                              boolean isSelected) {
            // Draw the slider a bit narrower, leaving room for the number box on its right.
            int full = this.owningEntryList.controlWidth;
            this.owningEntryList.controlWidth = full - BOX;
            try {
                super.drawEntry(slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY, isSelected);
            } finally {
                this.owningEntryList.controlWidth = full;
            }
            box.xPosition = this.owningEntryList.controlX + full - BOX + 3;
            box.yPosition = y + 1;
            if (!box.isFocused()) { // follow the slider while you drag it
                String t = format(((GuiSlider) this.btnValue).getValue());
                if (!t.equals(box.getText())) box.setText(t);
            }
            box.drawTextBox();
        }

        @Override
        public void mouseClicked(int x, int y, int mouseEvent) {
            super.mouseClicked(x, y, mouseEvent);
            boolean wasFocused = box.isFocused();
            box.mouseClicked(x, y, mouseEvent);
            if (wasFocused && !box.isFocused()) apply(); // clicked away: keep what was typed
        }

        @Override
        public void keyTyped(char c, int key) {
            super.keyTyped(c, key);
            if (!box.isFocused()) return;
            if (key == org.lwjgl.input.Keyboard.KEY_RETURN || key == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
                apply();
                box.setFocused(false);
                return;
            }
            // Numbers only (plus editing keys).
            if (Character.isDigit(c) || c == '.' || c == '-' || c == 0 || key == org.lwjgl.input.Keyboard.KEY_BACK
                    || key == org.lwjgl.input.Keyboard.KEY_DELETE) {
                box.textboxKeyTyped(c, key);
                apply();
            }
        }

        /** Puts the typed number on the slider (kept inside the slider's range). */
        private void apply() {
            try {
                GuiSlider s = (GuiSlider) this.btnValue;
                double v = Double.parseDouble(box.getText().trim());
                v = Math.max(s.minValue, Math.min(s.maxValue, v));
                s.setValue(v);
                s.updateSlider();
            } catch (NumberFormatException ignored) {
                // half-typed ("", "-", "."): wait for more
            }
        }

        @Override
        public void updateCursorCounter() {
            super.updateCursorCounter();
            box.updateCursorCounter();
        }
    }
}
