# VoiceHotbar (Forge 1.8.9)

Say an item and your hotbar switches to it: "**blocks!**", "**sword!**", "**pearl!**".
Speech recognition runs offline on your PC (Vosk). Nothing is sent anywhere. Windows only.

## Install

Drop `VoiceHotbar-1.6.4.jar` in your 1.8.9 Forge profile's `mods` folder (alongside NativePad if you use it).
The first launch unpacks the speech model (~70 MB) into a `voicehotbar` folder in your profile, which takes a few seconds.
When it's ready you'll see `[Voice] Voice commands ready` in chat.

## Words

| Say | Selects |
|---|---|
| blocks / block / wool | first slot with blocks |
| sword | sword |
| pickaxe / pick | pickaxe |
| axe, shears, bow, pearl, rod, egg, water, potion | that item |
| apple / gap | golden apple |
| fire / fireball | fire charge |

Say the same word again to cycle to the next matching slot (e.g. a second stack of blocks).
One command per breath: once a word fires, nothing else fires until you pause, so the tail of a word can't trigger a second command ("gapple" no longer also selects wool, "pickaxe" no longer also selects the axe). Pause briefly between two commands.
Item words are only listened for while that item is in your hotbar, so "bow" does nothing (and can't be misheard) when you have no bow.
Add or change words in the settings: each line is `word=TARGET` (English) or `pt:word=TARGET` (Portuguese).

Slot numbers are off by default. To add them back, add lines like `pt:dois=SLOT_2` (Portuguese numbers um…nove have tuned spellings so they don't get mistaken for items) or `one=SLOT_1`.

## Dictate to chat

Hold **Dictate to chat** (default **B**; change it with the **Dictate to chat key** button in `/voice`), talk, release. What you said is sent to chat about 0.6 s later,
without opening chat. A live preview shows above the hotbar while you talk.

**Dictation engine** (setting):
- **Accurate (Whisper)**, the default: much better word accuracy (no/know, there/their...), real commas, periods, question marks and capitals, and it's told the player names in tab. Runs locally (whisper.cpp, bundled) in a small background process. The first launch downloads its model once (148 MB, from the official whisper.cpp model page, checksum-verified); until then dictation uses the fast engine. Uses ~255 MB of RAM while the game runs.
- **Fast**: the original engine. Sends in ~0.3 s but makes more mistakes.

Hotbar words always use the fast engine, so item switching stays instant.

| Say | Sends |
|---|---|
| "good game everyone" | `Good game everyone` |
| "command shout hello world" | `/shout hello world` |
| "command party invite steve" | `/party invite steve` |
| "slash shout hello world" ("slash" works the same as "command") | `/shout hello world` |
| "... cancel" (as the last word) | nothing |

- Hotbar words are paused while you dictate, so "bring blocks to mid" won't switch to blocks.
- **Player names autocorrect** from the tab list, by spelling or by sound: "hello super girly gamer" sends `Hello supergirlygamer`, "my name is cindy know all though" sends `My name is Santinoaldo`. Digit words count too ("dark knight ninety nine" -> `DarkKnight99`).
- **Spoken punctuation:** say "exclamation point" `!`, "question mark" `?`, "period" `.`, "comma" `,`, "colon" `:`, "dot dot dot" `...`. Say it more than once to stack ("nice exclamation point exclamation point" -> `Nice!!`). Turn it off with "Spoken punctuation" in settings.
- **Auto punctuation** (off by default): ends messages with `.`, or `?` when they start like a question ("where is red base" -> `Where is red base?`). Commands are left alone.
- **Name matching: Strict / Loose.** Strict only turns clear matches into names. Loose also catches names the recognizer hears as other words ("sentinel" -> `Santinoaldo`) but sometimes turns phrases like "send in" into a name.
- Rare words may come out wrong.
  Check the preview, and say "cancel" at the end if it misheard you.
- Sent messages go into chat history, so the up arrow in chat brings them back.
- **Controller:** bind **Dictate to chat (hold)** to a button in NativePad's Controller Bindings.
- **Capitalization** (first letter and "i" -> "I") can be turned off in the settings, or flipped any time with the **Toggle capitalization** keybind (unbound by default; bind it in Controls or on a controller button via NativePad).
- Change the command/cancel words or turn it off in the settings.

## Settings (`/voice`, or Mods → VoiceHotbar → Config)

- **Listen mode.** Always on, or Push to talk (only listens while the key is held). **Push to talk key** (below it): click, then press any key or mouse button. Default V.
  **Controller:** in NativePad, open `/nativepad` → **Controller Bindings...** → find **Push to talk** → press a paddle.
- **Instant.** Acts mid-word for the fastest switch. Turn it off if it ever picks the wrong item.
- **Show what was heard.** Shows the word above the hotbar.
- **Microphone.** Click to cycle through your mics. "Default" = the Windows default input. Pick your real mic if Windows defaults to a virtual one (Voicemod, Steam, Oculus).
- **Mute / unmute** has its own keybind (unbound by default; can also go on a controller button).

`/voice status` shows whether it's listening.

## Tips

- With **always listening**, keep words distinct from what you say to teammates. Only the listed words can trigger.
- If game sound leaks into your mic (speakers), use push to talk or headphones.

## Note on servers

Saying a slot number (if you add them back) is the same as pressing a number key. Item words choose the slot for you based on
what's in it, which is close to "auto tool" style mods. Some servers (including Hypixel) don't allow those,
so use at your own risk there. The number words are the safe option.

Settings save the moment you change them: no need to press **Done** (Esc or the back arrow keeps your changes too).

## Build

Needs JDK 17: `gradlew build` → `build/libs/voicehotbar-1.6.4.jar`.
Speech recognition by [Vosk](https://alphacephei.com/vosk/) (Apache 2.0), small English model, and [whisper.cpp](https://github.com/ggml-org/whisper.cpp) v1.8.2 (MIT) with the base.en model.
