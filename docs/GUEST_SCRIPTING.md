# Driving the emulated Macintosh from a script

**Why this exists.** Every combat feature needs a real battle on screen, and
reaching one by hand does not work: a synthetic `adb shell input tap` will not
open a Mac menu, and two taps are too far apart in time to be a double click.
Several sessions were spent clicking at the Finder and getting nowhere. This is
the tooling for [F17](BACKLOG.md), and the reason it matters is
[F16](BACKLOG.md) — the battle overview may be labelling the wrong side, and
that cannot be settled without a live mid-battle capture.

**Emulator only, and only into the app.** Both tools refuse any serial that is
not `emulator-NNNN`, and refuse to send a key, a tap or a drag unless PoolRad is
the foreground app — the same two rules `android-ui.mjs` already follows. The
second one is not hypothetical: during a long scripted tour the app lost the
foreground, the rest of the run went into the launcher, and the emulator ended
up on a web search for the letter "a". They play the game the way a
player does — menus, keys and the mouse — and never write guest memory. The
BOUNDARY stands: nothing here edits stats, teleports, or touches a save.

## The two primitives that made it work

**`input motionevent DOWN|MOVE|UP`.** Not `input tap`. With separate down, move
and up there is a real mouse: a click the Mac believes, a double click inside
its own double-click time, and a menu pulled down and released over an item.

**Keys already reach the guest.** `adb shell input keyevent 66` dismissed the
Mac's own "press the Return key to continue" dialog, because `LiveMapView` sets
`setFocusable(false)` precisely so hardware keys belong to the emulated machine.
`input text 8` walks the party forward.

## Waiting on the screen, never on a sleep

A fixed sleep is a guess that is either slow or flaky. `tools/guest.py` reads
frames from `adb exec-out screencap`, whose raw form is a 16-byte header then
RGBA8888, so it needs no image library:

```sh
tools/guest.py --serial emulator-5584 settle --region guest   # until it holds still
tools/guest.py --serial emulator-5584 await-change <digest>   # until it moves
tools/guest.py --serial emulator-5584 ink --region 20,1483,1000,3
tools/guest.py --serial emulator-5584 shot /tmp/now.png
tools/guest.py --serial emulator-5584 click 500 1200
tools/guest.py --serial emulator-5584 drag 92 646 150 686     # a menu pull
tools/guest.py --serial emulator-5584 press/move/release      # a menu held open
```

`press`/`move`/`release` are separate so a menu can be photographed while it is
still down, which is how the game's File menu was read in the first place.

## Reading the game's state by counting its buttons

The game says what it wants next by how many buttons it draws in the Message
window, so `tools/play.py` counts them instead of recognising any wording:

| Buttons | State | What it is |
| --- | --- | --- |
| 1 | `continue` | Continue |
| 2 | `question` | a yes/no question |
| 4 | `encounter` | Combat / Wait / Flee / Advance |
| 6 | `explore` | Area / Cast / View / Encamp / Search / Look |

Two measurements make that reliable, and both were found the hard way:

- **The band is `y 1483..1485`, the buttons' top edges only.** The Message
  window's own frame runs the full width at 1474, 1479, 1480 and from 1522, and
  counting across it merged every button into one run.
- **Not the whole row.** The Mac's mouse pointer parks in the row after every
  click and sits low in it; counted over the full row it was a third button in
  a two-button question, and judged as a digest it reported an encounter on the
  very first step.

A state is only believed when two consecutive frames agree. The frame taken the
instant a move lands catches the game mid-redraw, where the row is briefly
neither one thing nor the other.

## Movement, confirmed

Exploration takes the number keys; combat takes the arrows.

| Key | Effect |
| --- | --- |
| `8` | forward |
| `4` | turn left |
| `6` | turn right |
| `2` | turn around |

Verified one key at a time from a known square: `3,8 N` → `8` → `3,7 N` → `4` →
`3,7 W` → `8` → `2,7 W` → `6` → `2,7 N` → `8` → *unchanged, a wall* → `2` →
`2,7 S`.

A move is confirmed by the game's own coordinate readout at `(505,1000,120,40)`
changing — which is also how a wall is detected, since a blocked step leaves it
alone. The clock beside it is deliberately outside that box, or every sample
would differ.

## The scenarios

