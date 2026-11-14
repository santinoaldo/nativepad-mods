# Modrinth listing: NativePad

- **Name:** NativePad
- **URL slug:** nativepad
- **Summary:** Native Xbox-style controller support for 1.8.9 with no Steam Input: press-to-bind controls, snap-to-slot inventories, last-slot swap, rumble.
- **Project type:** Mod · **Loader:** Forge · **Game version:** 1.8.9
- **Environment:** Client: required · Server: unsupported
- **Categories:** Utility
- **License:** MIT
- **Source:** https://github.com/santinoaldo/nativepad-mods
- **Issues:** https://github.com/santinoaldo/nativepad-mods/issues
- **Disclosures:** Contains AI-generated content: **yes**
- **Version:** 1.3.1 (release) · file `NativePad-1.3.1.jar`

---

## Description

**Play Minecraft 1.8.9 with a controller, natively.** NativePad reads Xbox-style (XInput) controllers directly, so you don't need Steam Input or any other mapping program.

### Built to be safe on servers
Everything NativePad sends looks exactly like a keyboard and mouse:
- Movement snaps to the 8 directions WASD can make (no "half-pressed" movement that anticheats flag).
- Aim turns in the same steps a real mouse does.
- Attack/use on the triggers are real mouse clicks, so CPS counters and other mods see them.

There is **no** aim assist, auto-clicking or macros.

### Features
- **Press-to-bind controls.** Open `/nativepad` → *Controller Bindings*, click an action, press a controller button. Every Minecraft keybind and every other mod's keybind can go on a button.
- **Last-slot swap.** One button flips between your current and previous hotbar slot (sword ↔ blocks).
- **Snap-to-slot menus.** In inventories, chests and shops the D-pad *and* left stick hop slot to slot. Other menus get a smooth cursor that isn't tied to frame rate.
- **Rumble.** Thump on damage, heartbeat at low health, long rumble on chat alerts (bed destroyed, trap). Every part can be toggled, plus a *Toggle rumble* keybind.
- **Tunable aim.** Turn speed, response curve and deadzones, all from an in-game settings screen with sliders.
- **Battery indicator** for controllers that report their battery to Windows.

### Requirements
- Windows, Minecraft 1.8.9 with Forge.
- An Xbox-style controller in XInput / PC mode.
- Turn off Steam Input for the game (or launch without Steam), otherwise you'll get double input.

### Notes
- Server rules vary. NativePad only sends normal keyboard/mouse input, but always check the rules of the server you play on.
- Much of this mod was written with the help of an AI assistant.

Works great with **VoiceHotbar** (voice item switching and voice-to-chat): its keys can be bound to controller buttons.

---

## Changelog (1.3.1)

First public release.
- Native XInput controller support with keyboard/mouse-identical input.
- Press-to-bind Controller Bindings screen for any keybind.
- Last-slot swap, hotbar left/right.
- D-pad and left-stick snap-to-slot in inventories, chests and shops.
- Rumble on damage, low health and chat alerts (toggleable).
- Triggers send real mouse clicks (CPS counters work).
- Battery indicator (for controllers that report it).
