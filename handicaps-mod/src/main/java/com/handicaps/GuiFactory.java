package com.handicaps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.Set;

/** Settings screen: Mods > Handicaps > Config, or /handicaps. */
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
            super(parent, Handicaps.config.guiElements(), "handicaps", false, false, "Handicaps",
                    "Changes apply when you press Done");
        }
    }

    /**
     * An on/off row with a "Test" button on the right that plays that Paranoia sound.
     * (Forge's own on/off row can't be extended, so this re-implements it.)
     */
    public static final class SoundToggleEntry extends GuiConfigEntries.ButtonEntry {
        private static final int TEST_WIDTH = 44;
        private final boolean beforeValue;
        private boolean currentValue;
        private final net.minecraftforge.fml.client.config.GuiButtonExt test;
        private final ParanoiaSound sound;

        public SoundToggleEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement element) {
            super(owningScreen, owningEntryList, element);
            beforeValue = Boolean.valueOf(element.get().toString());
            currentValue = beforeValue;
            sound = ParanoiaSound.byKey(element.getName());
            test = new net.minecraftforge.fml.client.config.GuiButtonExt(0, 0, 0, TEST_WIDTH, 18, "Test");
            updateValueButtonText();
        }

        @Override
        public void updateValueButtonText() {
            btnValue.displayString = net.minecraft.client.resources.I18n.format(String.valueOf(currentValue));
            btnValue.packedFGColour = currentValue
                    ? net.minecraftforge.fml.client.config.GuiUtils.getColorCode('2', true)
                    : net.minecraftforge.fml.client.config.GuiUtils.getColorCode('4', true);
        }

        @Override
        public void valueButtonPressed(int slotIndex) {
            if (enabled()) currentValue = !currentValue;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected) {
            // Draw the toggle narrower to leave room for the Test button.
            int full = owningEntryList.controlWidth;
            owningEntryList.controlWidth = full - TEST_WIDTH - 4;
            try {
                super.drawEntry(slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY, isSelected);
            } finally {
                owningEntryList.controlWidth = full;
            }
            test.xPosition = owningEntryList.controlX + full - TEST_WIDTH;
            test.yPosition = y;
            test.drawButton(mc, mouseX, mouseY);
        }

        @Override
        public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            boolean hallucination = configElement.getName().startsWith("fake");
            if ((sound != null || hallucination) && test.mousePressed(mc, x, y)) {
                test.playPressSound(mc.getSoundHandler());
                if (sound != null && Paranoia.instance != null) Paranoia.instance.test(sound);
                if (hallucination && Hallucinations.instance != null) Hallucinations.instance.test(configElement.getName());
                return true;
            }
            return super.mousePressed(index, x, y, mouseEvent, relativeX, relativeY);
        }

        @Override
        public boolean isDefault() {
            return currentValue == Boolean.valueOf(configElement.getDefault().toString());
        }

        @Override
        public void setToDefault() {
            if (enabled()) {
                currentValue = Boolean.valueOf(configElement.getDefault().toString());
                updateValueButtonText();
            }
        }

        @Override
        public boolean isChanged() {
            return currentValue != beforeValue;
        }

        @Override
        public void undoChanges() {
            if (enabled()) {
                currentValue = beforeValue;
                updateValueButtonText();
            }
        }

        @Override
        public boolean saveConfigElement() {
            if (enabled() && isChanged()) {
                configElement.set(currentValue);
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public Boolean getCurrentValue() {
            return currentValue;
        }

        @Override
        public Boolean[] getCurrentValues() {
            return new Boolean[]{currentValue};
        }
    }

    /**
     * A main-list row: the category button (opens its settings) plus an ON/OFF button right next to it,
     * so a handicap can be switched without opening it.
     */
    public static final class ToggleCategoryEntry extends GuiConfigEntries.CategoryEntry {
        private static final int TOGGLE_WIDTH = 50, GAP = 4;
        private final net.minecraftforge.fml.client.config.GuiButtonExt toggle;

        public ToggleCategoryEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement element) {
            super(owningScreen, owningEntryList, element);
            // Same total width as before, so the undo/reset buttons don't move.
            btnSelectCategory.width -= TOGGLE_WIDTH + GAP;
            toggle = new net.minecraftforge.fml.client.config.GuiButtonExt(0, 0, 0, TOGGLE_WIDTH, 18, "");
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected) {
            super.drawEntry(slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY, isSelected);
            boolean on = Handicaps.config.isOn(configElement.getName());
            toggle.displayString = on ? "ON" : "OFF";
            toggle.packedFGColour = on
                    ? net.minecraftforge.fml.client.config.GuiUtils.getColorCode('a', true)
                    : net.minecraftforge.fml.client.config.GuiUtils.getColorCode('c', true);
            toggle.xPosition = btnSelectCategory.xPosition + btnSelectCategory.width + GAP;
            toggle.yPosition = y;
            toggle.drawButton(mc, mouseX, mouseY);
        }

        @Override
        public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            if (toggle.mousePressed(mc, x, y)) {
                toggle.playPressSound(mc.getSoundHandler());
                String cat = configElement.getName();
                Handicaps.config.setOn(cat, !Handicaps.config.isOn(cat)); // saved and applied right away
                childScreen = buildChildScreen(); // so the settings inside show the new value
                return true;
            }
            return super.mousePressed(index, x, y, mouseEvent, relativeX, relativeY);
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
