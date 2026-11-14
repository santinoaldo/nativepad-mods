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
