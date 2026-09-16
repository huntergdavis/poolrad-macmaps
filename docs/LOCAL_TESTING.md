# Local Android prototype

## 0.28.0 — the tablet measured, and a footprint switch (2026-09-15)

The final public universal APK is `scratch/poolrad-macmaps-0.28.0.apk`, SHA-256
`6676b745df67d0a27ca4e0fe8a6e31291bf940a2a9b649765b6da6d198f7667b`, versionCode 94.

**The missing party pane was not a layout bug.** The user's two on-device
screenshots were measured rather than guessed at, which is what he asked for:

| Measured | Value |
| --- | --- |
| Screenshot | 1440 x 1742 px |
| Tab bar | y 0-95 |
| Companion pane | y 96-779, so **1440 x 684** |
| Header cap rows | y 119-139, baseline ~140 |
| Header baseline offset | 44 px below the pane top |
| Implied density | `LiveMapView` draws that baseline at 22dp, so **exactly 2.0** |

Not the 2.625-3.0 the 0.27.0 strip was built for. At 1440 x 684 and density 2.0
the layout already returns a two-column sidebar, so the arithmetic was not the
fault.

**The screenshots are from v0.18.0 or earlier.** They carry a two-line caption
("North up · 55 walked · Info: trail options" above "Tap a tile or symbol ·
Notebook 1"); that first line is a string literal that was removed in v0.19.0,
and it appears in every tag from v0.3.0 to v0.18.0 and in none after. The v0.18.0
layout was a single fixed 216dp column needing `24 + 6*48` dp of height, so at
1440 x 684 any **font scale at or above 1.10** dropped the sidebar outright —
which e-ink tablets commonly run. The current build shows the party at that
geometry at every font scale from 1.0 to 2.0.

Three things came out of that:

- **Every caption now ends in the build name**, e.g. `· v0.28.0`. Dating those
  screenshots took an archaeology pass over 29 tags; it should take a glance.
- **A very large font scale no longer empties the pane.** Seven members at scale
  1.8 wanted more than 684px in both the sidebar and the strip, and the answer
  was to draw nothing. `PartyPaneLayout` now steps the scale back toward 1.0
  until something fits and reports it as `appliedScale`, which the view draws
  the party rows with. Smaller party text beats no party; the map's own text is
  untouched.
- **A new test pins his measured geometry**: 1440 x 684 at density 2.0, swept
  across font scales 1.0 to 2.0 and party sizes 1 to 8.

**The footprint toggle he asked for** is a 26dp button in the top-left corner of
the map, drawn with a 48dp touch target, two filled soles when on and a
white-underlaid slash across them when off. An earlier hollow-soles-plus-slash
version read as a percent sign at button size. It writes the same preference the
Info tab's "Show directional footprints" checkbox uses, so the two agree.

Verified on emulator-5584, not only asserted:

- Rendered at his exact geometry (1440 x 684, 320 dpi) through `app_process`:
  the six-member sidebar draws with the map, the trail and the caption
  `Tap a tile or symbol · Loading notebook… · v0.28.0`; at font scale 1.8 with
  seven members the strip draws four columns of two where 0.27.0 drew nothing.
- The harness itself was wrong before this: its synthetic PRM5 map could never
  match a shipped area fingerprint, so every render it has ever produced showed
  "Position unavailable" and a blank map. It now sends a legacy PRM1 packet,
  which is accepted on geometry alone, and draws a real grid.
- Tapped live: the button flips on → off → on, and off survives a force-stop
  and relaunch.
- `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass, **429 Java tests, zero
  failures/errors/skips**.

Not verified here: anything on the physical tablet. The user installs and
accepts that himself.

## 0.27.0 — the party strip and the bundled journal (2026-09-15)

The final public universal APK is `scratch/poolrad-macmaps-0.27.0.apk`, SHA-256
`55af96f6834e9e596a29336757757facc4849d6ed81083312ad0dfdd02f33060`, versionCode 93.

**The party pane disappearing on the tablet was reproduced without the tablet.**
Simulating `PartyPaneLayout` across densities showed that at 2.625 and above a
1440px pane has no side-by-side arrangement at *any* height: the 280dp map floor
plus two 150dp columns exceed the width. A 292 PPI panel reports exactly that.
The previous column-shrinking fix could not have helped, because the problem is
width that does not exist rather than a column that is too wide.

The party now falls back to a strip under the map. Verified by **rendering on
the device at 320, 420 and 480 dpi**, not only by assertion: at 480 dpi eight
members draw as a 4×2 strip with readable names, hit points and bars, and at
320 dpi the ordinary sidebar is untouched. 428 Java tests pass, including a
sweep of every density, pane height and party size from 1 to 8 that previously
produced nothing at all.

**The journal now ships in the APK** at `assets/journal/adventurers-journal.prjr`
(200,819 bytes): 58 entries, 18 proclamations, 23 tavern tales, 14
illustrations. `JournalController` reads it from assets; the document picker,
the import/replace buttons and the private `journal.prjr` copy are gone.

`check-wheel-apk.mjs` previously *forbade* `.prjr` in the APK. That rule was
there because the journal was treated as private, and the user reversed that on
2026-09-15. The gate now verifies the book instead of banning it: exactly one
`.prjr`, at the expected path, byte-identical to the tree, with a plausible
size. ROMs, disks, DAX archives and personal-package payloads are still refused.

Verified live by clearing app data outright — a fresh install — and opening
Info → Journal: the reader came up with no import step and reported
"58 journal entries · 18 proclamations · 23 tavern tales".

An honest note on that check: `pm clear` also wiped the emulator's ROM, disks
and notebooks, which was careless. The ROM was restored from `scratch/input`,
the campaign disk from the backup taken earlier that day, and its preferences
rewritten by hand. Nothing of the user's was lost — the campaign disk had been
copied off before any of this work began — but a targeted delete of
`files/journal.prjr` would have proved the same thing.

## R9 completed — proclamations and journal entries (2026-09-15, v0.26.0)

The final public universal APK is `scratch/poolrad-macmaps-0.26.0.apk`, SHA-256
`08b336a1b4cd19ec930be9d839ae244233676d450387ab4f4f360542ca8d4836`, versionCode 92.

The two missing wordings came from the user. The proclamation one was then
**reproduced here from scratch**, which is what makes it a live result rather
than a transcription:

- Booted 0.26.0, loaded `PoolRadSave/SampleParty`, ran the tour to its end at
  0,4, and walked the three steps to **3,4 E** outside the City Hall.
- On arrival the game printed `PROCLAMATIONS ARE POSTED ON THE WALLS, IN YOUR
  JOURNAL YOU NOTE PROCLAMATIONS LXIV, LXXVIII, CIX, AND LIX.`
  (`scratch/r9-proclamations-live.png`). It fires **on entering the square**;
  an earlier sweep stood on 3,4 and missed it by pressing Look, which clears
  the window.
- Info → Journal then listed **Proclamation 64, 78, 109 and 59**, in the order
  the game printed them, beside the **Tavern tale 18** that has now survived two
  reinstalls, and the player's own separate bookmark
  (`scratch/r9-encountered-proclamations.png`,
  `scratch/r9-encountered-proclamations-2.png`).

Automated suites:

- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **425 tests,
  zero failures/errors/skips**; `JournalCitationTest` grows to eighteen cases,
  including every one of the eighteen proclamation numerals round-tripping
  through its canonical Roman spelling, a numeral the journal does not define
  being skipped inside an otherwise good list, a non-canonical spelling being
  refused, and a dozen plausible-but-unobserved wordings that must not match.
- All four native probe suites pass unchanged.
- `check-wheel-apk.mjs` passes and `apksigner verify` succeeds on the same key.

Not claimed: the **journal-entry** wording is matched from a screenshot of a
different port of the game, not of the supported Macintosh build, so that third
of the feature is shipped unconfirmed here. Its anchor puts the number between
`ENTRY` and `IN YOUR JOURNAL`, so a wording mismatch produces silence rather
than a wrong number; one Mac-side sighting, most likely in Mendor's Library,
would settle it. Emulator-5584 only; the user's `m1gate` save was never opened.

**E-ink, as reported by the user:** Hunter does all of his own testing on his
e-ink tablet and reported on 2026-09-15 that e-ink is working great. That is his
report, recorded as such; no e-ink, stylus or tablet testing is performed here.

## L2 — the tactical combat overview (2026-09-15, v0.25.0)

The final public universal APK is `scratch/poolrad-macmaps-0.25.0.apk`, SHA-256
`2aa8337fd39cda0958f4472594e0f338fcee98c1add5c7102f904b13ab95a689`, versionCode 91.

Offsets and how they were found: [COMBAT_MEMORY.md](COMBAT_MEMORY.md). The
short version is that the game keeps one four-byte entry per combatant at
`A5-0x46e4`, in the same order as the combat roster, with a count at
`A5-0x46e8` that is zero whenever no battle is running.

**Finding it needed the input question answered first.** The previous pass was
blocked because the number keys that walk the party outdoors do nothing in
combat. They are the wrong keys: **in combat the arrow keys move the
combatant**, the mirror of exploration. With that settled, `Move Left` counting
down from 9 confirmed each step, and a capture pair either side of one verified
square gave the position bytes immediately — `x` and `y` adjacent, changing by
exactly one in the direction moved. A second, orthogonal move separated the two
axes beyond doubt.

Automated suites:

- Native `tools/test-combat-probe.c` with `-Wall -Wextra -Werror` and
  ASan/UBSan passes: the whole-battle shape, a count that disagrees with the
  roster in either direction, every entry's self-index, the flag byte's known
  values, coordinates at and past the ceiling, a missing end sentinel, four
  separate ways of breaking the roster chain, the largest roster the reader
  accepts and one past it, another application frontmost, and a byte-for-byte
  check that guest RAM is never written.
- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **416 tests,
  zero failures/errors/skips**; `CombatSnapshotTest` adds eight.
- `tools/CombatMapRenderCheck.java`, run detached on emulator-5584 through
  `app_process`: **6 checks pass** — a battle draws what an empty one does not,
  party marks use more ink than hollow ones, leaving combat clears the
  battlefield immediately, a battle sample arriving outside combat is ignored,
  a malformed packet leaves the ordinary Combat wording rather than a half-drawn
  grid, and nothing paints the pane's right edge at 380×300.
- `check-wheel-apk.mjs` passes and `apksigner verify` succeeds on the same key.

Decoded against real captures with the production reader:

- Two different live battles read correctly: the ten-orc fight (16 combatants,
  six party east and ten orcs clustered west, exactly as its screenshot shows)
  and the council-guard fight (35 combatants). Camp, walking and the Slums
  arrival all report unavailable with no squares at all.

Live on emulator-5584, API 30, 1200×1600, with `PoolRadSave/SampleParty`:

- 0.25.0 installed, the guest cold-booted, the party was loaded and walked into
  the City Hall, and the council guard was refused to start a real battle.
- The companion header read **`BATTLE · overview`**, **`25,11 to 36,19`** and
  **`6 of yours · 29 others`**, and the pane drew six filled circles for the
  party against twenty-nine hollow squares, in the same arrangement as the
  game's own Combat View directly below it:
  `scratch/l2-combat-overview-live.png`. The caption read *Filled is yours ·
  reference only, tap the game below to act*.
- The party sidebar tracked damage through the same battle, showing the injured
  badge on the members who had taken hits.

Not claimed: **no terrain is decoded or drawn**, so the overview shows where
combatants stand and not what they stand on; the grid's own bounds are not
known either, so the pane frames the squares actually occupied rather than the
whole arena. Initiative, facing and who is acting are not read. The overview
was exercised on one encounter type on this emulator; no physical e-ink,
stylus or tablet acceptance was performed. Only emulator-5584 was used, the
user's `m1gate` save was never opened, and nothing was saved to the guest.

## R9 part one — references the game cites (2026-09-14, v0.24.0)

The final public universal APK is `scratch/poolrad-macmaps-0.24.0.apk`, SHA-256
`96c8185f5cbd57ac5c426bde16b48a11e31115469ffa3afae800d1b163bbc1ca`, versionCode 90.

**The blocking observation was made in play.** Following the user's bar hint,
the party was driven from the tutorial start to the gambling tavern of
civilized New Phlan and declined `DOES ANYONE WANT TO GAMBLE?`. The game then
printed, in its own Message window, the wording that had been missing for two
attempts:

```
YOU OVERHEAR TAVERN TALE 15
```

`scratch/r9-tavern-tale-15.png`. The next message was
`A DRUNKEN BRAWL BREAKS OUT. YOU ARE CAUGHT IN THE MIDDLE` and combat began.

Getting there needed two corrections to the earlier driving recipe, both of
which had silently defeated the previous attempt:

- **The party walks on the number keys, not the arrow keys** — `4` left, `6`
  right, `8` forward on the Mac keyboard's symbol page. The arrow keys do not
  turn the party at all.
- **Every location message must be acknowledged.** The game replaces its
  six-button action row with a single **Continue** button and ignores movement
  until it is clicked, which looks exactly like keys not arriving.
- Routes were computed, not guessed: the live probe packet already carries the
  16×16 GEO geometry, so `GeoMap.edgeKind` gives a breadth-first path over
  **OPEN edges only**. Doorways must be excluded; they are building entrances
  and the game stops the party at them. The tavern is the doorway at tile
  **10,8**, entered south from 10,7, which matches the published civilized-
  district map's 1-based `[11,9]` — the same 1-based offset that puts its City
  Hall `[5,5]` at the clerk encounter the guest actually shows at 5,5.

Automated suites:

- Native `tools/test-message-probe.c` with `-Wall -Wextra -Werror` and
  ASan/UBSan passes: TERec decoding through all four indirections at relocated
  addresses, all 256 byte values in the text (only printable ASCII, CR and tab
  are accepted, and one bad byte rejects the whole sample), the exact ceiling
  and one byte past it, six malformed pointers at each of four indirections, a
  length running off the end of RAM, another application frontmost, and a
  byte-for-byte check that guest RAM is never written.
- `tools/test-map-probe.c` and `tools/test-party-probe.c` still pass unchanged.
- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **408 tests,
  zero failures/errors/skips**. `GameMessageTest`, `JournalCitationTest` and
  five new `JournalHistoryTest` cases add twenty.
- `check-wheel-apk.mjs` passes: 72 bundled runes unchanged, no ROM, disk or
  game archive. `apksigner verify` succeeds on the same debug key.
- One older assertion had to change and is worth noting: `JournalHistoryTest`
  required *every* truncation of a stored record to be rejected, but dropping
  the final byte now leaves exactly a pre-0.24.0 record with no encountered
  list, which must keep loading. That one case is now explicit, with the rest
  of the loop unchanged.

Decoded against real captures, using the production native reader and the
production Java parser:

- `poolrad_message_probe` over two private RAM captures returns exactly the
  text the corresponding screenshots show, to the character.
- Feeding that native packet to `GameMessage.parse` and `JournalCitation.read`
  yields the text and **no** citation for Rolf's farewell, which is correct:
  that sentence cites nothing.

Live on emulator-5584, API 30, 1200×1600, with `PoolRadSave/SampleParty`:

- The 0.24.0 build was installed, the guest cold-booted and its startup alias
  launched the game once. The boot showed the Macintosh's improper-shutdown
  notice, which is honest and expected: the previous session was stuck in an
  endless tavern brawl with the menu bar disabled, so the guest was stopped with
  the emulator's own **Force Power Off** rather than a Finder shut down. Nothing
  had been saved in that session, and the notice cleared with one OK.
- The party was walked back to the tavern door at 10,7 and stepped south. On
  declining the gamble the game printed `YOU OVERHEAR TAVERN TALE 18`
  (`scratch/r9-tavern-tale-18.png`) — a different tale from the first run,
  which is the point: the number is read, not remembered.
- **Info → Journal then showed "Encountered in play · Tavern tale 18"** while
  the game's own Message window still read that sentence:
  `scratch/r9-encountered-live.png`. That is the whole feature working in the
  running app, not a harness.
- A RAM snapshot taken at that same moment, run through the production native
  reader and then the production `GameMessage`/`JournalCitation` classes on the
  host, decodes `YOU OVERHEAR TAVERN TALE 18` and yields exactly one citation,
  `Tavern tale 18`, which `JournalHistory.encounter` accepts once.

Not claimed: **only the tavern-tale wording is recognised.** The journal-entry
and proclamation wordings have still not been observed, so R9 stays open and
neither category is detected; nothing is guessed for them. No physical e-ink,
stylus or tablet acceptance of the new list was performed. Only emulator-5584
was used. The user's `m1gate` campaign save was listed in the load dialog but
deliberately not opened, and nothing was saved to the guest during this pass.

## R6 search-mode indicator (2026-09-14, v0.23.0)

The final public universal APK is `scratch/poolrad-macmaps-0.23.0.apk`, SHA-256
`2b065d0a683142458cdab9d6321304377a6fc0a30b23f87394f494783153eebe`, versionCode 89.

R6 was reduced by the user to the search indicator only; coordinates on demand
were cut. CODE3 `+0x2c52` tests bit 0 of the 16-bit field at
`*(A5-0x5eae) + 0x594` and appends STRS0 `+0x13de`, `" search"`, to the game's
own position line. The probe reports that one bit and nothing else, as byte
1200 of the new 1,204-byte `PRM5` packet, where 255 means the record could not
be read.

**A regression was found and fixed during this pass.** `MapObservation` still
rejected any packet that was not exactly 1,200 bytes, so the first `PRM5` build
installed on the emulator turned the entire map off — the header read *Position
unavailable* and no geometry drew at all, even though the native probe and
`PoolRadState` were both correct. `MapObservation` now accepts `PRM5` at 1,204
bytes and requires byte 1200 to read *unavailable* in a status-only packet,
because a cleared byte there would claim "not searching" beside no position at
all. The native probe sets that byte explicitly on the status-only path, where
the existing bulk `memset` had been clearing it to zero.

Adding a truncated-packet case to the new test also exposed a latent crash:
both `MapObservation.parse` and `PoolRadState.parse` indexed bytes 0..3 before
checking the length, so a short packet threw `ArrayIndexOutOfBoundsException`
instead of reading as unavailable. Both now check the length first.

Automated suites:

- Native `tools/test-map-probe.c` with `-Wall -Wextra -Werror` and
  ASan/UBSan passes. It now asserts `PRM5` throughout, covers all 256 values of
  the low byte of the search field (only bit 0 is read, the high byte never is),
  a missing record, six malformed handles at each of the two indirections, a
  record that would run past the end of RAM, and that a party searching while
  the guest is in combat still yields a status-only packet with no position.
- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **388 tests,
  zero failures/errors/skips**. `MapObservationTest` adds three.
- `check-wheel-apk.mjs` passes: 72 bundled runes unchanged, no ROM, disk or
  game archive. `apksigner verify` succeeds on the same debug key
  (SHA-256 `39fe2409…`). Python helpers unchanged and not re-run.

Live on emulator-5584, API 30, 1200×1600, with `PoolRadSave/SampleParty`:

- The 0.23.0 guest was quit through the game's own **File → Quit** dialog and
  then **Special → Shut Down** before `adb install -r`, so the volume was clean;
  the next cold boot showed **no improper-shutdown notice** and the startup
  alias launched the game exactly once.
- After Rolf's tour ended at 0,4 W the companion header read `0, 4 W` while the
  game's own line read `0,4 W 00:00`.
- Tapping the game's **Search** button put the game's line at
  `0,4 W 00:00 search` and the companion header at `0, 4 W S` in the same frame:
  `scratch/r6-search-live.png`.
- Tapping **Search** again cleared both together — the game returned to
  `0,4 W 00:00` and the header to `0, 4 W`: `scratch/r6-search-off.png`.
- The same session is the live proof that the `MapObservation` fix works: the
  New Phlan geometry, the coordinate rulers, the facing arrow and the party
  sidebar all draw again, which the broken build did not do.

Not claimed: the indicator was exercised only in New Phlan on this emulator, no
search was carried through to a found item, and physical e-ink/stylus acceptance
of the marker is untested. Only emulator-5584 was used. The user's `m1gate`
campaign save was listed in the load dialog but deliberately not opened, and
nothing was saved during this pass.

## R2 training readiness, and R9 blocked (2026-09-14, v0.22.0)

The final public universal APK is `scratch/poolrad-macmaps-0.22.0.apk`, SHA-256
`3e6bb1772ca8a21a5270545dc8db156f9498ea0592ad34589a264489c23ca4e0`, versionCode 88.
Offsets and the threshold table: [TRAINING_READINESS.md](TRAINING_READINESS.md).

**R9 was attempted first, as the user raised it to P0, and is blocked.** The
enabler is built and verified — the game's Message text reads out of
`A5-0x6178` → `TERec` → `hText` and decodes exactly right against all fifteen
private captures — but the phrasing the game uses to cite a journal entry
appears nowhere in STRS0, nowhere else in the game application's resources, and
nowhere in the RLE-decoded DAX files, and no capture contains a citation because
all of them are from the opening tour. Guessing the pattern would file wrong
entries, which R9 forbids. One screenshot of the game citing an entry unblocks
it. No code was shipped for R9. [Reader and evidence](MESSAGE_MEMORY.md).

R2 automated suites:

- Native `tools/test-party-probe.c` with `-Wall -Wextra` passes, now covering
  the real Fighter threshold row, every level 1..8 against the figure the game
  compares, a multiclass character reporting each class, more classes than the
  block holds, a level past the table, a character with no class, and per-member
  independence. Two older blanket "rest of the packet is zero" assertions were
  scoped to the row region; they predated every block added since conditions.
- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **381 tests,
  zero failures/errors/skips**. `PartyTrainingTest` adds ten.
- `check-wheel-apk.mjs` passes. Python helpers unchanged and not re-run.

Private RAM replay decodes the whole sample party, and the values agree with the
game's own character sheet:

```text
Arax the Bold    xp 2134  Fighter L1 next 2001   ready
Lara Spellsword  xp  957  Fighter L1 next 2001 · Magic-User L1 next 2501
Tanarakis        xp  970  Cleric  L1 next 1501 · Magic-User L1 next 2501
Hogarth          xp  970  Fighter L1 next 2001 · Thief      L1 next 1251
Shara the Grey   xp  970  Fighter L1 next 2001 · Magic-User L1 next 2501
Zarram           xp 2134  Cleric  L1 next 1501  ready
```

Live on emulator-5584 after a clean shutdown and in-place update: the guest's own
View for Arax prints `EXP: 2134` and `Level: 1`, and the companion's details read
`Experience: 2134` and `Fighter level 1 · 2001 reached, ready to train` with the
training-hall reminder beneath.

Not claimed: **nobody was trained through a hall**, so a level has not been
watched to change, and the hall's own class restriction and fee are not
modelled. No physical e-ink or tablet acceptance; Hunter's own reports are the
only hardware evidence on record.

**Worth noting for the next pass:** the character details pane now needs
scrolling at 1200×1600 to reach the training lines. It has grown a line at a
time across 0.15.0–0.22.0 and is due a layout review.

## 0.21.0 — the party sidebar on Hunter's actual tablet (2026-09-14)

**The target device is a Viwoods AiPaper Mini: 8.2-inch E Ink, 1920 × 1440 at
292 PPI, Android 13, 4 GB RAM, stylus with 4,096 pressure levels.** Every
emulator check in this document runs at 1200 × 1600 and density 1.25, which is
both taller and much coarser. That gap hid a real defect.

At 1440 px wide and a density near 2.25, the sidebar's fixed 216dp column plus
the map's 280dp floor need more width than the panel has, so `PartyPaneLayout`
fell through to its collapse branch and **the whole party sidebar disappeared
from four members upward** — the user's "not showing the party up top" report.
Two columns did not help, because the failure is width, not height.

Fixed by letting the column shrink from 216dp toward 150dp before giving up, and
capping the sidebar at 55% of the pane. Computed across densities 2.0 and 2.25
and pane heights 530–720, all eight party sizes now keep a sidebar, every row
stays at or above 48dp, every column at or above 150dp, and the map keeps its
280dp floor. The full-width column is still used whenever it fits, so the
emulator geometry is unchanged.

`PartyPaneLayoutTest` gains that sweep plus a check that the wide column is
retained when there is room. `exactReadableThresholdIsSupported` was rewritten:
one pixel under the old hard cutoff no longer hides the sidebar, it shrinks the
column by a pixel; a column that would fall under 150dp is still refused.

**Verify layout work against 1440-wide, high-density geometry, not just the
emulator.** The emulator cannot reproduce this class of bug.

## Repairing the combined boot disk

`tools/fix-startup-disk.py` moves a game out of `System Folder:Startup Items`
into a root folder and leaves one alias behind, optionally setting the desktop
pattern. **Everything is done in place**, for a reason found the hard way.

### The first attempt was broken, and the diagnosis was wrong twice

The first version rebuilt the whole HFS volume with `machfs.Volume.write`. Its
output stopped at startup with the Mac's own *"Not enough memory is available
while using General Controls"*. That was first written up here as a property of
this disk's heavy System Folder — 34 extensions and MacTCP — on the grounds
that "the System and Finder files are byte-identical to the user's original".
**Both halves of that were wrong.** The comparison had only checked the System
*data* fork while the *resource* fork had in fact changed, and the conclusion
was never tested against the obvious control.

Running the control settles it. Booted on emulator-5584, same app, same ROM:

| Image | Result |
| --- | --- |
| The user's untouched `minivmacandpools.dsk` | **Boots clean** to the game (and opens all 15 Startup Items — the original bug) |
| machfs-reconstructed repair, desktop pattern changed | "Not enough memory… General Controls" |
| machfs-reconstructed repair, **no** pattern change, System byte-identical in both forks | **The same error** |
| The same move done in place with hfsutils | **Boots clean**, nothing auto-opens |

So it is neither the System Folder nor the desktop pattern: it is the
full-volume rewrite. [PERSONAL_BOOT.md](PERSONAL_BOOT.md) had already recorded
that *"earlier experimental machfs-reconstructed images failed in General
Controls and must not be deployed. That writer path has been removed."* This
tool had reintroduced exactly that path.

### The in-place tool

`hfsutils` edits the catalog and leaves the rest of the volume alone: `hmkdir`
and `hrename` move the entries, and the startup alias goes back in as MacBinary
II through `hcopy -m`, which carries both forks and the Finder alias bit. The
desktop pattern, when asked for, is patched **where those bytes already sit** —
the `PAT ` 16 and `ppat` 16 payloads are located by their surrounding bytes and
overwritten, so no fork is moved or resized.

The tool verifies its own output before it exits: the System data fork must be
unchanged, the resource fork too unless a pattern was requested, Startup Items
must hold exactly one `adrp` alias with the Finder alias bit set, and every
moved file must match its original data, resource, type and creator.

### The delivered disk

Built from the user's own image with `--desktop bricks`:

```sh
tools/fix-startup-disk.py scratch/minivmacandpools.dsk     scratch/minivmacandpools-bricks.dsk --desktop bricks
```

- **2,811 of 25,165,824 bytes differ** from the source: the catalog entries, the
  new alias, and 55 bytes of desktop pattern. The machfs rebuild had rewritten
  the whole image.
- Booted on emulator-5584: **no memory error**, the desktop shows the offset
  brick course, and the startup alias launches **the game and only the game** —
  none of the journals, rule books or folders that used to open
  (`scratch/c15-bricks.png` layout captured during the run).
- The game was then quit through its own dialog and the guest shut down with
  **Special → Shut Down**, and the image pulled back off the device:
  `scratch/minivmacandpools-bricks-clean.dsk`, SHA-256
  `2bba9628e919b2a7…`. Its HFS `drAtrb` bit 8 is set, so the volume is marked
  cleanly unmounted and the next boot shows no improper-shutdown notice.

The `bricks` pattern is the standard offset course, a mortar row every four rows
with the vertical joints staggered by half a brick. The earlier `stone` name
described those identical bits and still works.

Not claimed: this was exercised on emulator-5584 only, with one boot of each
image; no physical tablet or e-ink testing was performed. The user's campaign
disk was copied off first (SHA-256 `de417f05…`), every swap was made against
that copy, and it was restored and re-verified byte-for-byte afterwards.

## 0.20.0 — quiet header## 0.20.0 — quiet header, no helper captions, two tools removed (2026-09-14)

The final public universal APK is `scratch/poolrad-macmaps-0.20.0.apk`, SHA-256
`23d27ae23bb5dc49832a50587312c9b47212914a6c6b234d35afea34baae77fd`, versionCode 86.

User-reported, then fixed:

- **The header flashed.** It flipped between *Updating*, *Loading / setup* and
  *Position unavailable* several times a second while the game settled, and the
  transient ones drew as inverted white-on-black, so each flip flashed. It now
  shows one steady *Position unavailable* until a real position arrives.
- **Helper-state text removed.** The centred "No verified game position is
  available…" line is gone, and the caption under the map no longer repeats the
  camp/loading explanation; it shows the notebook name and the tap hint only.
  `mode.explanation()` is still in the accessibility description, so the reason
  remains available to a screen reader.
- **Disk checkpoints and Mac desktop appearance are removed** at the user's
  direction, because both required a guest shutdown. There is no safe
  instantaneous version of either: copying or rewriting a volume the emulator is
  actively writing produces a torn image, which is exactly what `DiskAccessGate`
  exists to prevent. Deleted: `DiskCheckpointDialog`, `DiskCheckpointStore`,
  `DesktopAppearanceDialog`, `DesktopAppearanceStore`, `DesktopDisk` and their
  three test classes. `DiskAccessGate` and `DiskAccessGateTest` stay: `Core` and
  `FileManager` still use the gate.

**Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass** after the
removals, and the eight companion View checks confirm the Info list is back to
seven everyday tools with no maintenance action among them.

The party sidebar was checked against a draw-order concern raised by the user:
`onDraw` calls `drawParty` **before** `drawMap`, so the early return in the map
path cannot hide the party. `drawParty` returns early only when there is no
party sample at all or the pane has no room for a row.

## Not reproduced here

The user reported, from their own tablet, that the guest "keeps opening all the
files in the folder" instead of letting them press Continue, and that the party
was not shown. **Neither was reproduced on emulator-5584**, and their screenshot
was not available on either emulator attached to this machine, so it was not
examined. The most likely cause of the first is structural rather than a code
defect: see the combined test disk section below — that image places all fifteen
game items directly in `System Folder:Startup Items`, and System 7 opens every
item in Startup Items at boot. A party that never finishes loading would also
leave the companion with no party to show, which would explain both together.
This remains a hypothesis until the screenshot or the disk's boot behaviour is
actually observed.

## R4 and F13 — readied equipment, one-line caption, two-column party (2026-09-14, v0.19.0)

The final public universal APK is `scratch/poolrad-macmaps-0.19.0.apk`, SHA-256
`c307965d034f7e1ced0b4223241edb77ef9a045b70a365e9d0edfb5583f1f262`, versionCode 85.
Offsets and limits: [EQUIPMENT_MEMORY.md](EQUIPMENT_MEMORY.md).

Automated suites:

- Native `tools/test-party-probe.c` built with `-Wall -Wextra` passes, now also
  covering name composition from one, two and three parts, the descending append
  order, all eight suppression-bit combinations, an empty slot, a purged handle,
  odd/low/out-of-range handles and item pointers, every control byte inside a
  name part, an empty part, a maximum-length name and one byte over, and
  per-member independence.
- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **407 tests,
  zero failures/errors/skips** (403 before). `PartyEquipmentTest` adds nine and
  `PartyPaneLayoutTest` gains the two-column cases.
- **23 Android View checks** pass on emulator-5584, including a new
  eight-member check that asserts the sidebar keeps two columns of four, every
  member is drawn and tappable in its own cell, rows stay at or above the 48dp
  minimum and the map keeps its 280dp floor.
- `check-wheel-apk.mjs` passes. Python helpers were not re-run: unchanged.

Private RAM replay, read-only:

- `f7-combat-active-2.ram`, the one capture taken with the item blocks resident,
  decodes all six members: Long Sword / Dart / Long Bow / Flail with Banded Mail
  throughout, matching the sample party's real kit.
- Every other capture reports **equipment unavailable** rather than inventing
  empty hands, because those item blocks are purged.
- Movement and carried weight decode in **every** capture, item blocks or not:
  Arax 988, Lara 624, Hogarth 822, Shara 735, Zarram 658, all moving 9 squares.
  Tanarakis reads 796 before combat and 781 during it, consistent with darts
  having left her pack.

Live on isolated `poolrad-package-test`, emulator-5584, after a normal game
quit, **Special → Shut Down** and a `/proc/<pid>/fd` check showing no `.dsk`:

- Opening the game's own View for Arax shows `Weapon: Long Sword`,
  `Armor: Banded Mail`, `ENCUMBRANCE: 988`, `MOVEMENT: 9`. The companion's
  details then read exactly `Weapon: Long Sword` and `Armor: Banded Mail`.
- Before the game had loaded those item blocks the same pane read
  **Readied equipment unavailable**, which is the honest state, not an error.
- The map caption is now the single line `Tap a tile or symbol · Notebook 1`,
  and the grid is visibly larger: cell pitch grew from about 22.5px to 23.5px at
  1200×1600, because `MapViewport` reserves 22dp instead of 38dp.
- Six members still render in one column exactly as before.

Not claimed: the **eight-member two-column layout was checked by the Android
View harness at the real density, not by playing with NPCs in the party** — the
sample party has six. Ammunition is not reported at all. No physical e-ink,
stylus or tablet acceptance was performed; Hunter's 2026-09-14 pen confirmation
is the only hardware acceptance on record. The user's `m1gate` save was never
opened, and nothing was saved to the guest.

## R3 — spell readiness (2026-09-14, v0.18.0)

The final public universal APK is `scratch/poolrad-macmaps-0.18.0.apk`, SHA-256
`fe396d9ae192fd67155bbfdb0e84cba755941ea7f5a45ee3dd8bda07ade19f94`, versionCode 84.
Offsets, the five code sites and the PRP4 packet: [SPELL_READINESS.md](SPELL_READINESS.md).

Automated suites:

- Native `tools/test-party-probe.c` built with `-Wall -Wextra` passes, now also
  covering an empty array, all 21 slots filled alternating ready and awaiting
  across three levels, **every spell id 1..127 in both states**, every invalid
  level byte (0 and 4..255), spell id 0 with bit 7 set, per-member independence
  with one unreadable member beside two readable ones, and the table bound.
- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **393 tests,
  zero failures/errors/skips** (385 before); `PartySpellTest` adds eight.
- `check-wheel-apk.mjs` passes. The party sidebar is unchanged, so the existing
  View harnesses were not affected; Python helpers were not re-run.
- Private RAM replay: all captures decode with an entirely empty spell array,
  which is the documented empty state, with HP/AC/class/condition unchanged.

Live on isolated `poolrad-package-test`, emulator-5584, after a normal game quit,
**Special → Shut Down** and a `/proc/<pid>/fd` check showing no `.dsk` handle:

- Baseline: Zarram (Cleric) read `No spells ready to cast` and `Nothing waiting
  on rest` — available and empty, not "unavailable": `scratch/r3-baseline.png`.
- Playing the original game's Encamp → Magic → Memorize and choosing **Cure
  Light Wounds** moved the game's own allowance line from `Cleric Spells: 3` to
  `2` (`scratch/r3-game-memorize.png`). The companion then read **Awaiting rest:
  level 1 × 1** with the rest reminder, while the guest's own *Zarram's Spells
  to be memorized* window listed exactly `*Cure Light Wounds` under `1st Level`:
  `scratch/r3-awaiting-rest.png`. The level and the state both match the game.
- The game calculated `Rest Time: 00:04:15`, and the city watch interrupted the
  rest after five game-minutes. The companion then read `Nothing waiting on
  rest`, and the game's allowance had returned to `Cleric Spells: 3` — the
  pending spell really was discarded: `scratch/r3-after-interrupt.png`.

**Not claimed: a completed rest was never observed.** Every rest attempt in that
New Phlan street was interrupted by the city watch, so the transition to *Ready
to cast* is covered by the decoder tests over all 127 spell ids rather than by
live play. The per-level allowance the game prints (`Cleric Spells: 3`) is a
**remaining** count, not a daily capacity — that is why it is not presented.
No character was played into casting, and physical e-ink acceptance of these
lines is untested. The user's `m1gate` save was never opened; only the
`SampleParty` load was used, and nothing was saved to the guest.

## R8 — disk save checkpoints (2026-09-14, v0.17.0)

The final public universal APK is `scratch/poolrad-macmaps-0.17.0.apk`, SHA-256
`b9893126176cfe5873102c73eae3fbd5ec212559841327deb0ad10149428286b`, versionCode 83.
Behaviour and limits: [CHECKPOINTS.md](CHECKPOINTS.md).

Automated suites:

- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **385 tests,
  zero failures/errors/skips** (377 before). `DiskCheckpointStoreTest` adds eight
  covering the exact-bytes copy and its labels, refusal of every operation while
  a lease models a running guest, restore leaving a working undo copy, the
  newest-first order and six-checkpoint limit, refusal of a tampered payload
  with the live disk left alone, damaged/truncated/renamed records reported as
  errors rather than an empty list, rejection of unusable disks, oversized and
  control-character labels and non-PNG thumbnails before any copy, and refusal
  of null, empty, traversal and unknown ids.
- **8 companion Android View checks** pass on emulator-5584; the tool list is now
  eight entries and routes `CHECKPOINTS` distinctly.
- `check-wheel-apk.mjs` passes and `apksigner verify` succeeds on the same key.
  Python helpers were not re-run: no helper or build tooling changed.

Live on isolated `poolrad-package-test`, emulator-5584, API 30, 1200×1600, on the
real 32 MiB `disk1.dsk`. The guest was quit and shut down with **Special → Shut
Down** and `/proc/<pid>/fd` confirmed to hold no `.dsk` handle before the update:

- With the guest **running**, the panel shows the shutdown instruction and its
  actions are disabled: `scratch/r8-refused-while-running.png`.
- With the guest **shut down**, it reports "The Mac is shut down; checkpoints can
  be saved" and Save produced "Checkpoint saved and verified", listing
  `disk1.dsk · 32 MB · 8a918d7d` with the map thumbnail:
  `scratch/r8-saved.png`. Independent `sha256sum` on device gives
  `8a918d7d0f3ae6c804307d4a17c7880152bf1ea2663635f7e9d1d176d93446a1` for **both**
  the live disk and `checkpoints/<id>/disk.img` — a byte-identical copy.
- A normal restart-and-shutdown cycle changed the disk to
  `ba17772176218e5142e79b27fe4c8507bfba62d39b07cbcbd6e60cd66cc890c3`. Restoring
  the checkpoint returned the live disk to `8a918d7d…` exactly, and the automatic
  safety copy holds `ba177721…`, so the restore is reversible:
  `scratch/r8-restored.png`.
- The restored disk cold-booted with **no improper-shutdown notice**, all 15
  items including `PoolRadSave` present, and the game auto-launched.
- The area reads "Not recorded" because the companion has no verified area while
  the guest is shut down. That is the honest value, not a decoded position.

Not claimed: no checkpoint was taken of a disk holding an in-progress campaign
beyond the sample state above, the six-checkpoint and free-space limits were
exercised in unit tests rather than by filling the device, and physical e-ink
acceptance of this panel is untested. The user's `m1gate` save was never opened;
it rode along inside the copied disk and returned intact. Only emulator-5584 was
used.

## R5 — journal history in the notebook, tasks and flag links (2026-09-14, v0.16.0)

The final public universal APK is `scratch/poolrad-macmaps-0.16.0.apk`, SHA-256
`79fc4da52a2c79419329c6a9cdbbe53364c1a484c1978cbf43c96fc9db1d91e0`, versionCode 82.
Linking rules and limits are in [JOURNAL.md](JOURNAL.md); what a backup now
carries is in [NOTEBOOK_BACKUPS.md](NOTEBOOK_BACKUPS.md).

Automated suites:

- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **377 tests,
  zero failures/errors/skips** (363 before). `JournalHistoryTest` adds seven
  covering the binary round trip, the 0.14.0 preference format, task rules,
  independent link toggling, deleted-flag forgetting, every bound, and rejection
  of each truncation, trailing byte, duplicate, bad kind/number and non-boolean
  checked flag. `JournalArchiveTest` adds six exercising the real
  `exportNotebook`/`importNotebook` code paths on temporary directories.
- **6 detached Android View checks** pass on emulator-5584 for the compact note
  editor, including the new one: the Journal action joins the existing
  scrolling tool row, is at least a 48dp target, never lands on the paper, and a
  linked count neither moves Close & save nor changes the sketch allocation.
  The measured sheet stays 450px at 1200×1600, exactly the 0.13.0 figure.
- `check-wheel-apk.mjs` passes and `apksigner verify` succeeds on the same key.
  Python helper suites were not re-run: no helper or build tooling changed.

Live on isolated `poolrad-package-test`, emulator-5584, API 30, 1200×1600, using
the existing Notebook 1 that already had 0.14.0 preference history:

- Before the update, preferences held `journal.<uuid>.recent = 0:37,2:1,1:59`
  and `journal.<uuid>.stars = 0:37`, and the notebook folder had no journal file.
- After installing 0.16.0 and launching, those two preference keys are **gone**
  and the notebook contains a 71-byte `journal.bin`. Decoding it independently
  gives the matching notebook UUID, recent `Journal 37, Tavern tale 1,
  Proclamation 59`, bookmark `Journal 37` unchecked, no links, a valid CRC and
  no trailing bytes. Info → Journal lists exactly that history.
- **Check off** on Journal 37 rewrote the record with `done=1`, and the state
  survived a guest reload and app restart.
- With `SampleParty` loaded in New Phlan, **Link a map flag…** offered only the
  real existing flag at 11, 2 and recorded
  `Journal 37 -> por-mac-v11-geo-0 @ 11,2`. The entry then shows
  "Your linked map flags · Open 11, 2 · area 0".
- Opening that flag's handwritten page shows its existing ink unchanged and a
  **Journal 1** action; that action lists "Read Journal 37 · done" and
  "Unlink Journal 37" for that exact tile.
- A first attempt stacked a refreshed entry dialog over the previous one, so
  closing it revealed a stale copy without the new link. Fixed by replacing the
  entry window instead of stacking, and re-verified on the rebuilt APK.

**Operator error during this pass, and its outcome:** the rebuilt APK was
installed with `adb install -r` while the guest still had `disk1.dsk` mounted,
instead of shutting the guest down first. On the next boot System 7.5 reported
"This computer may not have been shut down properly". The volume mounted without
any repair, all 15 items including `PoolRadSave` were intact, the game
auto-launched, `SampleParty` reloaded with the six correct characters, and the
notebook, handwriting and journal record were unchanged. No data was lost, but
the clean-shutdown step in [handoff.md](../handoff.md) exists for this reason
and was skipped.

Not claimed: notebook export/restore of `journal.bin` was exercised through the
real store code in unit tests on temporary directories, **not** through the
Android document picker on device this pass. No physical e-ink or stylus
acceptance of the Journal action. The user's `m1gate` campaign save was never
opened and nothing was saved to the guest. Only emulator-5584 was used.

## R1 — party condition badges (2026-09-14, v0.15.0)

The final public universal APK is `scratch/poolrad-macmaps-0.15.0.apk`, SHA-256
`b46a9f4f89b847bd178fdc1af36daea7e1d7ff6e2878c904cad5cfdc3d3e394d`, versionCode 81.
Badge meanings, the Macintosh v1.1 offsets they come from and the PRP3 packet
are in [PARTY_CONDITIONS.md](PARTY_CONDITIONS.md).

Automated suites, all rerun for this pass:

- Native `tools/test-party-probe.c` built with `-Wall -Wextra` passes: all 256
  condition bytes, all 256 effect IDs, combined effects, handle cycles, aliased
  records, out-of-range pointers, every allocator padding remainder, the 64-node
  limit and its 65-node rejection, and failure clearing.
- Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **363 tests,
  zero failures/errors/skips** (355 before; `PartyConditionTest` adds eight).
- **22 detached Android View/software-Canvas checks** pass on emulator-5584,
  including the two new ones: distinct monochrome badges confined to the
  existing class-icon slot with map, names, HP bars and other rows unchanged;
  and condition-only updates cancelling stale party presses and accessibility
  action IDs.
- Python helpers pass **81 tests** (archive 21, personal boot 14, package 16,
  pinned fetch 25, journal 5).
- `check-wheel-apk.mjs` passes: 72 bundled runes unchanged, no ROM, disk, game
  archive or journal payload. `apksigner verify` succeeds on the same debug key.

Private RAM replay through the rebuilt probe, read-only:

- `p1-fresh-party-*`, `phlan-15-1-west-intro`, `phlan-11-2-south-tour`,
  `f10-camp`, `f10-walking`, `m1-*` and `f7-combat-active-2` all decode the six
  correct characters with unchanged HP/AC/class, condition `0` (Okay) and no
  tracked effects.
- `f7-combat-active-1`, `f7-combat-active-3` and `f7-combat-ready-20260914`
  report the party unavailable. The pre-change probe rebuilt from the previous
  commit rejects exactly the same three captures, so this is the existing
  application-profile guard, not a regression, and no condition is invented for
  a rejected sample.

Live on isolated `poolrad-package-test`, emulator-5584, API 30, 1200×1600:

- The running 0.14.0 guest was quit normally through the game's own dialog,
  then **Special → Shut Down** in the Finder. `/proc/<app-pid>/fd` independently
  showed no remaining `.dsk` handle before the in-place APK update.
- 0.15.0 installed over 0.14.0 with `adb install -r`; no app data was cleared
  and no disk was replaced. The guest cold-booted, auto-launched the game and
  the automatic code wheel was accepted without manual entry.
- `PoolRadSave/SampleParty` was loaded. The user's `m1gate` campaign save was
  listed but deliberately not opened, and nothing was saved during this pass.
- All six sample characters retain their **ordinary class symbols**, because
  every member is Okay and at full HP: `scratch/r1-live-sidebar.png`. This is
  the live proof that no badge appears when none is warranted.
- Tapping Arax the Bold shows `Condition: Okay` and `No poison or helplessness
  detected` above the unchanged AC line: `scratch/r1-live-details.png`. The
  guest's own Information window lists the same six names, AC and HP.

**Injured badge seen live, 2026-09-14 (during the R6 pass).** An accidental
combat with a council guard left five of the six members below full HP, and the
sidebar drew the `+` injured badge beside each of them while Zarram, still at
full health, kept his plain class symbol: `scratch/r1-injured-badges-live.png`.
That is the first non-empty badge observed in actual play rather than in a
synthetic packet.

Not claimed: no character was played into an unconscious, dying, dead,
petrified, poisoned or helpless state, so those badges remain covered by the
native decoder, Java and View suites rather than by live gameplay. Physical
e-ink/stylus acceptance of the badges is untested; the user's earlier
handwritten-notes acceptance stands separately. Only emulator-5584 was used;
5580 was not touched. No GitHub Actions workflow exists.

## REF5 — offline adventure journal (2026-09-14, v0.14.0)

The final public universal APK is `scratch/poolrad-macmaps-0.14.0.apk`, SHA-256
`84068d6f6d9eb3703e9d6567bd627546e66ca018f9d8afaab22c57f4f71837e5`.
It contains the reader, not the copyrighted reference book. The private
`scratch/poolrad-journal-0.14.0.prjr` is 200,819 bytes, SHA-256
`a178f61f3b948a85a408858452db9e5aece330e46adc8b73e0c1fdff2a30f374`.
Source documents and forks came from Q2's verified extraction and were not
modified. See [JOURNAL.md](JOURNAL.md) for conversion and tablet import.

On isolated `poolrad-package-test`, emulator-5584, API 30, 1200×1600:

- Info → Journal opened its first-use instructions; the actual Android local
  document picker imported the book. The private stored bytes matched exactly.
- Journal 59 was rejected by the number picker without a recent-history entry.
  Journal 37, Proclamation 59 and Tavern tale 1 opened their correct categories.
- Journal 37's bookmark and recent lookup survived a clean guest exit and an
  in-place APK update. Replacing the reference with the final polished book
  retained that history. No notebook, disk or app data was reset.
- Selecting a deliberately invalid 77-byte file through the real picker left
  the previous book's SHA-256 unchanged and retained the bookmark; the reader
  remained usable. Cancellation is also handled without writes in the callback,
  but a separate live cancellation trial was not performed in this pass.
- The preview showed the complete original atlas. A live visual check caught
  a blank nested-scroll enlargement; the final APK replaces it with a normal
  full-width vertically scrolling image page. `scratch/ref5-atlas-fixed.png`
  shows the original heading/map at readable width above the undimmed guest;
  the Close control remains inside the companion. Earlier blank captures are
  diagnostic evidence, not the final release behavior.
- With the virtual keyboard open, the reader/picker ends at y=500 instead of
  y=630. Entering 37, scrolling the keypad to Read, and opening its illustrated
  entry worked with Close at y=427–495, above the guest. Captures:
  `scratch/ref5-keypad-keyboard.png`, `scratch/ref5-entry-keyboard.png`.
  The illustration scroll changed visible content without moving the guest.

Android `assembleMacIIDebug` and `testMacIIDebugUnitTest` pass: **355 tests,
zero failures/errors/skips**. Python helpers pass **81 tests** (archive 21,
personal boot 14, package 16, pinned fetch 25, journal 5). Eight detached Android
companion View checks exercise the seven real tool buttons, retained map,
small/large-text layouts and touch boundaries. `check-wheel-apk.mjs` verifies
all 72 bundled runes and rejects private journal/game payloads; signature
verification succeeds. No native-reader changes were made or native suites
rerun in this pass. No GitHub Actions workflow is configured.

Manual recent lookups are not automatically encountered references (R9).
Journal history is local per notebook but not in notebook exports yet (R5).
Physical tablet acceptance of this new reader, two-notebook live switching and
full gameplay coverage are not claimed. Prior user acceptance of handwritten
notes on e-ink remains valid. Only emulator-5584 was used; 5580 was not touched.

## Build

Use JDK 17, Android SDK 34, build-tools 34.0.0, and NDK 27.0.12077973.
The 72 rune GIFs are checked in under
`android/minivmac/src/main/assets/codewheel/`: `esp01.gif`–`esp36.gif` and
`det01.gif`–`det36.gif`. No external artwork preparation or runtime download is
needed. The original numbering and unchanged reference images are credited in
[CODE_WHEEL_ARTWORK.md](../licenses/CODE_WHEEL_ARTWORK.md).
`verifyRuneArtwork` rejects missing, oversized, or invalid-header/dimension images.

From `android/`:

```sh
./gradlew :minivmac:assembleMacIIDebug :minivmac:testMacIIDebugUnitTest
```

Set `JAVA_HOME` and `ANDROID_HOME` to the local installations if necessary.
Normal builds produce debug-signed APKs in
`android/minivmac/build/outputs/apk/macII/debug/`, including ARM64 and universal
variants. Debug signing is suitable for this prototype; keep the same personal
key for future updates. The public prototype APK uses that same debug key;
dedicated production signing is not configured.

For faster **local x86_64 emulator testing**, add
`-Pandroid.injected.build.abi=x86_64`. That IDE-style build instead writes to
`build/intermediates/apk/macII/debug/`, is marked test-only, and requires
`adb install -t`. Do not hand that APK to an ARM tablet.

The `macPlus` flavor builds separately and requires a matching Plus ROM. The
supplied 256 KiB Mac II ROM is not a substitute. The tested local app ID is
`com.hunterdavis.poolradmacmaps.ii`; neither flavor replaces the stock app.

## Combined boot + game test disk

The user supplied **`scratch/minivmacandpools.dsk`** on 2026-09-14: one 24 MiB
HFS volume that boots and launches the game by itself. Use it as the standard
emulator test disk.

- Volume name `Mini vMac Boot v2`, 25,165,824 bytes, SHA-256
  `7ee39cb8ee3d96e190eecf10099811e98e086b85cafc516613617da2983816de`.
- Valid `LK` boot blocks and an HFS `BD` master directory block.
- `System Folder:Startup Items` holds the whole game, so it **auto-launches on
  boot** with no timed clicks: `Pool of Radiance v1.1` (APPL/prad, 327,595-byte
  resource fork, matching the known-good size), `PoolRad2/3/4`, `PoolRadGen`,
  `RadiancePrefs`, the Adventurer's Journal parts, the Rule Book sections and
  `tables`.
- `PoolRadSave` contains `PoRCharacters` and **`SampleParty`** and no campaign
  save, so it is a clean baseline rather than someone's playthrough.

**Known problem with this image.** All fifteen game items sit *directly inside*
`System Folder:Startup Items`. System 7 opens every item in Startup Items at
boot, so this disk opens the journals, the rule books and the PoolRad data
folders as well as launching the game. That matches three symptoms the user
reported from their tablet: the guest "opening all the files in the folder"
instead of accepting a Continue click, the Mac's own "could not read a text file
because its text is longer than 8,000 characters" (its text editor refusing the
oversized journal and rule-book TEXT files it was told to open), and no party in
the companion, which follows if the game never finishes coming up. That message
does not exist anywhere in this project's code; it comes from the guest.

`tools/prepare-personal-boot.py` builds the working layout instead: it
**requires Startup Items to be empty** and places exactly one alias there, with
the game and its data folders elsewhere on the volume. Rebuilding this image
that way should stop the boot from opening everything.

**Never mount or modify the supplied file.** Copy it first and work on the copy;
everything above was read from a copy under the session scratchpad. `hmount`
opens a volume read/write, and hfsutils shares one current-volume state per
host user, so run HFS jobs serially and `humount` when finished.

To use it on the emulator, import the copy as an additional disk rather than
overwriting an existing campaign disk. If it must replace one, take a
[checkpoint](CHECKPOINTS.md) of the current disk first — that is exactly what
checkpoints are for, and the restore is verified and undoable.

This disk has **not** yet been booted on the emulator; it has been verified as a
file only. The existing `personal-package/disks/disk1.dsk` remains the disk the
recorded live checks actually ran against.

## Test inputs

Never mount the original supplied disks for testing. ROMs, images, saves,
extracted resource forks, screenshots, and RAM stay in ignored `scratch/`.
The app uses private internal storage for disks/ROMs/cache and disables cloud
backup. Regular imports use the upstream Android file picker.

On this workstation, the AOSP API 30 emulator `poolrad-map-test` runs on
`emulator-5580`, at 1200×1600 and density 200. It has booted copies of
`MacII.ROM`, `MinivMacBootv2.dsk`, and an HFS disk made from the supplied game
archive. This does not verify the physical tablet's e-ink refresh behavior.

### Resource-fork-safe test disk

Install Python 3, `unar` and `hfsutils`. Extract privately into a new directory
and require an audit before building a disk:

```sh
unar -q -s -k visible -o scratch/extracted scratch/input/Pool_Of_Radiance.SIT
python3 tools/verify-game-extraction.py --archive scratch/input/Pool_Of_Radiance.SIT scratch/extracted
node tools/prepare-test-game.mjs 'scratch/extracted/Pool Of Radiance' scratch/test-disks/NEW-game.dsk
```

The helper refuses an existing destination, wraps the extracted data/resource
forks in MacBinary II, then delegates HFS creation/copying to `hfsutils`. It
does not ship game data or implement HFS. The game application has an empty
data fork and a 327,595-byte resource fork; losing that fork makes it unlaunchable.

**Q2 resolved, 2026-09-14:** unar 1.10.8 leaves `PoolRad2/ITEM2.DAX` empty, so
the commands above intentionally fail on that extraction. **Stop on either
extraction or audit failure.** An alternate decoder recovered all 632 bytes
with the archived CRC; a new corrected copy passes all 108 files/114 nonempty
forks and 81 DAX files/606 records. A new HFS disk independently preserves all
file content. See [recovery evidence, tests and limits](ARCHIVE_CHECK.md).
The builder now rejects malformed DAX before making another disk. Existing
installed disks and personal bundles remain unchanged; booting the tutorial
does not establish a complete campaign's integrity.

## M4 wall and doorway symbols (0.13.1, 2026-09-14)

Reused [the existing Mac memory proof](MAP_MEMORY.md), immutable GeoMap data
and shared MapArtwork renderer. A bounded `deja` recall timed out. Inspecting
the original v1.1 CODE 6 accessor and CODE 4 movement dispatch established the
wall-first rule and the reason to keep door states neutral; exact offsets and
limits are in [MAP_EDGES.md](MAP_EDGES.md). No Mac memory or game rules changed.

Final public and personal universal APK builds pass with **346 Java tests,
zero failures/errors**. Ten detached Android software-Canvas checks pass on
isolated API30 `emulator-5584`, including no-surface door bits, full/fog/note
maps, feet/foreground layering and identical neutral states 1/2/3. The private
29-map audit finds 88 directional no-surface edges with nonzero door bits.
That audit is not live exploration of every map. The public-APK check confirms
all 72 rune GIFs and rejects ROMs/disks/game archives/personal payloads; APK
signature verification passes. Native reader code was not changed this turn.

Using the existing 0.13.0 app and unchanged `m1gate` game save, ordinary Android
number keys (4/6 turn, 8 forward) exercised these original-game behaviors:

| Facing edge | Surface / door bits | Actual result |
| --- | --- | --- |
| New Phlan 0,4 south | 4 / 0 | Ivy-covered wall; forward remains 0,4 S |
| New Phlan 0,4 east | 0 / 0 | Open street; forward reaches 1,4 E |
| New Phlan 1,4 south | 5 / 1 | Wooden door in ivy; forward reaches 1,5 S |
| New Phlan 1,4 north | 13 / 1 | Ornate temple door; forward reaches 1,3 N and the original priestess conversation |

Map coordinates/facing matched the guest on each captured settled frame.
Coverage grew from ten to twelve visited squares, with real feet on the route;
the old 11,2 flag stayed present. No game save was overwritten or healing bought.
Initial Android keypad/DPAD attempts did not move the guest and are **not**
counted as blocked-wall evidence; the documented ordinary number keys worked.
Private captures are `scratch/m4-wall-facing.png`, `m4-wall-blocked.png`,
`m4-street-crossed.png`, `m4-door-facing.png`, `m4-door-crossed.png`,
`m4-second-door-facing.png` and `m4-second-door-crossed.png`.

The old app supplies original-game behavior evidence; the newly compiled
production renderer and final build supply the changed-symbol regression checks.
After normal game Quit and Mac Shut Down, installing the final 0.13.1 public
APK in place preserved the writable disk, tile11,2 ink and both New Phlan/Slums
exploration records byte-for-byte. The installed package reports versionCode79;
both new APKs retain the preceding release's signing certificate. Comparison
records are `scratch/m4-upgrade-before.sha256` and `m4-upgrade-after.sha256`.
The installed release cold-boots and automatically launches the original game.
Normal File → Load of the retained `m1gate` save restores New Phlan 0,4 E,
all twelve walked squares, the existing flag, and all six matching HP/AC rows
(`scratch/m4-final-live-map.png`). This is the actual final app window, not
only the detached Canvas harness.
Reopening tile11,2 in the installed release shows the complete map-left page,
the original two-stroke ink and the retained 450-pixel sketch height
(`scratch/m4-final-note.png`). No drawing or note deletion was needed.
There is no claim of a played-through lock/secret mutation or every one of the
88 corrected edges. Physical e-ink stylus/two-finger acceptance was reported by
the user separately on this date; it is not an agent-run test. Detailed hardware
checks remain under Q1. A startup Android System UI ANR was dismissed with Wait
before game acceptance; no app crash was observed on this route.

## F11/M2 tutorial tracking and honest map modes (0.13.0, 2026-09-14)

Reused the original-code evidence and bounded reader in [MAP_MEMORY.md](MAP_MEMORY.md),
the existing local exploration recorder, and the earlier emulator acceptance
below. Bounded `deja` recalls timed out; no unseen history is claimed as evidence.
No Mac RAM, game rules, or save records are edited by this feature.

The first candidate restored Rolf's Continue stops but still missed his walking
tiles. A real unsuspended run exposed that failure: x15,14,13,12,11 at y1 were
discarded because the tour performs coordinate assignments followed by redraw,
not just the ordinary one-square forward command. Three later read-only RAM
captures identified the committed redraw, PRINTCLEAR and Continue phases. These
brief debugger captures are diagnosis, not live footprint acceptance.

The final native profile validates the original New Phlan tour loop and tables,
preserves continuity through its partial coordinate writes, and publishes only
committed positions. General relocations still break continuity. Display mode
is independent of recording safety: verified local positions remain visible
during story text, but only safe observations authorize visited squares or feet.
Partial-update frames neither record nor refresh the previous-safe deadline.
Camp/combat/outdoors/loading packets contain status, never stale local positions.

Final universal public and personal builds pass with **340 Java tests**. All
**33 detached Android View/software-Canvas checks** pass on API30: PartyPane20,
CompanionPane8 and compact-editor5. Three native map/party/wheel suites pass
with ASan/UBSan and strict compiler warnings, including real captured-memory
replays and malformed tour-loop/table/phase negatives. The existing 54 Python
helper and 18 lifecycle/input checks also passed during this change.

On isolated `emulator-5584`, normal Mac Quit and Finder Shut Down preceded an
in-place final APK install. The disk, existing two-stroke tile11,2 flag, and
six-tile exploration record have identical before/after hashes. This is an
actual update preservation check, not a fresh-install substitute. The earlier
combined candidate's real camp entry showed the Camp badge, reference map and
six HP rows without adding visits; ordinary Exit restored the live arrow.

The final installed APK then cold-booted and loaded the unchanged SampleParty
through the original File menu. During an **unsuspended, normal-speed** Continue
walk, the map moved from 15,1 W through x14,13,12,11 at y1 to 11,2 S. The
mid-walk 11,1 S frame matches the guest and already shows the new footprints;
the Temple stop retains them while its story text displays. Previously saved
six-tile coverage becomes ten as the four unseen corridor tiles are observed.
The original 11,2 flag remains present. Private evidence:
`scratch/m2-final-tour-midwalk.png`, `m2-final-tour-temple.png` and
`m2-final-tour.log`. No debugger pauses supplied this acceptance route.
The actual Info → Exploration trail panel lists 11,2 → return north to 11,1,
then eastward returns through 12,1, 13,1, 14,1 to 15,1. The movement samples
share native epoch14; the five-second Temple story-print interval is displayable
but not recordable and does not invent an extra step.

Reopening the old flag in the final installed APK still measures y175..625
for the sketch, with title-row Fit page, no Pen only or Save PNG controls,
and byte-identical saved ink (SHA-256 beginning `4f54c36f`). The README's compact-editor
image is this actual final app window, not a mockup.

Loading/setup and combat classifications also replay actual captures; wilderness
has original-code/synthetic coverage only. Dedicated tactical/wilderness maps,
all-area playthrough and physical e-ink/stylus acceptance remain open.

## F12 compact flag editor (0.13.0, 2026-09-14)

Reused the existing composite InkSheetView, normalized strokes, ordered
autosave queue and upper-companion dialog bounds. Only the editor's chrome
and controls changed; its old pen-only preference is no longer consulted.
Historical `deja` recall timed out; the repository's prior note evidence was
used instead. The original game below remains unchanged and undimmed.

Public and personal APK builds and 339 Java tests passed on the first combined
0.13.0 candidate. On the isolated API30 `emulator-5584` at 1200×1600/density200,
the actual app dialog occupies y110..630. Its old sketch was y334..547 (213px);
the compact editor is y175..625 (450px), with title/status and all controls
sharing the row above. Close & save never scrolls away. Pen only and Save PNG
are absent. `scratch/m2-note-before.png` and `scratch/m2-note-after.png` show
the real before/after; these are not physical tablet screenshots.

A finger-drawn temporary stroke autosaved; Undo restored the original ink hash
`4f54c36f19a4de315c33bebd1b6c0a72850ee5501cb5b24a094fec26a0919d10`.
Redo restored the identical added-stroke record; another Undo restored the
original again. Fit, Close & save and reopen kept that exact note and displayed
the map-left/writing-right composition in `scratch/m2-note-reopened.png`.
The guest remained at the training-school tutorial stop, 5,2 E. No game save
was overwritten, and no existing handwriting was left changed.

Five actual detached Android View/input checks in `tools/NoteEditorLayoutCheck.java`
pass: doubled allocation, preserved page aspect, narrow scrolling tools with
fixed Close, larger text, normalized ink across resize/Fit, finger drawing and
canceled input. Measured pane1200×520/header60/sheet1190×450/paper1160×435.
These checks are not physical stylus, palm rejection or e-ink acceptance.

## F10 exploration memory (0.12.0, 2026-09-14)

Reused the proven native identity/allocator work from [M1](AREA_IDENTITY.md),
the original notebook store/archive, and the existing detached Android Canvas
and lifecycle harnesses. Local `deja` recalls timed out; no unseen historical
result is treated as evidence. All new checks used copies on the isolated
`poolrad-package-test` / `emulator-5584`; the older `emulator-5580` was untouched.

### Checks and live corrections

Public universal APK build and all **325 Java tests pass**, including PRM3
compatibility/processing validation, immutable visited bits and bounded route,
ordered recording/gaps/clearing, corrupt persistence, and exact backup restore.
Three C address/undefined-behavior sanitizer suites pass. Existing Python
packaging/fetch/boot suites pass (16 + 25 + 13), as do ten actual lifecycle-source
and eight stopped-core input-binding checks. No marathon CI suite was added.

Actual Android software rendering passes eight exploration checks, sixteen
party/map checks and eight companion checks. These cover four travel directions,
visited-only geometry, prior real feet surviving a later anchor, unchanged note
geometry, wall/flag layering, unsafe-area overlay clearing, identical-sample
callbacks and all six Info tools. They use synthetic fixtures, not physical
e-ink or GPU acceptance. The first standalone launch used an incorrect class
name and aborted; the documented unqualified invocation passes all eight checks.

The first live APK exposed over-fragmented stationary anchors. System 7 briefly
switches CurApName to background processes between game ticks; the observer now
skips those bounded non-game intervals rather than reading cached game pointers
or treating every switch as a load. An explicitly unavailable requested sample
still breaks continuity. Input-busy engine-4 packets record nothing; the next
settled sample must retain the native epoch and fit the 1,250 ms/tile limits.
Identical stationary anchors collapse, and gaps do not erase earlier genuine
directional feet. Focused regressions cover all three corrections.

A debugger callback trace was inconclusive and is not acceptance evidence.
The corrected build was checked without suspending the core, using temporary
opt-in `PoolRad.Walk` DEBUG metadata (no RAM contents); logging was disabled
again afterward. [Verified gate fields and limitations](MAP_MEMORY.md).

### Actual game and storage acceptance

Normal game Quit → Finder Shut Down reached **Restart Emulator** before APK
replacement. Updating to the corrected 0.12.0 build retained the existing note
byte-for-byte and earlier three-square coverage through cold boot and normal
File → Load of the private `m1gate` save (New Phlan 0,4 E). No save was overwritten.

After the explicit New Phlan reset confirmation, ordinary forward inputs
visited 0,4, 1,4 and 2,4. The actual Info route includes **2,4 — return west to
1,4**, recorded while Info was selected. Turning west and returning to 1,4
leaves the earlier eastward feet visible behind the party. Fog shows three
walked squares, hides other geometry and leaves the user's old manual flag
visible. The companion coordinates and all six HP/AC rows match the guest.
Host stalls/missing samples produced explicit segment starts on some moves;
this is deliberately not a claim of complete step-by-step replay.

The ordinary west gate crossing shows **Slums of Phlan 15,4 W**, zero flags and
one independently walked square. Turning back and returning to New Phlan
restores its three-square coverage and original flag. Clear footprints only
retains those three squares and leaves a fresh current-tile anchor; handwriting
is unchanged. The old tile 11,2 flag still opens its saved composite note page
while fog is enabled. Reset and clear confirmations stay above the undimmed game.

A real Android document-picker export creates a 727-byte PRNA backup containing
the notebook record, original ink and exploration for GEO 0 and GEO 20. Its
length/trailer SHA-256 pass independent inspection. The production
`NotebookStore.importNotebook` restores this actual export into a fresh local
directory; a new store instance reads Phlan=3 and Slums=1 and the exact original
219-byte flag record. An initial ad-hoc assertion used a private field and did
not run; the corrected public-API assertions all pass before this claim.
The installed campaign was never removed or overwritten for this check.

Original ink SHA-256 remains
`4f54c36f19a4de315c33bebd1b6c0a72850ee5501cb5b24a094fec26a0919d10`.
Private screenshots/RAM/exports stay in ignored `scratch/`. Both public and
personal universal builds pass. Public 0.12.0 SHA-256:
`fb89523c1ad9905ccafb1cd73137ffe4ebb9d0f6cd880f758f68ee628c85b836`.
The public APK has only the 72 credited rune assets, no ROM/disks/game archive,
and the same prototype certificate. The personal artifact stays local at
`scratch/poolrad-macmaps-0.12.0-personal.apk`; it is not a public release asset.

Physical e-ink/stylus, full-game mode presentation, all-area playthrough and
instruction-level continuity remain explicitly unclaimed. Hardware N3/Q1 and
the remaining M2/P1 queue are not closed by these software checks.

## M1 named area identity (0.11.0, 2026-09-14)

Public and local-only personal universal/all-four-ABI APKs build, versionCode 76.
**287 Java tests**, 54 Python helper tests, three native ASAN/UBSAN reader suites,
eight stopped-core input checks and ten companion lifecycle checks pass.
The focused identity/parser run contains 25 tests. Twelve actual detached
Android View/software-Canvas checks pass, including the new bounded long-title
and unavailable-status check. No physical stylus/e-ink or CI acceptance is inferred.

The private-source check verifies all 29 full-map and 768-byte prefix fingerprints
against the production catalog, including source-derived fourth-plane changes
retaining each ID. Independent review reproduced the source inventory and tests.
The standalone `AreaFingerprints` helper needs JDK 17; an initial root invocation
with `--release 8` correctly failed to compile the diagnostic, then passed with
JDK 17. App sources and their focused tests retain Java 8 compatibility.
The personal build's first invocation was interrupted with status 143 without a
compiler diagnostic; its incremental two-worker retry completed successfully.

Isolated AOSP API 30 `emulator-5584` (1200×1600, density 200) cold-booted the
existing private combined disk at normal speed. The other emulator, `5580`,
and the original input files were not touched. Android System UI briefly offered
Wait during emulator startup; choosing Wait recovered without stopping the Mac.

Before upgrading, ordinary SampleParty load, Rolf's tour and movement reproduced
New Phlan `0,4 W` → Slums `15,4 W` → turn to `15,4 E` → New Phlan `0,4 E`.
A newly written New Phlan tile `11,2` note disappeared from the Slums and returned
in New Phlan. Its saved bytes stayed unchanged. The guest's settled position and
all six party HP/AC rows agreed with the companion. Its arrival text can lag the
current coordinates; full loading/combat presentation remains M2, not this proof.

Four new read-only RAM captures replay through the exact updated native PRM2
reader and Java catalog as `0 → 20 → 20 → 0`:
`scratch/m1-new-phlan-before.ram`, `scratch/m1-slums-arrival.ram`,
`scratch/m1-slums-settled.ram`, and `scratch/m1-new-phlan-return.ram`.
Screenshots and probe files remain private in `scratch/`.

The original camp Save command created a separate **m1gate** save; SampleParty
was not overwritten. After normal game Quit and Finder Shut Down, the actual
Restart Emulator screen appeared. Installing the public 0.11.0 APK in place
preserved the entire writable disk and the existing note byte-for-byte:

- Disk SHA-256: `c2a70770bd4db565564f4a6384282fc6324b0d4832939127be106a297709aee5`.
- Tile `11,2` note SHA-256: `4f54c36f19a4de315c33bebd1b6c0a72850ee5501cb5b24a094fec26a0919d10`.

The public APK audit confirms all 72 offline rune images and no ROM/disks/game
archives. It retains the previous signing certificate. Public artifact:
`scratch/poolrad-macmaps-0.11.0.apk`, SHA-256
`d7727af3c51055d740875dd6d2f7dd64688caf68443b9c16aa23d08ba78f7c8d`.
The personal APK stays local and is not a public release attachment.

After the update, normal Mac startup/code-wheel handling and File → Load opened
**m1gate** at New Phlan `0,4 E`, with the original flag and all six party rows.
The new read-only `scratch/m1-upgrade-reload.ram` also passes the production
reader as mode 1, valid GEO 0. The first automated flag tap did not open a page;
the retry opened the correct tile `11,2` sheet and its original two-stroke L.
Closing it without editing retained the exact note checksum above.

The updated app then walked the route again: New Phlan `0,4 W` → named
**Slums of Phlan** `15,4 W`, zero inherited flags → turn to `15,4 E` → New
Phlan `0,4 E`, restored flag → turn to `0,4 S`. Both settled screenshots agree
with the guest's own coordinates/facing; all six HP/AC rows still agree.
Evidence: `scratch/m1-upgrade-loaded.png`, `scratch/m1-upgrade-note.png`,
`scratch/m1-upgrade-slums-settled.png`, and `scratch/m1-upgrade-return.png`.
This also completes M3's specified two-area/save-load/cold-boot route; it does
not convert that bounded route into complete game or physical-device coverage.

The [native proof](MAP_MEMORY.md) establishes the fourth-plane write boundary;
the [name provenance](AREA_NAMES.md) distinguishes original names from honest
descriptive labels. All-area playthrough and an actual door-mutating action are
not claimed from source-derived/synthetic checks. Full mode detection,
wall/door semantics and physical-device work remain separate.

## P1 compact party acceptance (0.10.0, 2026-09-14)

Public and local-only personal universal APKs build; **277 Java tests** pass
with zero failures/errors. The 54 Python helper tests, three native ASAN/UBSAN
reader suites, eight stopped-core input checks and ten companion lifecycle
checks also pass. The new packet decoder has 20 focused tests; the layout has
11. No CI or physical tablet run is inferred from these local checks.

Eleven actual detached Android View/software-Canvas party checks pass:
full/partial/zero HP, signed/unknown AC, all 18 distinct original class marks,
unknown class, join/leave/reorder, map-versus-row touch routing, stale touch and
accessibility actions, cancellation and small/large-text layout collapse.
Eight existing companion View checks also pass against the new source. An
initial hide/show fixture failed because detached Views do not receive Android's
attached visibility dispatch. The corrected fixture explicitly calls the real
visibility callback; it tests cancellation, not attachment. Production gesture
code was not weakened. The actual Info → Map transition was checked separately.

On isolated API 30 `emulator-5584` (1200×1600, density 200), the public 0.9.0
APK reproduced the fresh-SampleParty failure while Rolf and New Phlan `15,1 W`
were visible. Two private read-only captures established the Mac allocator
padding error described in [PARTY.md](PARTY.md). `tools/CapturePartyRam.java`
invokes the existing DEBUG snapshot request through local JDWP; it adds no
everyday menu or guest-memory editing. Original campaign emulator `5580` was
not touched.

After normal guest Quit → Finder Shut Down, the 0.10.0 APK installed in place.
The writable combined disk retained SHA-256
`71747cde21672d524d6b5c574220cf699130a57b46897d4933b70d9179248a53`
across installation. A fresh boot and the original unmodified SampleParty then
showed all six correct HP pairs and AC `0,−1,1,1,0,3` beside the Mac's own rows.
Lara's tap opened Fighter/Magic-User, 8/8 HP and AC −1 in the undimmed upper
companion rectangle. Keyboard open collapsed the sidebar and retained the map;
closing it restored the rows. Info → Map also restored fresh rows, with Rolf
and the guest position unchanged. Screenshots: `scratch/p1-final-party.png`,
`p1-lara-details.png`, `p1-keyboard-open.png`, `p1-restored-map.png`.

Existing F7 actual damage/healing/reorder acceptance remains documented in
PARTY.md; its HP/list semantics are unchanged and private combat captures replay
through the new reader. Join/leave changes are checked with native, Java and
Android View fixtures, not a claim of newly exercised live NPC recruitment.
Broader NPC/mode coverage, physical e-ink/stylus and Q1 recreation remain open.

The public APK has all 72 unchanged rune illustrations and no private media.
Public SHA-256:
`4920b928c32b6049374f203b3fbcd4786b781f6b1baa937463d50cfd38fa64da`.
Local-only personal APK SHA-256:
`1f803852afef8110ce5ba325319549d95fefd1b127231843b0982a36bfb39d0a`.
The signing certificate is unchanged from prior updates; private assets,
diagnostic RAM, writable disks and personal APKs remain ignored/unpublished.

## UI1 companion tabs acceptance (0.9.0, 2026-09-14)

Public and local-only personal universal APKs build. **262 Java unit tests**
pass with zero failures/errors, including eleven new geometry/allocation tests.
The existing 54 Python helper tests, three native ASAN/UBSAN reader suites and
eight stopped-core input checks also pass. The public APK checker confirms all
72 unchanged rune pictures and no ROM, disk, game archive or private bundle,
including after building the personal flavor in the same worktree.

`java tools/CompanionLifecycleCheck.java` passes ten checks extracted from the
actual fragment/activity method bodies, using recording views/preferences/Core
and a queued Handler. It covers one-time legacy-hidden migration, new-key
precedence, saved/unknown tab IDs, the activity's replacement-fragment path,
shown/resumed/Map polling, stale samples/generations/cores, hide/show and focus,
and wheel independence through pause/resume. These are software fixtures, not
proof of native session recovery after activity destruction.

The eight checks in `tools/CompanionPaneCheck.java` run on real detached Android
Views with installed APK resources. They cover the single retained map,
selection metadata, all five tools, large-text/narrow scrolling, black/white
contrast, touch consumption, and actual `MapStackLayout` guest/keyboard geometry
through Map/Info and hide/show in portrait/landscape/narrow sizes. An initial
test incorrectly asked a detached View for a populated accessibility node;
Android does not populate that node without attachment. The corrected probe
checks actual View metadata, and the attached UI tree separately confirms
Info `selected=true`, Map `selected=false`, labels and non-keyboard focusability.

Live checks use only the separate `poolrad-package-test` AVD, `emulator-5584`,
AOSP API 30, 1200×1600 at density 200. The existing campaign on 5580 is untouched.
The 0.8.0 installation was already cleanly shut down before the in-place update;
its existing private combined boot/game disk is reused, not replaced.

- Map/Info occupy the previous companion budget, including the 48dp tabs.
  Guest top stays at y=630 with the keyboard closed; the map itself starts at
  y=170 below the tabs. All five Info tools open and Close returns to Info.
- Reference windows are `[0,110][1200,630]`, not half the activity. With the
  guest keyboard open they shrink to `[0,110][1200,500]`; the Mac begins at
  y=500. Actual Spells, Equipment, Money and all three rune/path pickers remain
  above it, with scrollable content, reachable Close and no game dimming.
- PoolRad has exactly Show companion, Notebooks, Screenshot and Desktop
  appearance. Hiding Info removes the whole pane; opening Notebooks from that
  state reveals it and waits for valid bounds. Closing the notebook chooser
  returns to the retained Info tab. No notebook was deleted or replaced.
- Original automatic code-wheel entry succeeds while Info is selected. The
  existing game then accepts File → Load. After selecting PoolRadSave and
  touching Info, a held Android Return opens the guest folder; another loads
  SampleParty. Zero-duration ADB Return was missed, as distinct key edges need
  guest CPU time; the held-key check reuses the existing input timing described
  in [WHEEL_MEMORY.md](WHEEL_MEMORY.md), not a new key-routing workaround.
- Returning to Map requests fresh data and shows New Phlan 15,1 W, agreeing
  with the original guest during Rolf's introduction. Fresh-save party HP is
  still unavailable on this disk, the same known coverage gap as B1/W1.
- Android Home → resume brings the same activity back with Info selected and
  the guest intact. Screenshot produces a real 1200×1420 PNG with Info's five
  tools above the original Rolf/party display; menu, toolbar and system bars
  are excluded. Cancel returns normally, and switching back to Map restores
  fresh position data. This does not test activity/process destruction.
- Live picker inspection caught AppCompat reapplying its animation style when
  a lazily created picker was shown. The shared helper now calls `create()`
  before configuring its window, so the first frame is configured too. After
  normal game Quit → Finder Shut Down → Restart Emulator, the final public APK
  is installed in place. Its main lookup and all three rune/path pickers have
  no window-animation style and occupy `[0,110][1200,500]` with the keyboard
  open; settled rune grids/path choices and their Cancel actions are usable.
  Initial Android
  accessibility dumps after relaunch returned no root; retry after startup
  succeeded. No APK crash or guest reset was used to work around that delay.

No physical tablet, e-ink refresh, stylus/palm or hardware-keyboard acceptance is
claimed. Forced native activity recreation, split-screen/vendor window insets,
full-game area/mode coverage and fresh-save HP remain separate validation work.
Landscape/large-text coverage above is the View/geometry probe, not a claim
that Q1's native resize/recreation lifecycle issue is fixed. No CI is configured
or claimed by this local release check.

## W1 desktop appearance acceptance (0.8.0, 2026-09-14)

Both public and personal universal Mac II APKs build with JDK 17. The full
unit task passes **251 tests, zero failures/errors/skips**: 13 new bounded-HFS
desktop tests, 17 setting-store tests and eight disk-access tests, plus the
213 existing tests. The 16 personal-package, 25 source-fetch and 13 personal
boot-helper tests pass, as do all three native reader sanitizer suites.
The public artifact checker verifies all 72 unchanged rune pictures and no
ROM, disk, game archive or private personal-package assets.

The new tests use synthetic disks, including a fragmented System resource
fork and an ordinary saved-game region. They check allocated/overlapping
extents, overflow chains, malformed resources, dirty/locked disks, unsupported
profiles, exact appearance-only edits, and restoration while retaining a newer
save byte. Store checks cover original-backup corruption, path confinement,
failed publication, same-size/mtime source mutation, cleanup and an existing
reader keeping its original inode. Maintenance refuses running emulation and
disk import/mutation; only native exit plus handle cleanup releases that gate.
Pausing or merely observing a non-ready screen is insufficient.

Actual acceptance uses the separate AOSP API 30 `poolrad-package-test` AVD on
`emulator-5584` at 1200×1600, density 200. The original campaign emulator on
5580 is untouched. The test uses its existing writable 32 MiB combined disk,
not an original input or replacement campaign image.

- A normal game Quit → Finder Special → Shut Down reached Restart Emulator
  before updating 0.7.0 to 0.8.0. The original game then booted again normally.
- During emulation, White/Mist/Stonework previews are available but Check/Apply
  are disabled, with explicit clean-shutdown instructions. The guest remains
  visible and undimmed below the existing upper-half dialog.
- Normal guest shutdown under the new gate enables the disk check. The actual
  System 7.5.5 resources validate, and Apply White completes through the menu.
  A 380-byte original-setting record is saved in private app storage.
- Comparing **every byte of the 32 MiB disk** immediately before/after Apply
  finds exactly 57 changed bytes, all inside the permitted `PAT ` 16 / `ppat`
  16 appearance fields. Catalogs, executable resources, saves and every other
  byte are unchanged. Both mono and color forms are updated; no RAM patch or
  Android paint-over is involved.
- Restart Emulator boots to a genuinely white guest desktop with the original
  disk, utility and Trash icons intact. Early Welcome-to-Mac boot frames still
  use the ROM's checker pattern; the selected System desktop appears afterward.
- The startup alias still launches the original game and automatic wheel entry
  still succeeds. Loading the existing SampleParty reaches Rolf's introduction;
  guest and live map both show New Phlan 15,1 W. Original game windows remain
  intact on the white background. Fresh-save companion HP is still unavailable,
  as recorded for B1; this appearance change does not claim to fix that reader.
- After another clean shutdown, the final APK is installed in place. Its
  installed SHA-256 exactly matches the public release artifact. It boots with
  the White setting intact; normal Quit/Shut Down makes the original setting
  available again, so the backup survives the update and process restart.
- That final artifact applies Mist and Stonework from their previews. Relative
  to the stopped white disk they change 16 and 30 appearance bytes respectively,
  with zero other changes. Their binary patterns, fallback bits and color
  pixels agree. Controls, Close and Back are locked during saving. These two
  styles are checked through real menu/disk operations, not separate guest boots.
- Original restores all **190 original resource bytes exactly**, while every
  other byte still matches the current disk. **1,008 bytes changed by the
  intervening normal guest sessions are retained**, not rolled back to the first
  before-image. These are filesystem/session changes, not a claimed newly saved
  campaign; later campaign-byte preservation is also covered synthetically.
- Restarting the final artifact after restoration shows the original gray
  checker desktop and the original game launching again (`w1-restored-desktop.png`).

Evidence remains private in `scratch/w1-running-preview.png`,
`w1-white-saved.png`, `w1-white-desktop.png`, `w1-party-file-menu.png`,
`w1-stone-preview.png`, `w1-original-restored-ui.png`, and stopped before/after
disk copies.
The supplied original ROM and source disks are not modified or published.
An Android SystemUI ANR occurred during the old 0.7.0 cold-start setup, before
installing this change; selecting Wait allowed that session to continue. This
is not claimed fixed by W1. The checked 0.8.0 Android crash buffer is empty.
Physical tablet, stylus, e-ink, broader System versions and arbitrary wallpaper
utilities remain untested. No GitHub Actions or physical-device CI is claimed.

## B2 source-fetch build acceptance (2026-09-14)

This is build tooling on `main` after 0.7.0, not a new Android runtime version.
`python3 tools/test-fetch-personal-assets.py -v` passes **25 focused tests**:
strict metadata/source pins, no-follow redirects, streaming lengths/hashes,
ROM integrity, finite read timeouts, safe input-labeled errors, stage cleanup,
private output confinement, and immutable/offline cache checks. The tests use
synthetic bytes; the redirect test exercises the real handler against loopback.
The existing 16 personal-package tests and three native sanitizer suites pass.
The Android unit task was rerun, not merely accepted as up to date: **213 tests,
zero failures/errors/skips**.

A separate local HTTPS server serves a checksum-valid but nonbootable synthetic
ROM and a 512-byte fake disk. Its short-lived test certificate is trusted only
through `SSL_CERT_FILE` in the test build environment; production TLS validation
is not disabled. The actual Gradle source-manifest route makes exactly two
requests, verifies and packages those exact bytes in the Mac II universal
personal APK. It also builds the public APK, whose artifact checker confirms
all 72 unchanged rune GIFs and no `assets/personal/` metadata or private payload.

Missing acknowledgement, conflicting bundle/source properties and an offline
cache miss all fail **before any request**. With the HTTPS server stopped,
offline preparation succeeds from its verified cache. Changing that fixture's
cached disk then makes preparation fail even though old generated assets exist;
it neither repairs the input nor falls back to the old assets. Restoring only
the synthetic fixture permits the next offline preparation. A separate public
build also succeeds with deliberately nonexistent/conflicting private paths,
confirming it does not resolve private configuration at all.

The ignored local route and logs are `scratch/check-b2-build.py` and
`android/minivmac/private-assets/b2-acceptance-2n8mkpiu/`. No real game or firmware
was served over the network, and no fetched input or personal APK was uploaded.
The earlier `poolradPersonalBundle` route is also rebuilt using the existing
private B1 bundle, leaving local personal APK output with the supplied starting
media rather than synthetic test bytes. No guest is started for these checks.
Runtime, current campaigns, signing configuration and public APK version stay
unchanged. Physical tablet/stylus/e-ink acceptance remains pending.

## 0.7.0 personal-package acceptance (2026-09-14)

Both Mac II universal/all-four-ABI variants build, versionCode 72, with
**213 Java tests, zero failures/errors/skips**. The 15 new installer tests use
synthetic ROM/disks and cover verified atomic publication, exact lengths/hashes,
strict metadata, legacy-media refusal, mutable/deleted media preservation,
failure cleanup, publication races and root lookup during a blocked copy.
`python3 tools/test-personal-package.py -v` passes all 16 synthetic builder
tests. All three existing native probe suites also pass under address/undefined
sanitizers. These are focused local checks, not new CI or a long soak matrix.

The public APK was built in the same invocation as the personal APK, with the
private-bundle property present and generated private assets retained. The
public artifact checker passes: all 72 rune GIFs are unchanged, no ROM/disk/game
archives, and no `assets/personal/` payload or metadata. The personal assets are
present only in the opt-in variant. A subsequent `preparePersonalAssets`
invocation **without** the property fails with the explicit input-path error,
despite the cached prior private build. Both APKs use the existing prototype
signing certificate.

The separate API 30 `poolrad-package-test` AVD on `emulator-5584` is configured
at 1200×1600 / density 200 before boot. The existing campaign on `emulator-5580`
is not used or reset for these checks. Personal input is the previously verified
F9 combined System 7/game disk plus the supplied matching Mac II ROM, copied
through the new helper into ignored `scratch/b1-private-bundle`. Original ROM
and combined-disk hashes remain unchanged.

A read-only app-files directory on an otherwise empty disposable installation
provokes a real setup failure before publication. **Use my own files** shows
the ordinary ROM browser and remembers the choice after reopening; no bundled
media appears. A temporary setup View initially remained behind the welcome
fragment; removing it before the handoff fixed that observed defect, and the
repeated UI check shows only the normal welcome controls. After resetting only
this still-empty fixture, the same failure followed by restoring permission and
**Retry personal setup** publishes the ROM/disk and starts the guest without
ROM-selection or disk-import clicks. Its normal startup alias reaches the
original game's title screen without a Finder launch click.

Re-opening during copying does not hold the UI on the installer's copy lock;
that backend interleaving has a bounded concurrency test. The brief FileManager
root-selection step is separately synchronized to prevent stale legacy paths
from replacing the newly published personal paths. Error/recovery content is
scrollable. Neither measure claims physical rotation/stylus/e-ink acceptance.
See [personal packaging and recovery](PERSONAL_PACKAGE.md).

The shell-managed AVD processes exited during this route; a separate-session
launch (`setsid -f`) was used to continue without resetting the device. Restarting
the same AVD retained its installed data. An Android System UI startup timeout
was dismissed with Wait; its event identifies `SystemUIAuxiliaryDumpService`
at 03:47:14, before PoolRad was launched at 03:47:35. With PoolRad stopped,
SHA-256 comparison across both a personal APK
update and an update back to the public APK proves the disk, ROM, manifest and
receipt all remain byte-identical. The disk already differs from the bundled
starting image because of ordinary guest writes; updates do not restore that
starting image. On relaunch the public APK uses the retained personal disk,
including the original Mac's expected improper-shutdown warning after the AVD
interruption. A normal OK click continues its startup alias into the game.
This is not claimed as an uninterrupted boot or physical-device acceptance.
On the separate-session emulator, the public APK's retained SampleParty loads
through the original File → Load picker to New Phlan 15,1 W, with the live map
and Rolf's opening tutorial visible. No party statistics, movement or save
contents are edited to obtain this result.
Companion HP bars were not visible on that fresh SampleParty; this does not
repeat the earlier postcombat health acceptance. Party polling is enabled and
unchanged by B1. Fresh-save slot/heap coverage is recorded under P1, with the
cause still unverified; no RAM capture or guessed health was substituted.
After normal game Quit and Finder Shut Down, Manage Disks lists the retained
32 MiB `disk1.dsk`. Export resolves its new personal-storage FileProvider URI
and opens Android's chooser without an invalid-root error. This bare AVD has
no app accepting that disk type; no recipient is selected and actual external
disk-file delivery is untested. The existing Import control remains available.

Final public universal APK SHA-256:
`837e5f313c95f13b6830b08ace58df6748fb5df5743b0243221b6919d7629834`.
The personal APK is separate, kept locally in ignored scratch storage, and is
not a public release asset.

## 0.6.0 notebook protection acceptance (2026-09-14)

The universal/all-four-ABI build succeeds, versionCode 71. **198 Java tests,
zero failures/errors/skips**, three native sanitizer suites, six actual Android
page-image checks (`tools/NotePageImageCheck.java`) and eight stopped-core input
checks (`java tools/StoppedCoreInputCheck.java`) pass. The latter extracts the
actual listener/key-release methods against a recording Core; it is not physical
input acceptance. It covers a separately observed 0.5.3 null-Core mouse crash
after normal guest shutdown. The fix uses captured, ready Core references.

On the API 30 emulator, a disposable Notebook 3 receives a Smithy flag at New
Phlan 2,2 and a stroke crossing the map/writing boundary. Its PNG saved through
Android's document picker matches the immutable prepared PNG byte-for-byte
(SHA-256 `1d94dd3ac4d75a6e39bb529b1d9a9a6a89c889c6709c82a8154b8d6f03302f0d`).
Visual inspection confirms a legible 1600×768 heading, complete map, Smithy
symbol and the full stroke. The game stays at the sample party's opening tour,
15,1 W; no game statistics or save contents are edited.

A complete `.prnb` backup saved to Downloads is also byte-identical to its
prepared source. Cancelling an earlier export reports no saved backup; the
next export succeeds. Cancelling Remove leaves the note hash unchanged. A
confirmed removal retires only Notebook 3, then the file-picker restore brings
back the same UUID, tile, Smithy symbol and exact vector bytes. The reopened
page visually agrees. Reimporting the same file reports an existing-notebook
error without overwrite; Notebook 1 remains active until explicitly switched.
The original temple note and retained `.v1` bytes survive the in-place upgrade
and all transfer operations unchanged.

The final APK (including the input guard) was also installed in place after
normal game Quit and Finder Shut Down. Both original records and the restored
test note remained byte-identical. APK SHA-256:
`0026d638fe96e26c6ca9830b96f23815a56b4991dd366bb2dcf7af5394d2df00`.
The prototype signing certificate is unchanged; all 72 rune pictures match the
source and no ROMs, disks or game archives are bundled.

Storage tests additionally cover all-area/legacy/blank flags, label collisions,
checksums, malformed/truncated/trailing/oversized input, unsafe paths, unknown
records, failed staging publication, failed removal and post-commit cleanup.
Transfer-file tests cover write/flush failures, immutable retry sources and
safe pending-file restoration/expiry. These are software checks, not a claim
that every Android document provider or process-death scenario was exercised.
No CI is configured. Physical pen/e-ink acceptance and real rotation remain open.

### Android document-picker test input

On this emulator, ordinary `adb shell input tap` activated dialog buttons but
did not select DocumentsUI file rows. The explicit-finger helper below selected
the same rows successfully; no production code change was needed. It refuses
non-emulator hardware. Inspect the current picker bounds before choosing x/y;
coordinates below are an example, not a script for arbitrary screens.

```sh
picker_check=$(mktemp -d scratch/document-tap.XXXXXX)
javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
  -d "$picker_check/classes" tools/DocumentPickerTap.java
jar cf "$picker_check/classes.jar" -C "$picker_check/classes" .
/usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
  --output "$picker_check/document-tap.zip" "$picker_check/classes.jar"
adb -s emulator-5580 push "$picker_check/document-tap.zip" /data/local/tmp/
adb -s emulator-5580 shell \
  'CLASSPATH=/data/local/tmp/document-tap.zip app_process /system/bin DocumentPickerTap 310 425'
```

## 0.5.3 pen-note software acceptance (2026-09-14)

The Mac II universal/all-four-ABI build succeeds, versionCode 70. **170 Java
tests, zero failures/errors/skips**, all three native sanitizer suites, 13 new
synthetic Android pen-input checks, six existing composite-sheet checks and
seven party-pane View checks pass. The input harness exercises the actual
`InkSheetView` with Android pointer events, not a replacement input model.
[Controls, test details and physical acceptance checklist](PEN_NOTES.md).

An in-place update retains the original temple note and its migration backup
byte-for-byte. A disposable page accepts finger ink with Pen only off; enabling
Pen only prevents further finger gestures from changing the stored note bytes.
All 72 bundled rune images remain unchanged; no ROMs, disks, game archives or
private notes are in the public APK. The prototype signing certificate is
unchanged. These are local emulator/software checks, not CI or physical pen/
e-ink acceptance. N3 remains open for the actual tablet check.

The installed candidate's nearby-flag chooser opens the existing 2,2 page
from a finger tap on adjacent tile 1,2, without creating a second note. Pen
only remains enabled after the update/relaunch. A delayed selection was
declined during live sampling; the generation guard rejects changed map/
notebook context, so this is not evidence of uninterrupted mode recognition.

Forced ADB `wm size 480x1000` recreated the activity and rebooted the Mac core;
it did **not** validate the open editor's narrow-window toolbar. Restoring
1200×1600 left all saved note/backup hashes unchanged. Test ordinary rotation
and real resizable-window behavior separately under Q1; save the game before
forcing display configuration changes. Do not describe this as seamless resume.

## 0.5.2 party-health acceptance (2026-09-14)

The Mac II universal/all-four-ABI build succeeds, versionCode 69. **152 Java
tests, zero failures/errors/skips**, all three native sanitizer suites, and seven
actual Android health-pane View checks pass. All 72 rune GIFs are unchanged in
the APK; no ROMs, disks or game archives are bundled. The signing certificate
matches the preceding release, and an in-place update preserved both the
existing handwritten note and its original-byte backup exactly.

The real Slums combat list exposed a bug: enemies appended after the six
heroes caused the old eight-record reader to reject the entire list. The
corrected bounded reader distinguishes verified member slots from enemy groups.
The updated APK displays all six heroes during an actual kobold battle,
including new injuries and zero HP, without emitting enemy rows.

Normal game controls moved Zarram from last to first and saved `F7Injured`;
the update reloaded its names/order/HP correctly. After normal spell preparation
and rest, Cure Light Wounds raised Arax from **7/12 to 12/12** in both the guest
and companion pane. No game data was patched. [Evidence and replay/View-check
instructions](PARTY.md). These are local emulator checks, not CI or physical
e-ink/stylus acceptance; broader mode recognition remains open.

## 0.5.1 automatic-wheel acceptance (2026-09-13)

The normal Mac II universal/all-four-ABI build succeeds (versionCode 68), with
**152 Java tests, zero failures/errors/skips**, three native sanitizer suites
and the bundled-artwork/no-ROM-or-disks APK check passing. The same signing
certificate permits an in-place update; the existing note and original-byte
backup hashes were unchanged afterward.

The original game accepted **TEMPLE**, then **BEWARE** after normal game
Quit → Finder → Open, without restarting Android process 16292 or the emulator
core. Each entry read back six letters before one Return, with no outside answer
input. Both enabled Load Saved Game. The second run loaded SampleParty through
the original picker and reached Rolf's introduction with the live map, six HP
rows and the existing Temple flag. [Exact evidence](WHEEL_MEMORY.md).
Physical e-ink/stylus/tablet testing is not claimed.

## 0.5.0 integrated acceptance (2026-09-13)

- Normal Mac II universal build and all four ABI variants succeed, versionCode
  67. **149 Java tests pass**, with zero failures, errors or skipped tests.
  The automatic-input regression verifies that readbacks received while a key
  is still held cannot consume the next letter or Return without dispatching it.
- Native map, wheel and party readers pass their focused address/undefined
  sanitizer checks. All 72 checked-in rune GIFs match the APK; no ROM, disk or
  game archive is bundled in this public candidate.
- Six synthetic Android software-View checks exercise the actual composite
  ink sheet: left map/right writing, cross-sheet pen and ink-only erasing,
  symbol changes, nine distinct glyphs, cancellation and resize anchoring.
  See `tools/CompositeSheetRenderCheck.java` and [the page guide](MAP_INK.md).
- Character-field interpretation is checked against the original Mac executable
  and two private live captures, not inferred from identical full-health values.
  [Party evidence and limits](PARTY.md).
- The wheel reader follows the current input frame and live TextEdit buffer.
  The final candidate automatically typed six verified letters and Return;
  the original game accepted them and enabled Load Saved Game. No outside
  letter/Return input was supplied. The then-open distinct-prompt and game
  exit/relaunch checks are completed in 0.5.1 above. [Evidence](WHEEL_MEMORY.md).
- The existing ten-stroke TYR note at New Phlan 11,2 opened with a map on the
  left and unchanged handwriting on the right. Opening left its old file
  byte-identical; first save created an exact `.ink.v1` backup (SHA-256
  `c8b4c152a48451e712f85bfcca08aef973693f6cb652b7c98c36f8ce8c2e0aaf`).
- Changed that flag to Temple, drew across both halves and erased over a map
  wall: only ink disappeared. Undo/redo reproduced identical saved bytes.
  Undid the two test strokes, closed and reopened: Temple and TYR remained.
  A separate Smithy page at 10,2 retained its own stroke on reopen; explicit
  deletion removed only that test page. The original note/backup survived,
  and the guest stayed at 15,1 W. No standalone map-ink editor is shipped.
- The actual eight-action menu has no Annotate, standalone map editor or
  Capture RAM. Spells browse 55 entries, narrowed to eight Cleric 1 entries
  without search/keyboard. Equipment shows 46 weapons and 11 armor rows;
  banded-mail/chain-mail details and comparison pass in the upper-half panel.
- The sample party's six names and current/max HP display beside the map:
  12/12, 8/8, 7/7, 10/10, 9/9, 9/9. Further live damage/healing/reorder checks
  remain in [PARTY.md](PARTY.md), not inferred from full-health screenshots.
- No physical e-ink, stylus, real-device or full-game-mode acceptance is claimed.
- Published `v0.5.0` at commit `ec873f0`. An anonymous GitHub download matches
  the tested universal APK byte-for-byte; SHA-256 is
  `1ee7606613074028180d85bbca54a281eb723c065bd31fc005758e0f40373010`.
  The existing prototype signing certificate is unchanged.

## 0.4.0 handwritten cartographer acceptance (2026-09-13)

- Normal Mac II universal build and all four ABI variants pass. **90 Java tests**
  pass: the previous 50 plus 9 area-identity, 3 map-hit-coordinate, 10 ink-history,
  4 vector-model, 12 notebook-store, and 2 campaign-selection tests. Native
  address/undefined sanitizer checks and all 72 bundled-rune comparisons pass.
- VersionCode 66, versionName 0.4.0, same prototype signing key. Installed in
  place on the API 30 emulator; existing private ROM/disks remained available.
- Wrote a ten-stroke TYR note at New Phlan tile 11,2 with actual touch input.
  Close/reopen, pen/eraser, undo/redo and proportional portrait/landscape resize
  passed. Map/ink input did not move the guest party. The note file contains the
  selected notebook UUID, area key, tile and vectors, not a screenshot or text.
- Notebook 2 is empty in Browse mode; selecting Notebook 1 restores its flag.
  Confirmed linked deletion removes only a disposable second flag and its ink;
  cancellation retains both. A real permission-denied save leaves the sheet
  open and previous file unchanged, then succeeds when permission is restored.
- Completed the original Rolf tutorial, walked through New Phlan's west gate
  into the Slums (record 20), turned around and returned. The other area's flag
  set was empty; the original flag reappeared at 11,2 with byte-identical ink.
  [Identity and route evidence](AREA_IDENTITY.md), [handwriting evidence](NOTEBOOK.md).
- Android Back saves/closes the note. Force-stop/relaunch retains Notebook 1
  and byte-identical stored ink; startup correctly shows no area flags.
  After the complete Mac boot, the offline helper's 25/33/dots → VULCAN answer
  was accepted. Reloading the original sample party restored the flag in Browse
  mode; opening it showed the same TYR ink at 11,2. Crash buffer was empty.
- Physical e-ink, stylus/palm rejection, full-game area/mode coverage and
  notebook export/import are **not** established by these tests. No original
  game data or private notebook vectors are committed or included in the APK.

## 0.3.0 companion tools acceptance (2026-09-13)

- Built the normal Mac II universal APK and all four ABI variants. All **50 Java
  tests** pass (24 existing + 4 screenshot + 9 level + 7 spell + 6 money tests).
  The small native probe suite also passes with address/undefined sanitizers.
- APK inspection: versionCode 65, versionName 0.3.0, app label **Pool of
  Radiance**, ARM64/ARMv7/x86/x86_64. All 72 rune GIFs match the checked-in assets;
  no ROM, disk, save, RAM dump, or game archive is packaged.
- Upgraded the API 30 local Android emulator without uninstalling. Existing ROM
  and disk imports remain. Network stayed disabled. The actual code prompt
  13/21/dash-two-dots → JUNGLE was accepted through the helper's Enter + Return.
- Levels: Fighter overview, Thief level 2 and race-limit tab verified; all 29
  XP thresholds independently match the supplied Mac appendix. Supplemental
  DOS-based combat/saves/skills remain visibly qualified, not claimed as live
  Macintosh character readings.
- Spells: All → Magic-user, level 1, internal alphabet keypad → `sleep` gives
  one entry. Opened its range/duration/target/use details. Android reports
  `mInputShown=false`. Portrait and 1600×1200 landscape panel/Close bounds remain
  above the midpoint, without dimming the guest; long details scroll.
- Money: entered 5 gp + 1 cp in the real panel. It displays 1,001 cp,
  100 sp + 1 cp, 10 ep + 1 cp, 5 gp + 1 cp, and 1 pp + 1 cp exactly.
- Screenshot: used the actual menu action at New Phlan 15,1 W and 11,2 S. Its
  1200×1420 PNGs contain real map + guest pixels, no menu/system bars, and the
  keyboard when open. Android Save to Downloads produced byte-identical output
  (SHA-256 `318568f4626d0368a82759349445f6c67d5a43084675f10ed154173ce13291f3`
  for the first capture). Cancelled another Save picker and successfully took
  another screenshot afterward. Android's ChooserActivity opened with image/png
  content; no recipient was selected and nothing was transmitted. No screenshot
  errors were logged. These app-generated tutorial PNGs are the README images.
- Screenshot cache filename/restore/copy/expiry logic has focused tests and an
  independent code review. Active Save-picker state is serialized; actual
  process-death recovery was not separately forced in this acceptance run.
- Physical tablet, stylus, e-ink refresh, and broad game-area acceptance are
  still untested here. Handwritten notes and the new companion-tab design are
  not implemented by this release; see the authoritative backlog.

## Code wheel

Use **PoolRad → Code wheel lookup**. Select an outer Espruar rune, an inner
Dethek rune, and the dotted/dash-dot/dashed path. **Enter code** sends normal
guest key events, including Return. It does not bypass or patch game code.
Use the action at the game's code-word prompt; it does not yet detect the
prompt or identify its runes automatically. Closing the lookup sends nothing.
App pause cancels a pending entry and releases any held helper key.

Rune numbering matches the reading order of the linked reference. Its 72
illustrations are now bundled in the personal APK and read directly from assets.
No download button, network call, or existing app cache is needed, including on
first launch. Previous private caches can remain unused; they are not consulted.
The lookup, both rune grids, and path selector occupy only the upper half of
the usable screen. The game remains undimmed below. Rune grids and lookup
content scroll on smaller windows; Close/Cancel/Enter stay in the action row.
Rotation resizes open panels, and closing or cancelling sends no game input.

After building, run `node tools/check-wheel-apk.mjs` from the repository root
to check that all 72 images in the universal APK match the checked-in originals,
and that no ROMs, disks, or game data were packaged with them.

The linked table spells one entry `80ASIS` (with a zero); we preserve it instead
of silently changing the requested reference. That particular answer remains
unverified in-game. A leading wheel alignment digit is omitted when typing.

### Code-wheel 0.2.1 acceptance (2026-09-13)

- Built all four Mac II ABI APKs and universal APK; 24 Java tests pass.
- `node tools/check-wheel-apk.mjs`: all 72 GIFs match the local originals
  byte-for-byte (9,574 bytes total). No ROM/disk/game archive was bundled.
- Upgraded the test emulator to versionCode 64 / versionName 0.2.1, with Wi-Fi
  and mobile data disabled. Moved the old `files/codewheel` aside to
  `files/codewheel-legacy-backup`, preserving it; no active cache exists.
- Both 36-rune grids render, and 21/14/dashes still gives `WYVERN`.
- At 1200×1600, lookup/rune/path windows stop at y=780, above midpoint y=800;
  the guest is not dimmed. Verified again at 1600×1200 after rotation, including
  scrolling to rune 36 and accessible Cancel. Selections survive rotation.
- Against the actual Mac prompt, both runes and the path remain visible beneath
  the picker: `scratch/wheel-real-prompt-grid.png`. Selected 12/6/dashes →
  `FRIEND` and used Enter code, which sends the word and Return. The prompt
  closed back to the game's menus, without a rejection prompt.
- Screenshots: `wheel-top-half-offline.png`, `wheel-outer-bundled.png`,
  `wheel-inner-bundled.png`, `wheel-path-top-half.png`,
  `wheel-landscape-grid-scrolled.png`, and `wheel-real-prompt-answer.png`.

## Live area-map prototype

### Area identity investigation (not a finished notes feature)

`tools/AreaFingerprints.java` reuses our existing DAX/GEO reader to compare
full-geometry SHA-256 fingerprints without exporting map data. The supplied
29 decoded maps have distinct fingerprints; two separate New Phlan live
samples match GEO record 0. This is only a candidate identity scheme. It has
not yet passed cross-area, mutable-map, or note persistence acceptance, and
N1/M1 remain unchecked. The tool never changes the game or its save.

### Current live-map behavior

The map appears above the Mac display by default. **PoolRad → Show live map**
toggles it and stores the choice locally. Opening the virtual keyboard reserves
its space below the guest. The map uses spare portrait height where possible;
on smaller windows the guest scales down without overlapping either pane.

This is the **area position**, not a tactical combat map. Area transitions,
combat/wilderness recognition, and area names are not yet validated. Do not
interpret a retained local-area position as the combatant's tile or world-map
location. This is not yet a complete-game mapper.

The game supplies the loaded map, so there is no map-file import step and no
game map bundled in the APK. The full area is shown, not an explored-only map.
All door bit types use the same outline; secret/locked semantics are not claimed.

If a read becomes unavailable after a valid map, retain its geometry but hide
the arrow and label it **Last area · position unavailable**. This avoids a whole
map disappearing on a transient read while never showing an old arrow as live.

### Native profile and update path

`POOLRAD.h` implements one observed game-version profile:

- Low-memory `CurApName` at `0x910` must exactly match the Pascal string
  `Pool of Radiance v1.1` (21 characters).
- Read big-endian `CurrentA5` at `0x904`. The geometry **handle** is stored at
  `A5 - 15168`; dereference the handle to obtain the movable map block.
- The adjacent X/Y bytes are at `A5 - 15086`, followed by facing. Observed
  west/south values are 6/4; the profile interprets 0/2/4/6 as N/E/S/W.
- Validate alignment, every address/range, the 1,024-byte heap payload size,
  16×16 coordinate bounds, and the even facing value. Reject empty geometry:
  the code-wheel screen allocates a zeroed map with default 15,1 W globals.
- The four 256-byte map planes come directly from that handle; they have no
  two-byte DAX record prefix. `PoolRadState` adapts them to the existing `GeoMap`.

These are **Mac v1.1 observations**, not DOS addresses. Only A5-relative offsets
are fixed; A5, the master pointer, and the map allocation are resolved each time.
`tools/test-map-probe.c` exercises relocated synthetic addresses and invalid
pointers with the exact same native reader used by the APK.

The UI requests at most one sample per 250 ms while resumed and visible. An
atomic request is serviced between emulated CPU ticks. JNI returns a 1,200-byte
`PRM1` packet (identity/pointers, 128 nearby global bytes, and 1,024 geometry bytes)
or null. No full RAM scan, file write, or network call is part of live polling.
Java compares position/facing/geometry before invalidating the view; unrelated
global changes don't cause map redraws. Pause/hide removes the polling callback;
core identity and generation checks discard obsolete queued UI updates.

Native checks, using synthetic data only:

```sh
cc -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined tools/test-map-probe.c -o scratch/test-map-probe
scratch/test-map-probe
```

To inspect an existing private RAM capture with the production native reader:

```sh
cc -std=c11 -Wall -Wextra -Werror tools/probe-ram.c -o scratch/probe-ram
scratch/probe-ram scratch/EXAMPLE.ram > scratch/EXAMPLE.probe
```

The CLI prints coordinates/pointers to stderr and the small binary packet to
stdout. Unavailable state exits nonzero with no packet. Keep both RAM and probe
files private; the latter still contains game geometry.

### Completed smoke checks (2026-09-13)

- All four configured Mac II ABIs built, with a normal universal APK.
- 24 JUnit tests passed (13 DAX/GEO tests, 5 wheel tests, 6 live-state tests).
- Native probe tests passed with AddressSanitizer/UndefinedBehaviorSanitizer:
  identity, null/short buffers, relocation, invalid pointers, heap bounds,
  coordinates/facing, empty startup geometry, and geometry payload copying.
- All 29 local maps parsed; every cell/direction lookup exercised.
- Actual game boot, code-wheel answer, and sample-party load succeeded.
- Private RAM snapshots captured while the game remained running.
- The native rune download cached all 72 illustrations; glyphs and lookup worked
  with the test emulator's Wi-Fi and mobile data disabled.
- Native **Enter code** selected 21/14/dashes → `WYVERN`, typed it, pressed
  Return, and passed the actual Mac game's startup check. No external key event
  supplied the Return in that test.

The checked screenshots live only in `scratch/`. `tools/android-ui.mjs` selects
host controls by fresh accessibility labels, avoiding timing-sensitive taps.
It requires an explicit emulator serial and refuses physical-device targets.

### Discovery captures and remaining coverage

Debug **PoolRad → Capture RAM** copies RAM between CPU ticks, then writes the
copy on a worker thread to private `files/snapshots/`. No native RAM pointer is
read from the UI thread. On the tested 8 MiB Mac II, captures are 8,388,608 bytes.
Pull snapshots only from the local test app using `adb exec-out run-as`; never
commit them or put them in an APK.

`tools/InspectMaps.java` and `tools/InspectRam.java` use the same Java DAX/GEO
decoder as the app. Compile with the mapper classes to a directory in scratch.
The latter finds geometry candidates, **not** confirmed active-map pointers.

Two labeled captures show the game reporting `15,1 W`, then `11,2 S` during its
opening tour. Map 0 wall data matched at `0x71d9e8`; the X/Y bytes changed
correctly at `0x7677ba`, with facing `6 → 4`. CurrentA5 was `0x76b2a8`, the map
handle `0x668538`. The A5-relative reader now reproduces both labeled states,
and rejects `scratch/startup.ram` (no party, zeroed map). None of those absolute
heap addresses are embedded in the tracker.

After a later cold boot and sample-party load, the Android pane automatically
drew New Phlan and its `15,1 W` arrow. The screenshot is
`scratch/live-map-party.png`. A subsequent boot with version 0.2.0 confirmed
the startup guard in the Android pane, then the sample-party load and tour:

| Stop | Map pane | Independent guest readout | Evidence in scratch |
| --- | --- | --- | --- |
| Rolf introduction | 15,1 W | 15,1 W | `map-final-party-ready.png` |
| Temple of Tyr | 11,2 S | 11,2 S | `map-tour-stop-1.png` |
| Training school | 5,2 E | 5,2 E | `map-tour-stop-3.png` |

The UI probe also observed 5,3 N while the tour was moving. One transient sample
was unavailable; this prompted the explicit last-area/no-arrow presentation.
The map follows game state during scripted movement, not an estimate from keys.
After the tour, Android number keys exercised ordinary movement: `6` turned
`0,4 W → 0,4 N`, `8` stepped to `0,3 N`, `6` faced `0,3 E`, and another `8`
hit a solid east wall without changing coordinates or moving the map marker.
Both the game and pane matched in `map-manual-turn.png`, `map-manual-step.png`,
`map-facing-wall.png`, and `map-blocked-step.png`.
Area transitions and combat/wilderness still require separate checks.
Keyboard-open layout was checked in
`map-keyboard-open.png`; panes remain separate at 1200×1600.
The PoolRad toggle removed/restored the map, and Android Home → resume restored
the live `5,2 E` position without rebooting the guest (`map-resumed.png`).
The last-area/no-arrow presentation and non-focusable map adjustment were built
after that Android route; they were code-reviewed, not rerun through a full
guest boot. The 24 Java tests and all four ABI builds pass on the final source.

One switch to 8× during Mac OS startup was followed by a guest divide-by-zero
error. A subsequent cold boot at the default 1× succeeded; use 1× until Finder
for this test disk. No Android app crash appeared in the checked crash log.

Local runner note: two long-lived terminal emulator processes exited gracefully
during testing (exit 0, no app-crash evidence in their exit logs). The current
AVD runs under `systemd-run --user --unit=poolrad-map-test --collect ...` so it is
not tied to a tool terminal session. This is test infrastructure, not an APK
dependency. Inspect it with `systemctl --user status poolrad-map-test`.
