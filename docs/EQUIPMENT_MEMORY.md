# Readied equipment in Macintosh memory — research notes

**Status: research only. R4 is not implemented and nothing here is shipped.**
This page records what has been established so the next attempt does not repeat
it, and — just as importantly — what has *not* been established.

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

## Open, and required before R4 can ship

1. **A stable name source.** Candidate: a type id in the item record — the three
   sample items differ at `+0x32` (`0x24` long sword, `0x3b` shield, `0x39`
   banded mail) — resolved against the game's own `ITEMS` / `ITEM%d.DAX` data
   through the existing `DaxReader`. The offset is a *candidate only*; it has not
   been confirmed against the code, and the mapping to `ITEMS` has not been
   established at all. The shipped [equipment reference](EQUIPMENT_REFERENCE.md)
   is authored and keyed by its own string ids, so it cannot supply this mapping.
2. **Ammunition**, which R4 marks "if verified".
3. **Carrying load and movement**, not yet located in the character record.

Until 1 is settled, the only honest thing R4 could report is that *something* is
readied, which is not worth shipping. No offsets from this page should be used
in the app before they are confirmed the way `+0xd8` was: against the code and
against a real capture.

## Method note

`scratch/m4-code.py` needs two virtual environments to run again: capstone from
`scratch/wheel-analysis-venv` and `macresources` from `scratch/personal-boot-venv`.
To find the routine that prints a given message, search every `CODE` resource for
`4879 0000 XXXX` (`pea.l $XXXX.l`), where `XXXX` is that string's STRS0 offset.
No executable bytes, private RAM, game records or saves are published here.
