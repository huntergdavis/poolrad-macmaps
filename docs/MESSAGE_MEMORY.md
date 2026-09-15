# The game's Message text in memory — research notes

**Status: the blocking observation has been made.** This records the verified
reader, the citation wording the game actually uses, and how it was reached.

## Verified: the displayed message text is readable

The original game's Message window is a TextEdit window. CODE2 `+0x606c..+0x608c`
creates it and stores the `WindowPtr` at **`A5 − 0x6174`**; CODE2 `+0x6120` calls
`_TENew` (trap `0xA9D2`) and stores the resulting `TEHandle` at
**`A5 − 0x6178`**.

From there the text is an ordinary `TERec`:

```text
A5-0x6178      -> TEHandle
  *TEHandle    -> TERec
    +0x3c        teLength, 16-bit
    +0x3e        hText, a Handle to the characters
```

Checked against every private RAM capture on this workstation. Each one decodes
to exactly the text the corresponding screenshot shows, for example:

| Capture | Decoded message |
| --- | --- |
| `p1-fresh-party-1` | `'GREETINGS, COURAGEOUS ONES, I AM ROLF, APPOINTED BY THE COUNCIL…` |
| `phlan-11-2-south-tour` | `'YOU NOW FACE AN ENTRANCE TO THE TEMPLE OF TYR.  THERE ARE THREE…` |
| `m1-slums-arrival` | `YOU HAVE ENTERED THE MONSTER-CRAWLING SLUMS OF PHLAN.…` |
| `m1-new-phlan-return` | `YOU ARE BY THE GATEWAY TO THE UNSETTLED AREAS.…` |
| `f10-camp` | `The party makes camp...` |
| `m2-tour-moving-1` | *(empty)* |

The three combat captures that the existing application-profile guard already
rejects return non-text bytes here too, so any reader must keep that guard and
must also require printable characters rather than trusting the length field.

## The citation wording, observed live 2026-09-14

In the gambling tavern of civilized New Phlan, at map tile **10,8** (entered
south through the doorway from 10,7), declining `DOES ANYONE WANT TO GAMBLE?`
made the game print, in its own Message window:

```
YOU OVERHEAR TAVERN TALE 15
```

`scratch/r9-tavern-tale-15.png`. So the form is a plain uppercase sentence with
a bare decimal number and **no** parentheses, no `#`, and no `SEE`. The next
message was `A DRUNKEN BRAWL BREAKS OUT. YOU ARE CAUGHT IN THE MIDDLE`, then
combat, so the tale citation is a normal Message-window string like any other.

**Only the tavern-tale wording is verified.** The journal-entry and
proclamation wordings have still not been seen, and R9 forbids guessing them,
so nothing may be filed for those two categories until one of each is observed
the same way.

## Why it was blocked until now

R9 needs to notice when the game cites a journal entry, proclamation or tavern
tale. **The phrasing could not be recovered offline:**

- STRS0 contains no `journal`, `proclamation`, `tavern`, `entry` or `paragraph`
  text; a scan of **every** resource in the game application finds none either.
- A scan of every `*.DAX` file in the verified extraction, RLE-decoded, finds
  none. The ECL scripts hold no readable uppercase prose at all, so the
  encounter text is not stored there as plain ASCII.
- None of the private captures contains a citation, because all of them were
  taken during the opening tour, which never cites one.

R9's own wording forbids the shortcut: *"do not guess uncertain numbers"*. A
pattern invented from a plausible-sounding phrase would silently file wrong
entries into the notebook, which is worse than the feature's absence.

That is why it had to be observed in play, which is what the run above did.

## In-game search, 2026-09-14

Driving the guest is scriptable and works. Two corrections to the first
attempt's recipe, both found the hard way:

- **The party walks on the number keys, not the arrow keys.** On the Mac
  keyboard's symbol page (`123`), `4` turns left `(419,1315)`, `6` turns right
  `(659,1315)` and `8` steps forward `(899,1315)`. The arrow keys at
  `(719,1438)` / `(959,1438)` / `(839,1377)` do *not* turn the party; a run
  built on them walks into the nearest building and then wanders.
- **Every message must be acknowledged.** When the game prints a location
  description it replaces its six-button action row with a single **Continue**
  button at `(530,1257)`, and it ignores movement keys until that is clicked.
  A driver that does not click it looks exactly like one whose keys are not
  arriving.

Route planning is exact rather than by trial: the live probe packet already
carries the 16×16 GEO geometry, so `GeoMap.edgeKind` gives a breadth-first
route over **OPEN edges only**. Doorways must be excluded — they are building
entrances, and the game stops the party at them. Stepping the party and hashing
only the Message window band of each screenshot makes it cheap to find the
frames where the text actually changes.

The user suggested bars and the city hall. Following the city hall lead:

| Where | What the game said |
| --- | --- |
| 3,4 E | `YOU ARE OUTSIDE THE CITY HALL. THE CITY CLERK WAITS INSIDE TO AWARD COMMISSIONS` |
| 4,6 S | `YOU ARE INTERCEPTED BY THE COUNCIL GUARD. 'HALT. YOUR PRESENCE IS NOT AUTHORIZED. LEAVE.' DO YOU LEAVE?` |
| 5,5 E | `AT YOUR ENTRY, THE COUNCIL CLERK BEGINS LOOKING THROUGH A STACK OF PAPERS…` |
| 5,5 E | `SOKAL KEEP ON THORN ISLAND MUST BE CLEARED.'` and the rest of the commission list |
| 5,5 E | `'THESE ARE ALL OF THE COMMISSIONS CURRENTLY AVAILABLE` |

**The Council Clerk's commissions cite no journal entry.** That lead is ruled
out. A tavern was not reached: the civilized New Phlan streets around the city
hall loop back on themselves, and Pool of Radiance keeps its rumour-telling
taverns in the uncivilized districts, which is a much longer trip from the
tutorial start.

Also ruled out: the displayed text is **not** stored in plain form anywhere in
the supplied game files. Searching all 114 extracted files for a sentence the
game had just displayed — `MONSTER-CRAWLING SLUMS`, read live out of the TERec
above — finds nothing, raw or RLE-decoded, and only the documentation files
contain the word `PHLAN` at all. So the encounter text is encoded in a form this
project has not decoded, and the citation phrasing cannot be recovered offline
from the data either.

## What the app now does with it

The reader above is shipped as `POOLRAD_MESSAGE.h`, delivered to Java as a
520-byte `PRT1` packet on the existing 250 ms companion poll:

| Byte | Meaning |
| --- | --- |
| 0..3 | `PRT1` |
| 4 | 1 text present, 255 unreadable |
| 5 | 1 when the game's text was longer than the 512-byte ceiling |
| 6..7 | length, big-endian |
| 8.. | the characters, printable ASCII plus CR and tab only |

A single byte outside that set rejects the whole sample: a half-decoded
sentence could carry a half-decoded reference number. A truncated sample is
delivered but never read for citations, for the same reason.

`JournalCitation` then matches **only the wording above**, anchored so that
`TAVERN TALE 153` cannot read as tale 15, and drops any number the supplied
journal does not define. What it matches goes into the selected notebook's
encountered list, once, and rides along in that notebook's backup.

Until the other two wordings are observed, the manual reader shipped in REF5
remains the only way to reach a journal entry or proclamation the game
mentioned.
