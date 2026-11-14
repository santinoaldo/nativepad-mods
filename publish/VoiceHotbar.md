# Modrinth listing: VoiceHotbar

- **Name:** VoiceHotbar
- **URL slug:** voicehotbar
- **Summary:** Say an item to switch to it, or hold a key and talk to send a chat message or /command. Offline speech recognition on your PC.
- **Project type:** Mod · **Loader:** Forge · **Game version:** 1.8.9
- **Environment:** Client: required · Server: unsupported
- **Categories:** Utility, Social
- **License:** MIT
- **Source:** https://github.com/santinoaldo/nativepad-mods
- **Issues:** https://github.com/santinoaldo/nativepad-mods/issues
- **Disclosures:** Contains AI-generated content: **yes**
- **Version:** 1.6.1 (release) · file `VoiceHotbar-1.6.1.jar`

---

## Description

**Talk to switch items and to chat.** Say "blocks!" and your hotbar jumps to your blocks. Hold a key, say "command shout, red team has no bed!", let go, and it's sent as `/shout red team has no bed!`, without ever opening chat.

### Voice item switching
- Say an item (*blocks, sword, pickaxe, bow, pearl, apple, fireball, water*…) to select it. Say it again to cycle to the next stack.
- It only listens for items that are in your hotbar, and reacts while you're still saying the word.
- Always-on, or push-to-talk (bind it to a controller button with **NativePad**).

### Dictate to chat
- Hold the dictate key (default **B**), talk, release. The message is sent about half a second later.
- Uses Whisper for accurate words with real punctuation and capitals.
- Start with "command" or "slash" to send a command. End with "cancel" to throw it away.
- Player names from the tab list are recognized and spelled correctly.
- Optional spoken punctuation ("exclamation point"), auto punctuation, and capitalization toggles.

### Privacy: everything runs on your PC
- **Uses your microphone.** Pick which one in the settings.
- **Speech never leaves your computer.** Recognition runs locally ([Vosk](https://alphacephei.com/vosk/) and [whisper.cpp](https://github.com/ggml-org/whisper.cpp)). Nothing is uploaded.
- **One-time download.** On first launch it downloads Whisper's English model (148 MB) from the official whisper.cpp model page on Hugging Face, checksum-verified. That is the only network access.
- The accurate dictation runs as a small bundled background program (whisper.cpp server, ~255 MB RAM) that only listens on your own computer and closes with the game.

### Requirements
- Windows (64-bit), Minecraft 1.8.9 with Forge, a microphone.
- About 300 MB of disk space for the speech models.

### Notes
- **Server rules:** item words pick the slot for you based on what's in it. Some servers (including Hypixel) may treat that like "auto tool" mods, so use at your own risk there.
- Bundles Vosk (Apache 2.0) and whisper.cpp (MIT); their licenses are included in the jar.
- Much of this mod was written with the help of an AI assistant.

---

## Changelog (1.6.1)

First public release.
- Voice item switching (only items in your hotbar, one command per breath).
- Dictate to chat with Whisper: accurate words, punctuation, capitals.
- "command"/"slash" voice commands, "cancel" to discard.
- Player-name autocorrect from the tab list (Strict/Loose).
- Spoken punctuation, auto punctuation and capitalization toggles.
- Push-to-talk and dictation keys, microphone picker, in-game settings.
