# NativePad & VoiceHotbar

Two quality-of-life mods for **Minecraft 1.8.9 (Forge)**, made for playing on a controller.

| Mod | What it does |
|---|---|
| [**NativePad**](controller-mod/) | Native Xbox-style controller support: no Steam Input needed. Keyboard/mouse-identical input (safe on servers with anticheat), press-to-bind controls for any keybind, last-slot swap, D-pad and stick snap-to-slot in inventories, rumble. |
| [**VoiceHotbar**](voice-mod/) | Say an item ("blocks!", "sword!") to switch to it, or hold a key and talk to send a chat message or `/command`. Speech recognition runs offline on your PC. |

| [**Handicaps**](handicaps-mod/) | Make the game harder on purpose: short-sight fog, near-sighted blur, grayscale, tunnel vision, deafness, drunkenness, paranoia, hallucinations. |

They work on their own or together (NativePad can put VoiceHotbar's push-to-talk and dictation keys on controller buttons).

Windows only. Each folder has its own README with setup and settings.

## Building

Needs JDK 17. In either folder: `gradlew build` → `build/libs/`.

## License

MIT, see [LICENSE](LICENSE). VoiceHotbar bundles [Vosk](https://alphacephei.com/vosk/) (Apache 2.0) and
[whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT); their licenses are included next to them.

Much of this code was written with the help of an AI assistant (Claude).
