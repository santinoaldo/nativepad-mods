package com.voicehotbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.util.Set;

@Mod(modid = "voicehotbar", name = "VoiceHotbar", version = "1.6.0", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]", guiFactory = "com.voicehotbar.VoiceHotbar$Factory")
public class VoiceHotbar {

    static Logger LOG;
    static VoiceConfig config;
    private static VoiceListener listener;
    private static int configVersion;

    static KeyBinding pushToTalk;
    private KeyBinding toggleMute, toggleCaps;
    static KeyBinding dictateKey;
    private int previewTicks, namesTicks;
    private boolean openSettingsNextTick;
    private boolean announcedReady;

    static void bumpConfigVersion() {
        configVersion++;
        if (listener != null) listener.configVersion = configVersion;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        LOG = e.getModLog();
        config = new VoiceConfig(e.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        // Normal key bindings, so NativePad's Controller Bindings screen can put them on controller buttons.
        pushToTalk = new KeyBinding("Push to talk", Keyboard.KEY_V, "VoiceHotbar");
        toggleMute = new KeyBinding("Mute / unmute voice commands", Keyboard.KEY_NONE, "VoiceHotbar");
        ClientRegistry.registerKeyBinding(pushToTalk);
        ClientRegistry.registerKeyBinding(toggleMute);
        dictateKey = new KeyBinding("Dictate to chat (hold)", Keyboard.KEY_B, "VoiceHotbar");
        ClientRegistry.registerKeyBinding(dictateKey);
        toggleCaps = new KeyBinding("Toggle capitalization", Keyboard.KEY_NONE, "VoiceHotbar");
        ClientRegistry.registerKeyBinding(toggleCaps);

        listener = new VoiceListener(new File(Minecraft.getMinecraft().mcDataDir, "voicehotbar"));
        listener.configVersion = configVersion;
        listener.start(); // loads the model in the background so startup isn't slowed down

        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new VoiceCommand());
    }