```sh
tools/play.py --serial emulator-5584 boot            # to the game's menu bar
tools/play.py --serial emulator-5584 load SampleParty
tools/play.py --serial emulator-5584 tour            # Rolf's introduction
tools/play.py --serial emulator-5584 walk forward left forward
tools/play.py --serial emulator-5584 wander          # until something interrupts
tools/play.py --serial emulator-5584 fight           # answer an encounter with Combat
tools/play.py --serial emulator-5584 battle          # wander, then fight
tools/play.py --serial emulator-5584 state           # what the game is asking
```

`load` drives File → Load Saved Game and then the standard file dialog by
type-select, so it needs no coordinates inside the dialog at all.

**`load` only works straight after `boot`.** Once a game is running the game
greys Load Saved Game out — only Quit stays enabled — and picking a disabled
menu item does nothing whatsoever. That failed silently once: the folder and
save names were then typed into the running game, a stray letter opened the
camp menu, and the party was found altering its marching order in a tent.
`load` now checks that the screen changed after the menu pick and says so.

### The two saves on the test disk

| Save | Where it starts |
| --- | --- |
| `SampleParty` | New Phlan, 15,1 W, before Rolf's tour. Civilized: **no wandering monsters**, so `wander` will not find a fight there. |
| `m1gate` | camped at 12,11 in the Slums. Exit the camp and `wander` meets something. |

`wander` declines yes/no questions rather than answering yes — a script should
not spend the party's money — and turns away afterwards. Walking into the inn
is what raised the question, and a turn sent while the prompt is up goes
nowhere, so without the turn the party declines, steps back into the same
doorway and asks again until the move budget runs out.

## Touches always arrive; keys need focus

A touch is delivered to whatever window is under the coordinates. A key goes to
the focused window, and under load this emulator leaves `mCurrentFocus=null`
for minutes while the app is still the resumed activity — every key sent then is
dropped in silence. That is why `boot` clicks the startup dialog's OK button
instead of answering it with Return, and why a step that will not respond to a
key is worth checking `dumpsys window | grep mCurrentFocus` over before assuming
the game is at fault.

## When the guard refuses

`PoolRad is not the foreground app (...)` means exactly what it says, and the
fix is usually `am force-stop` on whatever is named. Once it was not: an ANR'd
`org.chromium.webview_shell` kept its focused window after a force-stop, a HOME
press and an explicit `am start`, and nothing would take the foreground back.
`adb reboot` on the emulator cleared it, and the app's ROM, disks and notebooks
live in app storage, so a reboot costs nothing but the wait.

## Coordinates are for this emulator

The pixel boxes above are for the 1200x1600 test emulator, `emulator-5584`. On
a different window size they need measuring again; `guest.py ink` and a
scanline sweep is how all of them were found:

```sh
python3 - <<'PY'
import sys; sys.path.insert(0, "tools")
import guest
screen = guest.grab("emulator-5584")
for y in range(1470, 1540):
    print(y, screen.ink((20, y, 1000, 1)))
PY
```

## Tests

`tools/test-guest.py` covers the parts that can be wrong without an emulator
noticing: region arithmetic and clipping, the PNG writer, every prompt named by
its button count, the window frame not merging the buttons, the mouse pointer
not being counted as one, and the refusal of any serial that is not an emulator.
Synthetic frames only — no screenshot, ROM, game or save bytes are embedded.
# Timed guest keyboard input from local debug tooling

`bash tools/guest-command.sh emulator-NNNN load` invokes the app's existing
timed Command-L routine through local JDWP. Other supported commands are
`quit`, `begin`, `view`, and `save`; `text STRING` types a short filename and
Return through the same normal input path. This is emulator-only tooling,
not a guest-memory write. The call queues input; verify the resulting dialog
before sending the next command. The text routine supports letters, digits,
spaces and periods. Command input was verified opening the game's standard
Load dialog on the isolated F89 emulator, 2026-09-19.

`python3 tools/start-test-sandbox.py --help` documents creating a fresh Android
data directory and installing a public APK with copies of supplied private
ROM/disk inputs. It refuses existing directories and in-use emulator ports.
Normal Mac shutdown is still required before replacing an installed APK.

For repeated emulator tests, `python3 tools/shutdown-test-guest.py emulator-5586
scratch/shutdown-evidence` performs that normal shutdown. The evidence directory
must be new. It uses the existing timed Command-Q/Return input, OCR checks the
quit question and Finder labels, chooses the visible Special → Shut Down,
then requires the stopped-emulator screen and no open disk-image descriptors.
It retains screenshots and OCR output, refuses unexpected screens, and never
force-stops the app. Requires `tesseract`; test emulators only.
