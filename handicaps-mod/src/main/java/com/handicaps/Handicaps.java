package com.handicaps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

@Mod(modid = "handicaps", name = "Handicaps", version = "1.4.1", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]", guiFactory = "com.handicaps.GuiFactory")
public class Handicaps {

    static Logger LOG;
    static HConfig config;
    private KeyBinding toggleAll, openSettings;
    private boolean openSettingsNextTick;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        LOG = e.getModLog();
        config = new HConfig(e.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        toggleAll = new KeyBinding("Toggle all handicaps", Keyboard.KEY_NONE, "Handicaps");
        openSettings = new KeyBinding("Open Handicaps settings", Keyboard.KEY_NONE, "Handicaps");
        ClientRegistry.registerKeyBinding(toggleAll);
        ClientRegistry.registerKeyBinding(openSettings);
        MinecraftForge.EVENT_BUS.register(new Effects());
        MinecraftForge.EVENT_BUS.register(new Paranoia());
        MinecraftForge.EVENT_BUS.register(new Hallucinations());
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new HandicapsCommand());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        while (toggleAll.isPressed()) {
            config.paused = !config.paused;
            if (mc.ingameGUI != null) {
                mc.ingameGUI.setRecordPlaying("Handicaps: " + (config.paused
                        ? EnumChatFormatting.RED + "off" : EnumChatFormatting.GREEN + "on"), false);
            }
        }
        while (openSettings.isPressed()) openSettingsNextTick = true;
        if (openSettingsNextTick) {
            openSettingsNextTick = false;
            mc.displayGuiScreen(new GuiFactory.SettingsScreen(null));
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if ("handicaps".equals(e.modID)) config.applyFromGui();
    }

    /** /handicaps opens settings; /handicaps reload re-reads the file. */
    private final class HandicapsCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return "handicaps";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/handicaps [reload]";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) config.reload();
            else openSettingsNextTick = true;
        }
    }
}
