# Handicaps (Forge 1.8.9)

Make the game harder on purpose. Built for challenge Bedwars, works anywhere. Every effect only makes
things harder for you, so there's nothing for a server to flag.

## Install

Drop `Handicaps-1.5.0.jar` in your 1.8.9 Forge profile's `mods` folder. Open the settings with
`/handicaps` (or Mods → Handicaps → Config). Everything starts off: click the ON/OFF button next to a handicap to switch it, or click its name for its settings.

## Handicaps

| Handicap | What it does | Settings |
|---|---|---|
| **Short sight** | Everything beyond a few blocks fades into fog: blocks, players, sky. Nametags of players beyond the fog are hidden too. | distance (4 blocks), fade length, fog color (black, gray, white, dark red) |
| **Near-sighted** | Close things are sharp, far things blurry. | sharp distance, how fast the blur builds, strength |
| **Grayscale** | No color at all: world, HUD, tab list, scoreboard, inventories and shops. | how gray |
| **Tunnel vision** | A dark ring closes in from the screen edges (the HUD stays readable on top). | visible area, edge softness |
| **Deaf** | No game sounds or music (menu clicks still play). | |
| **Drunk** | Swaying, tilting camera, wobbly double vision, and your aim slowly drifts. | strength |
| **Paranoia** | Fake Bedwars sounds from behind you: footsteps sneaking up (wool, stone, wood), someone bridging toward you or breaking wool, fall damage, bow shots, TNT fuses, fireballs, ender pearls, eating and drinking. They come from a real direction, only you hear them. | how often (about every 25 s), volume, sound set (Bedwars; more game modes later), and an on/off switch **per sound** (14 of them, including a fight between two players) with a **Test** button next to each that plays it right away |
| **Hallucinations** | Fake things only you can see: a **fireball** flies at you from 18-45 blocks away and "explodes" 2-3 blocks in front of you (sound and particles, no damage), or a **player from the tab list** (their skin and name) sprints at you with a sword (about half of them sprint-jumping, with real jump physics) along real walkable ground (no running on air or through walls) and vanishes right before reaching you; if there is no walkable path, you get a fake fireball instead. An **invisible player** leaves footprints and footsteps running right past you and on for another 8-15 blocks, with nobody there. An **arrow** is shot from far away and whizzes past your head. A fake **BED DESTROYED!** alert (title, wither sound, chat line) while your bed is fine. A **bridger** (a tab-list player, crouched, holding their team's wool) speed-bridges toward you across a real gap and vanishes 3 blocks away with all their blocks; the blocks are only drawn on your screen (never added to the world, nothing sent to the server, you can't stand on them). They can't hurt you and your clicks pass through them. | how often (about every 45 s), each one on/off with a **Test** button |

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

Needs JDK 17: `gradlew build` → `build/libs/handicaps-1.4.2.jar`. MIT license.
