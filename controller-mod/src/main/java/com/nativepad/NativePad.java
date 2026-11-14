package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
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

@Mod(modid = "nativepad", name = "NativePad", version = "1.3.1", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]", guiFactory = "com.nativepad.GuiFactory")
public class NativePad {

    public static Logger LOG;
    public static PadConfig config;
    public static KeyBinding swapKey, rumbleKey;
    static Feedback feedback;
    private static ControllerHandler handler;
    private static boolean openSettingsNextTick;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        LOG = e.getModLog();
        config = new PadConfig(e.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        swapKey = new KeyBinding("Swap to last hotbar slot", Keyboard.KEY_NONE, "NativePad");
        ClientRegistry.registerKeyBinding(swapKey);
        rumbleKey = new KeyBinding("Toggle rumble", Keyboard.KEY_NONE, "NativePad");
        ClientRegistry.registerKeyBinding(rumbleKey);

        handler = new ControllerHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        feedback = new Feedback(handler);
        MinecraftForge.EVENT_BUS.register(feedback);
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new PadCommand());

        LOG.info(XInput.isAvailable() ? "XInput loaded: " + XInput.libraryName() : "XInput not available");
    }

    /** Settings screen "Done" pressed: save and apply. */
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if ("nativepad".equals(e.modID)) config.applyFromGui();
    }

    /** Opening a screen from a chat command must wait a tick, or closing the chat would close it again. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && openSettingsNextTick) {
            openSettingsNextTick = false;
            Minecraft.getMinecraft().displayGuiScreen(new GuiFactory.SettingsScreen(null));
        }
    }

    /** /nativepad [reload|status] */
    private static final class PadCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return "nativepad";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/nativepad [reload|status]";
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
            if (args.length == 0) {
                openSettingsNextTick = true;
            } else if (args[0].equalsIgnoreCase("reload")) {
                config.reload();
                handler.chat("Config reloaded.");
            } else {
                handler.chat(handler.status());
            }
        }
    }
}