    // Run on both frame and tick so a command applies on the very next frame after it's heard.
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) applyHeard();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase == TickEvent.Phase.START) {
            boolean ptt = pushToTalk.isKeyDown() && mc.currentScreen == null;
            if (ptt && !listener.pushToTalkDown && config.pushToTalk && mc.thePlayer != null) {
                // Feedback on press: confirms the mic is live, or says why it isn't.
                actionBar(listener.ready
                        ? EnumChatFormatting.GREEN + "Listening..."
                        : EnumChatFormatting.RED + "Voice: " + listener.status);
            }
            listener.pushToTalkDown = ptt;
            while (toggleCaps.isPressed()) {
                config.toggleCapitalize();
                actionBar("Capitalization: " + (config.capitalize ? EnumChatFormatting.GREEN + "on" : EnumChatFormatting.RED + "off"));
            }
            while (toggleMute.isPressed()) {
                listener.muted = !listener.muted;
                actionBar(listener.muted ? EnumChatFormatting.RED + "Voice commands muted" : EnumChatFormatting.GREEN + "Voice commands on");
            }
            updateAvailableItems(mc);
            applyHeard();
            handleDictation(mc);
            if (!announcedReady && listener.ready && mc.thePlayer != null) {
                announcedReady = true;
                chat(EnumChatFormatting.GREEN + "Voice commands ready. " + EnumChatFormatting.GRAY + "/voice for settings");
            }
            listener.ensureWhisper();
            if (mc.thePlayer != null) {
                String notice;
                while ((notice = listener.notices.poll()) != null) chat(EnumChatFormatting.GRAY + notice);
                if (++namesTicks >= 20) { // refresh Whisper's name list once a second
                    namesTicks = 0;
                    listener.playerNames = playerNames(mc);
                }
            }
        } else if (openSettingsNextTick) {
            openSettingsNextTick = false;
            mc.displayGuiScreen(new Settings(null));
        }
    }

    /** Hold-to-dictate: live preview while held, send to chat once the listener finishes the message. */
    private void handleDictation(Minecraft mc) {
        boolean down = config.dictation && dictateKey.isKeyDown() && mc.currentScreen == null && mc.thePlayer != null;
        if (down && !listener.dictateDown && !listener.ready) {
            actionBar(EnumChatFormatting.RED + "Voice: " + listener.status);
        }
        listener.dictateDown = down;

        String preview = listener.dictationPreview;
        if (preview != null && mc.thePlayer != null && ++previewTicks >= 4) {
            previewTicks = 0;
            if (config.fixNames) preview = Dictation.fixNames(preview, playerNames(mc), config.looseNames);
            if (config.punctuation && !preview.isEmpty()) preview = Dictation.applyPunctuation(preview);
            if (preview.length() > 70) preview = "..." + preview.substring(preview.length() - 67);
            actionBar(EnumChatFormatting.YELLOW + "● " + EnumChatFormatting.WHITE
                    + (listener.transcribing ? EnumChatFormatting.GRAY + "Writing it out..."
                    : preview.isEmpty() ? EnumChatFormatting.GRAY + "Listening..." : preview));
        }

        String[] result;
        while ((result = listener.dictated.poll()) != null) {
            if (mc.thePlayer == null) continue;
            boolean fromWhisper = result.length > 2 && VoiceListener.WHISPER.equals(result[2]);
            String raw;
            if (fromWhisper) {
                // Whisper already got the words, punctuation and capitals right; names still get checked.
                raw = config.fixNames ? Dictation.fixNames(result[0], playerNames(mc), config.looseNames) : result[0];
            } else {
                raw = config.fixNames ? Dictation.pickVersion(result[0], result[1], playerNames(mc), config.looseNames) : result[0];
            }
            String line = Dictation.format(raw, config.commandWord, config.cancelWord, config.capitalize, config.punctuation, config.autoPunctuation);
            if (line == null) {
                actionBar(EnumChatFormatting.GRAY + (raw.trim().isEmpty() ? "Didn't catch that" : "Message cancelled"));
                continue;
            }
            // Same path as pressing Enter in chat: history (up arrow), client commands, then the server.
            mc.ingameGUI.getChatGUI().addToSentMessages(line);
            if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, line) == 0) {
                mc.thePlayer.sendChatMessage(line);
            }
            actionBar("");
        }
    }

    /** Tells the listener which item words are worth listening for: only items that are in the hotbar. */
    private static void updateAvailableItems(Minecraft mc) {
        if (mc.thePlayer == null) {
            listener.available = null;
            return;
        }
        java.util.EnumSet<Target> s = java.util.EnumSet.noneOf(Target.class);
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack == null) continue;
            for (Target t : Target.values()) {
                if (t.fixedSlot() < 0 && t.matches(stack)) s.add(t);
            }
        }
        if (!s.equals(listener.available)) listener.available = s;
    }

    /** Everyone in the tab list, plus you. */
    private static java.util.Set<String> playerNames(Minecraft mc) {
        java.util.Set<String> names = new java.util.LinkedHashSet<String>();
        if (mc.thePlayer != null) names.add(mc.thePlayer.getName());
        if (mc.getNetHandler() != null) {
            for (net.minecraft.client.network.NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                String n = info.getGameProfile().getName();
                if (n != null && n.matches("[A-Za-z0-9_]{3,16}")) names.add(n); // real usernames only, not tab decorations
            }
        }
        return names;
    }

    private void applyHeard() {
        VoiceListener.Heard h;
        while ((h = listener.heard.poll()) != null) {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP p = mc.thePlayer;
            String word = h.word;
            Target t = h.target;
            if (p == null || mc.currentScreen != null) continue;
            int slot = t.pick(p.inventory);
            if (slot < 0) continue; // item not in the hotbar: not a command, ignore it silently
            p.inventory.currentItem = slot;
            if (config.feedback) actionBar(EnumChatFormatting.AQUA + "\"" + word + "\"");
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if ("voicehotbar".equals(e.modID)) config.applyFromGui();
    }

    private static void actionBar(String msg) {
        Minecraft.getMinecraft().ingameGUI.setRecordPlaying(msg, false);
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "[Voice] " + msg));
        }
    }

    /** /voice [status|mics|reload] */
    private final class VoiceCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return "voice";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/voice [status|mics|reload]";
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
            String sub = args.length == 0 ? "" : args[0].toLowerCase();
            if (sub.isEmpty()) {
                openSettingsNextTick = true;
            } else if (sub.equals("mics")) {
                chat("Microphones (put part of a name in the 'Microphone' setting):");
                for (String m : VoiceListener.microphones()) chat(" - " + m);
            } else if (sub.equals("reload")) {
                config.reload();
                chat("Config reloaded.");
            } else {
                chat(listener.status + (listener.muted ? " - muted" : "")
                        + (config.pushToTalk ? " - push to talk" : " - always on"));
                chat("Dictation: " + (config.whisperDictation ? "Accurate (Whisper) - " + listener.whisper.status : "Fast"));
            }
        }
    }

    public static final class Settings extends GuiConfig {
        public Settings(GuiScreen parent) {
            super(parent, config.guiElements(), "voicehotbar", false, false, "VoiceHotbar Settings",
                    "Changes apply when you press Done");
        }
    }

    public static final class Factory implements IModGuiFactory {
        @Override
        public void initialize(Minecraft mc) {
        }

        @Override
        public Class<? extends GuiScreen> mainConfigGuiClass() {
            return Settings.class;
        }

        @Override
        public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
            return null;
        }

        @Override
        public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
            return null;
        }
    }
}
