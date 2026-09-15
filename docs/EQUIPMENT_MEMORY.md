# Readied equipment in Macintosh memory

Character details show the **readied weapon and armor**, named the way the
original game names them. Nothing here equips, unequips or changes an item.
A slot that is genuinely empty reads *No weapon readied*; item blocks that
cannot be read say *Readied equipment unavailable*. The two are never
conflated, because they mean completely different things.

## Established

**Readied items are an array of handles at character record `+0xd8`,**
indexed by slot: `*( *(character) + slot × 4 + 0xd8 )`.

CODE3 `+0x61aa..+0x620e` addresses it exactly that way (`base + index×4`, then
`+0xd8`, then one dereference to the item record) while printing STRS0 `+0x1602`,
"Already using". The character sheet at CODE3 `+0x3c26` and `+0x3ca4` tests
`$d8(a0)` and `$e0(a0)` and skips the "Weapon:" (`+0x148c`) and "Armor:"
(`+0x1494`) lines when either is null — so **slot 0 is the weapon and slot 2 is
the armor**, with slot 1 observed holding the shield.

Confirmed against a real capture. In `scratch/f7-combat-active-2.ram` — the only
private capture taken while the party was live in combat — all three handles for
every member dereference to real 76-byte item blocks sitting beside the
character records, and the contents match the sample party's actual kit:

| Member | Slot 0 | Slot 1 | Slot 2 |
| --- | --- | --- | --- |
| Arax the Bold | Long Sword | Shield | Banded Mail |
| Lara Spellsword | Long Sword | Shield | Banded Mail |
| Tanarakis | 24 Darts | Shield | Banded Mail |
| Hogarth | Long Bow | — | Banded Mail |
| Shara the Grey | Long Bow | — | Banded Mail |
| Zarram | Flail | Shield | Banded Mail |

In every other capture the same handles are present but their master pointers are
zero, i.e. the blocks are not resident. **A purged handle must be reported as
unavailable, never as "no weapon readied".** This is the single most important
constraint for implementing R4.

## The name comes from the game's own part table

CODE3 `+0x0658..+0x06c2` composes an item's name from up to **three name parts**.
It walks `item + 0x2f + n` for **n = 3 down to 1** (the countdown is set up at
`+0x064e`), skips a part whose index is zero, skips a part whose bit `3 − n` is
set in the byte at `item + 0x36`, and appends the string whose 4-byte pointer
sits at **`A5 − 0x5db2 + index × 4`**. That table holds 256 entries.

Reading the table out of the live-combat capture resolves index 36 to
`Long Sword`, 59 to `Shield`, 57 to `Banded`, 48 to `Mail`, 12 to `Flail`,
43 to `Long Bow` and 9 to `Dart`. Applying the composition rule reproduces the
sample party's kit exactly — including `Banded Mail`, which is two parts
appended in descending order:

```text
Arax the Bold    Long Sword   Banded Mail
Lara Spellsword  Long Sword   Banded Mail
Tanarakis        Dart         Banded Mail
Hogarth          Long Bow     Banded Mail
Shara the Grey   Long Bow     Banded Mail
Zarram           Flail        Banded Mail
```

The reader emits only that composed base name. Counts, plurals, magic columns
and `+N` suffixes are formatter extras it deliberately does not reproduce, so
Tanarakis' quiver reads `Dart` where the game's own list writes `24 Darts`.

## Why the obvious name field is unsafe

The item record starts with a Pascal string, and it is tempting to read it as the
item's name. It is not: it is the **scratch buffer the game's own formatter
writes into**, cleared at CODE3 `+0x04ca` and rebuilt on each render.

Its content therefore depends on the *last* screen that drew the item. The same
capture shows shields whose buffer reads `" Yes  Shield "` — the leading
`" Yes  "` is the magic-column string at STRS0 `+0x12fa`, left over from the item
list — and weapons whose buffer holds `"Long Sword "` followed by stale bytes
(`"Sword"`) from an earlier, narrower render. Reading it would sometimes show a
correct name and sometimes show a fragment or a stray column.

