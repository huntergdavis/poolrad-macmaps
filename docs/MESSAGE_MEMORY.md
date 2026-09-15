# The game's Message text in memory — research notes

**Status: research only. R9 is not implemented.** This records a verified reader
and the one missing fact that blocks the feature, so the next attempt does not
repeat the search.

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

## What blocks R9

R9 needs to notice when the game cites a journal entry, proclamation or tavern
tale. **The phrasing it uses has not been observed, and it cannot be guessed:**

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

**What would unblock it:** one observation of the game actually citing an entry
— a screenshot, or the exact wording typed out. With the phrase in hand the
reader above already supplies the text to match against, and the remaining work
is a bounded parser plus the per-notebook deduplicated list.

## In-game search, 2026-09-14

Driving the guest is scriptable and works. Arrow keys live on the Mac
keyboard's symbol page (`123`): up `(839,1376)`, left `(719,1438)`, down
`(839,1438)`, right `(959,1438)`. Stepping the party and hashing only the
Message window band of each screenshot makes it cheap to find the frames where
the text actually changes, which is how the run below was done.

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

Until then, note that the manual reader shipped in REF5 already covers looking
entries up; what is missing is only the automatic collection.
