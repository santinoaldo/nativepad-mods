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

    /** Forge's number slider, but always showing two decimals so small values like 0.08 aren't rounded. */
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
