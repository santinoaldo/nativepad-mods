# Handicaps (Forge 1.8.9)

Make the game harder on purpose. Built for challenge Bedwars, works anywhere. Every effect only makes
things harder for you, so there's nothing for a server to flag.

## Install

Drop `Handicaps-1.0.1.jar` in your 1.8.9 Forge profile's `mods` folder. Open the settings with
`/handicaps` (or Mods → Handicaps → Config). Everything starts off; turn on what you want.

## Handicaps

| Handicap | What it does | Settings |
|---|---|---|
| **Short sight** | Everything beyond a few blocks fades into fog: blocks, players, sky. Nametags of players beyond the fog are hidden too. | distance (4 blocks), fade length, fog color (black, gray, white, dark red) |
| **Near-sighted** | Close things are sharp, far things blurry. | sharp distance, how fast the blur builds, strength |
| **Grayscale** | No color at all: world, HUD, tab list, scoreboard, inventories and shops. | how gray |
| **Tunnel vision** | A dark ring closes in from the screen edges (the HUD stays readable on top). | visible area, edge softness |
| **Deaf** | No game sounds or music (menu clicks still play). | |
| **Drunk** | Swaying, tilting camera, wobbly double vision, and your aim slowly drifts. | strength |

They stack: try short sight + grayscale + deaf.

## Keys (Options → Controls → Handicaps, unbound by default)

- **Toggle all handicaps**: pause/resume everything instantly (shows on/off above the hotbar).
- **Open Handicaps settings**.

Both can go on a controller button with NativePad's Controller Bindings.

## Notes

- Screen effects need shader support (any GPU from the last decade). If it's missing you'll get a chat message; Deaf still works.
- Drunk moves your real aim in mouse-sized steps, like a shaky hand on a mouse.
- Much of this mod was written with the help of an AI assistant.

## Build

Needs JDK 17: `gradlew build` → `build/libs/handicaps-1.0.1.jar`. MIT license.
