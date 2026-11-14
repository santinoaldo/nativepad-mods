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

    /** Forge's number slider, always showing two decimals. */
    public static final class DecimalSliderEntry extends GuiConfigEntries.NumberSliderEntry {
        public DecimalSliderEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement element) {
            super(owningScreen, owningEntryList, element);
            GuiSlider s = (GuiSlider) this.btnValue;
            s.showDecimal = true;
            s.precision = 2;
            s.setValue(Double.parseDouble(element.get().toString()));
            s.updateSlider();
        }
    }
}
