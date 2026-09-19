# Original game clock

The map header shows **Day 1 · 12:00 am**, using the game's own saved counters.
It advances when the game advances time, including actions such as Look.
Android time, emulator speed and time spent reading companion pages do not
extrapolate it. Times always use am/pm; midnight and noon both use 12.

The clock stays within the existing header height, with bounded text regions
for area, time and position/combat status. Narrow layouts may abbreviate these
with an ellipsis; accessibility retains the full reading. Valid camp, combat
and wilderness readings can show time without pretending to have local map
coordinates. Loading clears it immediately. Brief unreadable frames use the
existing map reading hold; a prolonged refusal or hidden pane clears it.

## Original-code evidence

The 2,048-byte state allocation is reached through the movable handle at
A5−0x5eb2. All fields below are big-endian words.

| Offset | Meaning | Supported range |
| --- | --- | --- |
| +0x18c | fraction of a minute | 0–9 |
| +0x18e | minute units | 0–9 |
| +0x190 | tens of minutes | 0–5 |
| +0x192 | hour | 0–23 |
| +0x194 | day within 30-day unit | 0–29 |
| +0x196 | month within 12-month unit | 0–11 |
| +0x198 | low year counter | 0–255 |
| +0x19a | high year field | must be zero for this reader |

CODE3 +0x2b0a–0x2c52 builds the game's Information-window position line.
It formats +0x192 as the hour and 10×(+0x190)+(+0x18e) as the minute.
CODE4 +0x2126–0x21d0 carries each counter into the next, using the exact
unit table [10,10,6,24,30,12,256] at A5−0x373a.
CODE4 +0x2202–0x22c4 copies seven state counters from +0x18c, increments
the requested unit, normalizes it and writes them back.
CODE4 +0x1dce–0x1e58 sets these fields, including the separate high-year word.

The displayed day is one-based: 1 + day + 30×month + 360×low-year.
This is the game's counter rendered as a continuous day number, not a claim
about real elapsed playtime or a Forgotten Realms calendar date. The high-year
field and overflow beyond the verified seven-counter normalization are not
guessed: a nonzero high-year word or any out-of-range counter hides the clock.
The unit table itself must match before any time is trusted.

Original executable resources and RAM remain private. Reproduce the disassembly
with tools/disassemble-game.py and the read-only capture with tools/capture-ram.sh.
The existing state-block route is reused from [SAVE_FORMAT.md](SAVE_FORMAT.md)
and the native map reader. Clock arithmetic was checked directly against the
original executable after recalling earlier session work with Deja.

## PRM6

The map packet grows from 1204 to 1212 bytes. Its prior fields keep their offsets.

| Bytes | Meaning |
| --- | --- |
| 1204 | clock valid: 1; unavailable: 0 |
| 1205–1208 | one-based day, unsigned big-endian |
| 1209 | hour 0–23 |
| 1210 | minute 0–59 |
| 1211 | reserved zero |

Native code validates the game profile, display mode, complete movable state
allocation, unit table and all counters. It writes no guest data. The Java
parser independently checks the clock ranges. A bad clock cannot hide otherwise
valid geometry; older PRM1–PRM5 packets have no clock. Status-only PRM6 packets
carry no local geometry even when their independent clock is valid.

For a private capture, the existing diagnostic now supports the current display
packet:

    cc tools/probe-ram.c -o scratch/probe-ram
    scratch/probe-ram scratch/private.ram --display > scratch/private.probe

## Validation

A live original-game Look moved Information from 00:00 to 00:10.
Before/after full RAM captures changed the clock tuple from all zeroes to
[0,0,1,0,0,0,0,0], agreeing with the decoded minute calculation.

Native sanitizer tests exercise every supported component value and its first
invalid boundary, mode gating, allocation failure, table mismatch and unchanged
guest RAM. Parser tests cover midnight, noon, day boundaries, invalid values,
legacy/truncated packets, immutability and non-local modes. Rendering and
installed-app acceptance are recorded with the release.


Installed-app acceptance on 2026-09-19: the loaded ExportProof save showed
**Day 1 · 12:00 am** while Information showed 00:00. One original Look changed
both to ten past midnight. The final build passed 657 Java tests and 17 Android
rendering checks, including narrow/wide clock presentation, combat, loading
clearing and pane reset. Native tests passed under address/undefined-behavior
sanitizers. Actual day rollover and physical-tablet rendering were not exercised.

During clean-update acceptance, sparse OCR split Finder's “Special” label.
The helper stopped in Finder without replacing the APK. The reusable shutdown
tool now retries the alternate OCR layout and can resume from a Finder menu
verified by its ordered, aligned File/Edit/Label/Special labels. The retry
completed normal shutdown, proved disk descriptors closed, installed the APK
and loaded the original save.
