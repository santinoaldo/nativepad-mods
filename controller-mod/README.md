# NativePad (Forge 1.8.9)

Native Xbox-style (XInput) controller support for Minecraft 1.8.9. No Steam Input needed.

## Install

1. In the Modrinth App, open your 1.8.9 **Forge** profile → **Add content / Open folder** → `mods`.
2. Drop `NativePad-1.4.2.jar` in.
3. **Turn off Steam Input for this game**, or just launch Modrinth normally instead of through Steam.
   Otherwise Steam and the mod both act on the controller and you'll get double input.
4. Make sure the controller is in **XInput / PC mode** (it should show up as an Xbox controller in Windows).
5. Join a world. You should see `[NativePad] Controller connected` in chat.

## Default layout (in-game)

| Button | Action |
|---|---|
| Left stick | Move (8 directions like WASD) |
| Right stick | Look |
| RT | Attack |
| LT | Use / place / block |
| A | Jump |
| B | Sneak (hold) |
| **X** | **Swap to last hotbar slot** |
| Y | Inventory |
| LB / RB | Hotbar left / right |
| D-pad ← ↑ → ↓ | Slots 1 / 2 / 3 / 4 |
| L3 | Sprint |
| R3 | Third person (F5) |
| Back/View | Player list (Tab) |
| Start | Pause |

**Back paddles:** program them on the controller itself to copy a face button. The PC only sees the copied button.
Good picks: paddle = **A** (jump without leaving the right stick) and paddle = **X** (last-slot swap).

**In menus:** left stick moves the cursor (in inventories, chests and shops it **hops slot to slot** instead; flick for one slot, hold to repeat; setting "Left stick snaps in inventories"), **D-pad jumps to the next slot** (inventories, chests, the Bedwars shop) or button (other menus) in that direction (hold to repeat), A = click/drag, X = right click, RB = shift-click (quick move), B or Start = back. The button bound to Inventory (Y) also closes inventories and chests.

**Rumble:** vibrates when you take damage (harder hits rumble harder), a heartbeat at low health, and a long rumble when chat says your bed was destroyed or a trap went off. Each one can be turned off in settings → Rumble, or flip all of it with the **Toggle rumble** keybind. If a chat alert doesn't fire, edit "Chat alert phrases" to match your server's exact message.

**Battery indicator:** shows the controller battery in a corner (settings → Battery indicator) when Windows reports it. Controllers that connect through a USB wireless dongle (EasySMX included) report as "wired" with no battery level, so nothing is shown for them.

**Controller overlay:** a small live drawing of your controller (an Xbox-style pad with grips) in the bottom right corner. Buttons light up when pressed (A/B/X/Y in their colors), the trigger bars fill as you pull LT/RT, and the stick dots move with your sticks (yellow when clicked in). Drag it anywhere with **Move overlay...** in settings → Controller overlay (scroll on that screen to resize; it snaps to the edges and center), or pick a corner, a size, or Off there. It only displays; it doesn't change any input.

The last-slot swap also has a keyboard bind (Options → Controls → NativePad), so it works for keyboard play too.

## Tuning

**In-game:** type `/nativepad`, or go to **Mods → NativePad → Config** from the title or pause screen.
Sliders, toggles and button bindings all work with the controller cursor (hold A to drag a slider).
Changes apply when you press **Done**.

You can also edit `config/nativepad.cfg` by hand and type `/nativepad reload`.
`/nativepad status` shows whether a controller is detected.

| Setting | Default | What it does |
|---|---|---|
| `aim.yawSpeed` / `aim.pitchSpeed` | 450 / 300 | Max turn speed, degrees per second |
| `aim.curve` | 1.8 | Higher = finer aim near center, faster flicks at the edge. 1 = linear |
| `aim.deadzone` | 0.08 | Raise it if the camera drifts |
| `aim.invertY` | false | |
| `movement.analogUnsafe` | false | `true` = partial tilt walks slower. **Singleplayer only**: Hypixel lags you back for it |
| `movement.autoSprintOn` | false | Sprints whenever you push the stick |
| `triggers.triggerThreshold` | 0.10 | How far to pull LT/RT for a click. Lower = faster |
| Buttons | see above | Use **Controller Bindings...** at the top of the settings screen: click an action, press a button. Any keybind works, including other mods' keybinds |

## Build from source

Needs JDK 17. `gradlew build` → `build/libs/nativepad-1.4.2.jar`.
