# Handicaps (Forge 1.8.9)

Make the game harder on purpose. Built for challenge Bedwars, works anywhere. Every effect only makes
things harder for you, so there's nothing for a server to flag.

## Install

Drop `Handicaps-1.6.1.jar` in your 1.8.9 Forge profile's `mods` folder. Open the settings with
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
| **Hallucinations** | Fake things only you can see: a **fireball** flies at you from 18-45 blocks away, leading its shot if you're moving (it aims where you'll be when it arrives), and "explodes" (sound and particles, no damage): usually 2-4 blocks short of you, sometimes a near miss to the side, sometimes over your head landing behind you, sometimes right at your feet, or a **player from the tab list** (their skin and name) sprints at you with a sword (about half of them sprint-jumping, with real jump physics) along real ground, jumping up 1-block steps and stairs, dropping off ledges and parkour-jumping holes up to 3 blocks wide (no running on air or through walls) and vanishes right before reaching you; if there is no walkable path, you get a fake fireball instead. An **invisible player** leaves footprints and footsteps running right past you and on for another 8-15 blocks, with nobody there. An **arrow** is shot from far away and whizzes past your head on a real falling arc (vanilla arrow speed, drag and gravity). A fake **BED DESTROYED!** alert (title, wither sound, chat line naming an enemy in their team color) while your bed is fine. A **bridger** (a tab-list player holding their team's wool; sometimes slow-bridging crouched at the real crouch speed of ~1.3 blocks/s, sometimes speed-bridging at ~2.5-2.9 blocks/s, only crouching at each edge) always starts somewhere you can't see (behind you, to the side, or hidden behind blocks), at your level or a few blocks above or below, and bridges straight toward you over a real gap (a short trail of the bridge they already built stays behind them). From above they walk off the end and drop down to you; from below they nerd-pole up (jump, place, jump, place). Once they can reach you they pull out their sword, sprint at you over real ground and their own blocks, and swing a few times (you hear the hits, no damage), then vanish with all their blocks; the blocks are only drawn on your screen (never added to the world, nothing sent to the server, you can't stand on them). Fake players are always enemies (never your teammates) and wear the armor and sword that player was last seen with nearby (their team's leather if you haven't seen them yet). **Fake blocks in bridge holes**: about half of the 1-2 block holes in nearby straight bridges look filled in with the bridge's own block, but nothing is there, so you fall through (drawn only, like the bridger's blocks). A filled hole stays filled until you pass directly over it (walking, jumping over it or falling through). Turn on **Fake blocks look slightly off** to draw every fake block with its texture turned sideways, so a careful look gives them away. They can't hurt you and your clicks pass through them. | how often (about every 45 s), each one on/off with a **Test** button |

They stack: try short sight + grayscale + deaf.

## Keys (Options → Controls → Handicaps, unbound by default)

- **Toggle all handicaps**: pause/resume everything instantly (shows on/off above the hotbar).
- **Open Handicaps settings**.

Both can go on a controller button with NativePad's Controller Bindings.

## Notes

- Screen effects need shader support (any GPU from the last decade). If it's missing you'll get a chat message; Deaf still works.
- Drunk moves your real aim in mouse-sized steps, like a shaky hand on a mouse.
- Much of this mod was written with the help of an AI assistant.

Settings save the moment you change them: no need to press **Done** (Esc or the back arrow keeps your changes too). Every slider has a small box on its right where you can type an exact number (click it, type, press Enter).

## Build

Needs JDK 17: `gradlew build` → `build/libs/handicaps-1.6.1.jar`. MIT license.
