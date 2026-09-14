# PoolRad Mac Maps: the small, practical plan

Written 2026-09-13. Research and local evidence: [RESEARCH.md](RESEARCH.md).

**Current priorities:** [BACKLOG.md](BACKLOG.md) is the authoritative feature
queue. The user has expanded the original map-only scope to handwritten notes,
screenshots, a read-only party strip, guest desktop appearance, and five offline
reference panels. Handwritten map flags/notes are next after the 0.2.1 rune fixes.
The architecture below still applies; milestones also retain historical context.

## The outcome

Play the existing black-and-white Macintosh Pool of Radiance on the Android
e-ink tablet, with a readable map that automatically follows the party.
This is a personal tool for one game installation, not a general emulator
debugger or another RPG.

## Will this need a custom Mini vMac?

**For reliable live tracking, probably yes. A small personal build is the
recommended route, not a new emulator.** Android isolates ordinary apps from
one another's private memory. The inspected Mini vMac Android source has access
to its own emulated RAM but no existing external RAM interface in its Java/native
bridge. [Android sandbox](https://source.android.com/docs/security/app-sandbox),
[inspected Core.java](https://github.com/dolfin/minivmac4android/blob/b6ff92ce04a006e642ebceadf90b73d9f0b9e454/minivmac/src/main/java/name/osher/gil/minivmac/Core.java).

This is an engineering recommendation based on the inspected source, not proof
that every possible stock-app workaround is impossible.

| Route | Custom Android build? | Decision |
| --- | --- | --- |
| Map panel inside Mini vMac | Yes, narrowly modified | Recommended: same-process RAM access and one app to use. |
| Separate Android memory-reader app | Stock app cannot normally supply private RAM | Do not pursue rooting or debugger attachment as an everyday setup. |
| Desk accessory inside the emulated Mac | Potentially no | Plausible fallback, unproven with this game; still needs the same game-memory discovery, plus guest window/event integration. |
| Static maps or manually imported saves | No emulator changes | Useful fallback, but not continuous automatic tracking. |
| Screen reading / counting movement keys | Potentially no | Not the foundation: obscured views, blocked moves, teleports, and loading games need reliable correction. |

A custom app should install alongside the Play Store app under a distinct
application ID and label. Keep the original installed and use copies of its
disks. Do not try to overwrite an app signed by its original developer. Use one
stable personal signing key for subsequent mapper updates; keep it out of Git.
Android's signing/update rules are documented
[here](https://developer.android.com/studio/publish/app-signing).

## The whole architecture

```text
 ONE ANDROID APP -- entirely on the tablet
 +------------------------------------------------------+
 | Existing Mini vMac emulator                          |
 |   Mac OS -> original Pool of Radiance -> emulated RAM |
 |                                      | read only    |
 |                             tiny game-state reader   |
 |                             area / x / y / facing     |
 |                                      |              |
 | Loaded map geometry in RAM --------> B&W map panel    |
 +------------------------------------------------------+
 No root. No server. No network memory API. No cloud.
```

Use the existing native emulator and Android view system. Prefer one custom
Android Canvas view over adding a browser framework or separate companion app.
Do not rewrite the emulator core, game, CPU, or disk subsystem.

Implementation simplification: the game exposes its loaded geometry through
an A5-relative handle. Read that small block along with the position; a separate
GEO import/cache is unnecessary. The existing DAX reader remains useful for
local validation. Never ship the original game maps in the APK.

## Tablet layout — agreed direction

The user confirmed **Mini vMac** and reports spare screen space even with the
virtual keyboard open. Make a vertical stack the default, not side-by-side:

```text
+----------------------------------+
| MAP                      New Phlan|
|                                  |
|    B&W walls, doors, party arrow  |
|                                  |
+----------------------------------+
|                                  |
|       EXISTING MAC DISPLAY        |
|       Pool of Radiance unchanged  |
|                                  |
+----------------------------------+
|       VIRTUAL KEYBOARD            |
|       (only when opened)          |
+----------------------------------+
```

The map is an Android view outside the emulated framebuffer, not a floating
overlay or a window inside Mac OS. Opening the keyboard reserves space below
the game; closing it returns space to the layout. No pane covers another.

- Keep the game readable and preserve its aspect ratio. Allocate spare height
  to the map; do not split all three panes into arbitrary equal thirds.
- Keep map cells square. Fit the full 16-by-16 area within the available map
  pane, centered rather than stretched to fill a wide strip.
- Use a small area-name heading, high-contrast walls/doors, and a non-blinking
  direction arrow. The newly requested optional party strip uses spare width
  to the right of a left-aligned map; details stay behind a row tap.
- Keep the map position stable while walking; no automatic scrolling or
  animated recentering for these small full-area maps.
- Retain the existing keyboard toggle and add one map-collapse control. On a
  constrained window, collapsing the map can recover game space; do not solve
  insufficient height by covering the game or making everything unreadable.
- Keep game mouse/trackpad input scoped to the game pane. Taps on the map must
  not turn into Mac clicks. Recheck touch-coordinate scaling after resizing.

The current upstream `screen.xml` already uses a vertical layout with a
weighted display/trackpad container followed by an optional `KeyboardView`.
`ScreenView` centers the guest image and computes its scale within that
container. The proposal extends that host layout; it does not require changing
the Mac's resolution or keyboard implementation. See the pinned
[layout evidence](RESEARCH.md#tablet-layout-follow-up).

Actual pane heights remain a device test, not a claim based on guessed tablet
dimensions. Verify keyboard open/closed, system insets, and rotation on the
user's tablet. The approved map-left/party-right arrangement is within the upper
pane, not a replacement of the map-above-game layout.

## Milestones, in order

### 0. Establish the exact baseline — local boot/game proven; tablet match pending

- [x] Inspect the current Android source and likely DOS companion tool.
- [x] Identify usable open-source map decoding references.
- [x] Decode all eight supplied GEO files: 29 records, 1,026 bytes each.
- [x] Ignore local proprietary inputs and extracted resource forks in Git.
- [x] Confirm the app name: the user reports **Mini vMac**, not Mini V II.
- [ ] Verify the selected emulated machine/ROM when reproducing the setup.
  The supplied 256 KiB `MacII.ROM` still needs reconciliation with the actual
  configuration; do not infer the installed app from the ROM filename.
- [x] Boot a copy of the supplied disk and launch the supplied game.
  The supplied game is a StuffIt archive, not a ready-to-mount disk image.
  Preserve resource forks when installing it into a test HFS disk.

**Gate:** the test setup runs the same game/configuration as the tablet. Keep
original inputs untouched. An extraction error in `ITEM2.DAX` must be checked
before declaring the complete game installation healthy; it did not prevent
the separate GEO-format validation.

### 1. Prove live position — first-area checks passed

Implementation update: the fork now draws the current area above the Mac,
using a 1,200-byte native sample from the game's A5-relative globals and loaded
geometry handle. The first live New Phlan pane has been observed after a cold
boot/sample-party load. Startup's empty geometry is rejected. This remains a
single-version prototype; the exact checked movement/state coverage is recorded
in [LOCAL_TESTING.md](LOCAL_TESTING.md).

The New Phlan gate now passes: cold boot/load, scripted movement, manual turn,
successful step, blocked step without drift, and Android background/resume.
Next validation is an area transition and explicit combat/wilderness context.

1. Build the matching Android flavor from the pinned source. Make development
   builds independent of the upstream maintainer's `release.properties` and
   private signing keys. Limit the first target to the user's device/variant.
2. Add an explicit debug-only RAM-snapshot request. Copy RAM at a safe point on
   the emulation thread, then write the copy off-thread. Never read a changing
   native RAM pointer directly from the Android UI thread. Captures stay local.
3. Capture labeled states: stationary, a successful move in each axis, rotation
   without movement, a blocked move, and an area transition. Pair each capture
   with a screenshot or independently known position.
4. Search for loaded map geometry and changing coordinate candidates. Inspect
   the Mac executable's resource-fork code if snapshots leave ambiguity. A
   cached map match is only a candidate, not proof that it is the active area.
5. Identify current area, tile X/Y, facing, and enough context to distinguish
   exploration from combat, loading, and the world map. Determine coordinate
   origin and facing order from observed moves, not DOS assumptions.
6. Validate after save/load, leaving and returning to an area, and a cold boot.
   A single working absolute address is insufficient. For this personal V1,
   one validated game-version profile is enough; resolve relocated state or
   re-detect it rather than pretending DOS addresses apply to the Mac.

**Gate:** a small diagnostic display follows a known short walk and rotation,
does not move on a blocked step, and re-acquires after load/restart. If this
cannot be shown, stop UI expansion and report the exact unresolved field.
The next proof is **one working area**, not a large speculative framework.

### 2. Deliver the first useful map

- [x] Port only the small DAX/GEO reader, preserving MIT attribution.
- [x] Draw monochrome walls, door marks, and a distinct direction arrow.
- [ ] Resolve a reliable area ID/name; the pane currently says “Area map.”
- Verify the actual wall/door interpretation against the game's area view or
  observed routes. Nonzero wall types can include more than solid masonry;
  don't label secret or locked doors without validating the flags.
- [x] Implement the agreed vertical stack: map above the Mac display, optional
  virtual keyboard below. Preserve aspect ratios and input behavior, and
  provide one map-collapse control for constrained space.
- First release shows the full local-area layout. Persistent handwritten notes
  are now the next feature; exploration-only reveal remains optional later.
- [x] After initial detection, sample only the needed state, initially at a modest
  rate such as 4 Hz while active. Coalesce changes; repaint only when the map,
  party position, facing, layout, or tracking status changes. No blinking,
  animation, shadows, or continual full-screen map redraw.
- In combat/loading, retain the last map only with an explicit paused/stale
  indication; never present an old marker as a confirmed live position.
  Unknown state should say “Position unavailable,” not guess.

**Gate:** walk a real route with the map visible, cross to a second area, and
see the correct map switch. Check readability on the actual e-ink tablet;
desktop screenshots cannot establish refresh behavior or ghosting.

### 3. Finish the personal V1

- Exercise all 29 imported GEO records through the parser, and spot-check
  different layouts and door types visually.
- Check turning, blocked movement, map transitions, combat entry/exit, save/load,
  emulator reset, Android background/resume, and missing/invalid map files.
- Only add a separate wilderness renderer if the game exposes a different
  structure and it is needed for the user's route. Until validated, explicitly
  label wilderness as unsupported instead of treating it as a 16-by-16 dungeon.
- Deliver a sideloadable APK, matching source/patches, attribution, and a short
  setup guide. No Play Store submission or public-service infrastructure needed.
- Keep the upstream revision pinned and the modifications small enough to
  maintain. Avoid unrelated emulator refactors.

## Added user priority: PoolRad menu and code wheel

- [x] One top-level **PoolRad** menu groups our helpers; debug capture lives here.
- [x] Native rune/path picker and offline lookup follow the supplied reference.
- [x] All 72 rune images bundled in the personal APK; no runtime download/cache requirement.
- [x] Lookup and rune/path pickers use the upper half without dimming the game.
- [x] **Enter code** types the answer **and presses Return**, per the user.
- [x] Check all 3,888 input combinations for bounded outputs; verify known answers.
- [x] Android emulator check: cached glyphs and a known answer work with networking disabled.
- [x] End-to-end automatic submission accepted by the actual Mac game: 21/14/dashes → WYVERN + Return, with networking disabled.
- [ ] Automatic prompt/rune recognition is optional later, not required for the menu lookup.

**V1 definition:** automatic local-area maps and party direction for this
specific Mac game setup, usable offline on the tablet, with honest unavailable
states and no game/save modification by the mapper. Wilderness is a separately
tracked capability, not silently included in a claim of complete mapping.

## Deliberate limits

No LLM, cheats, character editing, cloud accounts, telemetry, multi-game plug-in
system, OCR pipeline, public RAM server, or all-device matrix. Read-only party
information and offline reference tables are now in scope. User-owned ink,
screenshots, and explicit guest desktop preferences are local writes, not
permission to manipulate game stats or saves. An additional tactical map is
optional later; it is not required for the next slice.
Testing should be short and meaningful: parser boundary tests, saved-state
reader fixtures, an Android build, and a real-device smoke test. No marathon
CI soaks or benchmark infrastructure for this personal tool.

## What is genuinely uncertain

Boot, game load, map decoding, and a live first-area pane are evidenced. The
user reports the app works well on the tablet. Broader state recognition and
specific physical e-ink/stylus checks remain unfinished. Validate area identity
and transitions as part of the upcoming persistent-notes work; do not substitute
a large framework or test matrix for those checks.

## Requested follow-up: personal startup shortcut

Existing `disk1.dsk` / `disk2.dsk` automount already works. After the live map,
configure a guest startup alias or similarly small personal launch path so the
game opens automatically. Keep the user's ROM/game disks private; do not bundle
them in the source repository or a distributable APK. This shortcut is not yet
implemented, and should not depend on blind fixed-delay mouse clicks.