The formatter itself (CODE3 `+0x04c0`) composes names from a magic flag at item
`+0x35`, a bonus at `+0x37` (STRS0 `+0x130a`), a count at `+0x3a`
(STRS0 `+0x130e`) and a per-index bitfield at `+0x36`. Reproducing it faithfully
is possible but is a decode of its own.

## What the reader refuses

Every one of these withholds that **one character's** equipment and leaves their
name, health, AC, class, condition and spells untouched:

- a purged item block, which is by far the common case;
- an odd, low or out-of-range handle or item pointer;
- a name-part pointer outside RAM, or a part containing a control byte;
- a part index whose string is empty;
- a composed name that will not fit the 31-character field, which is withheld
  rather than truncated into something misleading.

## Carried weight and movement

The game's own character sheet prints `ENCUMBRANCE` and `MOVEMENT` beside
`Weapon:` and `Armor:`. Both are plain character-record fields:

| Field | Offset | Width |
| --- | --- | --- |
| Carried weight | `+0x10e` | 16-bit big-endian, gold-piece weight |
| Movement | `+0x12c` | one byte, combat squares |

Each value is the **only** match for the sheet's printed number anywhere in the
302-byte record, and each varies per character and holds steady across captures:
Arax 988, Lara 624, Hogarth 822, Shara 735, Zarram 658, all moving 9 squares in
banded mail. Tanarakis reads 796 before combat and 781 during it, consistent
with darts having left her pack — the kind of change a stored total should show.

Unlike the item handles these never purge, so movement and carried weight are
reported **even when the readied items are unavailable**, and they are filled in
independently of the item status byte.

The details pane adds one warning, and only when the game's own movement is
already at **3 squares**, the slowest the printed rules describe. The app does
not compute, predict or explain the threshold; it reports the number the game
arrived at. The [equipment reference](EQUIPMENT_REFERENCE.md) carries the
printed movement and encumbrance rules offline.

## Still not included

**Ammunition**, which R4 marks "if verified". Slot 1 holds a shield for this
party, and nothing has established how a readied quiver is distinguished, so
ammunition is not reported at all rather than guessed at. The details pane
says so.

## Packet

PRP5 is **768 bytes**: the 248-byte PRP4 packet with version byte `5`, plus
eight 65-byte blocks at `248 + memberIndex × 65`:

```text
0      item status: 00 read, FF unavailable
1..32  readied weapon name, NUL-padded printable ASCII
33..64 readied armor name, NUL-padded printable ASCII
65     movement allowance, combat squares
66..67 carried weight, big-endian gold-piece weight
unused member blocks: all zero

The last three bytes are always filled, including when the item status says
unavailable, because they are ordinary record fields rather than item blocks.
```

PRP1 through PRP4 still parse at their exact old sizes with equipment
unavailable. Any other status byte, names on an unavailable block, a byte past a
name's terminator, a non-printable byte, an unterminated name or a nonzero
unused block is rejected. An equipment-only change invalidates the immutable
display, exactly like HP, condition, spells or party order.

## Verification scope

Native tests cover composition from one, two and three parts, the descending
append order, every one of the eight suppression-bit combinations, an empty
slot reading as empty, a purged handle, odd/low/out-of-range handles and item
pointers, every control byte inside a part, an empty part string, a name of
exactly the maximum length and one byte over, and per-member independence with
one unreadable member between two readable ones. Movement and carried weight
are checked to survive a purged item block. Java tests cover the labels,
the empty-versus-unavailable distinction, every malformed block, older packets,
redraw on an equipment-only change, per-member independence, the load fields
surviving unavailable items, and every movement value from 0 to 12 against the
single warning threshold.

Replaying the private captures confirms both paths on real data: the
live-combat capture decodes all six members' weapons and armor, and every other
capture — where the item blocks are not resident — reports equipment
unavailable rather than inventing empty hands. See
[LOCAL_TESTING.md](LOCAL_TESTING.md) for the live check.

## Method note

`scratch/m4-code.py` needs two virtual environments to run again: capstone from
`scratch/wheel-analysis-venv` and `macresources` from `scratch/personal-boot-venv`.
To find the routine that prints a given message, search every `CODE` resource for
`4879 0000 XXXX` (`pea.l $XXXX.l`), where `XXXX` is that string's STRS0 offset.
No executable bytes, private RAM, game records or saves are published here.
